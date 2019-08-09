package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileMissingBlock;
import com.lapissea.util.LogUtil;
import com.lapissea.util.NotNull;
import com.lapissea.util.UtilL;
import com.lapissea.util.function.TriFunction;
import com.lapissea.util.function.UnsafeConsumer;
import com.lapissea.util.function.UnsafeFunction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.lapissea.blendfileparser.BlockCode.*;
import static com.lapissea.blendfileparser.FileBlockHeader.*;
import static com.lapissea.util.UtilL.*;

public class BlendFile implements AutoCloseable, Comparable<BlendFile>{
	
	@NotNull
	public static BlendFile read(File file) throws IOException{
		Assert(file.isFile());
		File dir=Objects.requireNonNull(file.getParentFile());
		
		return read(FileInputStream::new, file.getPath());
	}
	
	@NotNull
	public static BlendFile read(UnsafeFunction<String, InputStream, IOException> dataProvider, String blendName) throws IOException{
		return new BlendFile(dataProvider, blendName);
	}
	
	final         UnsafeFunction<String, InputStream, IOException> source;
	private final String                                           name;
	public final  ID<?>                                            id;
	
	final         BlendFileHeader            header;
	public final  Dna1                       dna;
	private final Map<Long, FileBlockHeader> blockPtrIndex;
	final         FileBlockHeader[]          blocks;
	private final Struct                     strayPointerType;
	
	private final Map<String, TriFunction<Struct, FileBlockHeader, BlendFile, TypeOptimizations.InstanceComposite>> typeOptimizations;
	
	private final Map<Struct.Instance, Translator>                             translationCache=new HashMap<>();
	final         Map<String, Function<Struct.Instance, ? extends Translator>> translators     =new HashMap<>();
	
	public interface Translator{
		void translate(Struct.Instance data);
	}
	
	{
		registerTranslator(Library::new);
	}
	
	private BlendFile(UnsafeFunction<String, InputStream, IOException> dataProvider, String blendName) throws IOException{
		source=dataProvider;
		this.name=blendName;
		id=new ID<>(this);
		
		try(InputStream blendFile=openSource()){
			boolean supportsMark=blendFile.markSupported();
			
			if(supportsMark) blendFile.mark(BlendFileHeader.BYTE_SIZE);
			BlendFileHeader header;
			try{
				header=new BlendFileHeader(blendFile, false);
			}catch(IOException e){
				if(supportsMark){
					blendFile.reset();
					header=new BlendFileHeader(new GZIPInputStream(blendFile), true);
				}else{
					blendFile.close();
					try(InputStream s=openSource()){
						header=new BlendFileHeader(new GZIPInputStream(s), true);
					}
				}
			}
			this.header=header;
		}
		
		
		strayPointerType=new Struct(-1, (short)header.ptrSize, new DnaType("StrayPointer", 0, false, null), Collections.singletonList(new Field("void", "badPtr")));
		
		LinkedList<FileBlockHeader> blockBuilder=new LinkedList<>();
		{
			Dna1[]         dna      ={null};
			Consumer<Dna1> dnaSetter=d->dna[0]=d;
			
			try(BlendInputStream in=reopen()){
				in.skipNBytes(BlendFileHeader.BYTE_SIZE);
				while(true){
					FileBlockHeader block=new FileBlockHeader(in, dnaSetter);
					if(block.code==END) break;
					blockBuilder.add(block);
				}
			}
			
			this.dna=Objects.requireNonNull(dna[0]);
		}
		
		typeOptimizations=TypeOptimizations.get(this.dna);
		
		blocks=blockBuilder.toArray(NO_BLOCKS);
		
		blockPtrIndex=new HashMap<>(blocks.length);
		for(FileBlockHeader b : blocks){
			b.init(dna);
			//noinspection AutoBoxing
			blockPtrIndex.put(b.oldPtr, b);
		}
	}
	
	private static <T extends Translator> Function<Struct.Instance, T> classToNew(Class<T> tClass){
		try{
			Constructor<T> constructor=tClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			return d->{
				try{
					return constructor.newInstance();
				}catch(ReflectiveOperationException e){
					throw UtilL.uncheckedThrow(e);
				}
			};
		}catch(NoSuchMethodException e){
			throw UtilL.uncheckedThrow(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T, E> void registerTranslatorSwitch(@NotNull String structName, Class<?> switchClass){
		try{
			String waysName      ="TRANSLATOR_SWITCH";
			String identifierName="translatorIdentifier";
			
			java.lang.reflect.Field ways=switchClass.getDeclaredField(waysName);
			ways.setAccessible(true);
			Method identifier=switchClass.getDeclaredMethod(identifierName, Struct.Instance.class);
			identifier.setAccessible(true);
			
			if(ways.getType()!=Map.class) throw new RuntimeException(waysName+" needs to be type of "+Map.class.getName()+": "+switchClass);
			
			
			ParameterizedType generics=(ParameterizedType)ways.getGenericType();
			Type[]            args    =generics.getActualTypeArguments();
			if(args[0]!=identifier.getGenericReturnType()) throw new RuntimeException(waysName+" key generic type needs to be the same as "+identifierName+" generic type: "+switchClass);
			
			boolean isFunc;
			
			Type keyBase=args[1];
			find:
			try{
				if(keyBase==Class.class){
					isFunc=false;
					break find;
				}
				if(keyBase==Function.class){
					isFunc=true;
					break find;
				}
				ParameterizedType k1=(ParameterizedType)keyBase;
				
				Type k1raw=k1.getRawType();
				
				if(k1raw==Class.class){
					isFunc=false;
					break find;
				}
				if(k1raw==Function.class){
					isFunc=true;
					break find;
				}
				
				throw new RuntimeException("Unknown Map value generic type");
			}catch(Throwable e){
				throw new RuntimeException("TRANSLATOR_SWITCH needs to be type of Map<E, Class<T>> or Map<E, Function<"+Struct.Instance.class.getName()+", E>>!", e);
			}
			
			Function<Struct.Instance, Object> identifierVal=inst->{
				try{
					return identifier.invoke(null, inst);
				}catch(ReflectiveOperationException e){
					throw UtilL.uncheckedThrow(e);
				}
			};
			
			if(isFunc){
				registerTranslatorSwitchFunc(structName, (Map<Object, Function<Struct.Instance, Translator>>)ways.get(null), identifierVal);
			}else{
				registerTranslatorSwitchClass(structName, (Map<Object, Class<Translator>>)ways.get(null), identifierVal);
			}
			
		}catch(ReflectiveOperationException e){
			throw UtilL.uncheckedThrow(e);
		}
	}
	
	public <T extends Translator, E> void registerTranslatorSwitchClass(@NotNull String structName, @NotNull Map<E, Class<T>> ways, Function<Struct.Instance, E> identifier){
		registerTranslatorSwitchFunc(structName, ways.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e->classToNew(e.getValue()))), identifier);
	}
	
	public <T extends Translator, E> void registerTranslatorSwitchFunc(@NotNull String structName, @NotNull Map<E, Function<Struct.Instance, T>> ways, Function<Struct.Instance, E> identifier){
		registerTranslator(structName, data->{
			E                            id =identifier.apply(data);
			Function<Struct.Instance, T> way=ways.get(id);
			if(way==null) throw new RuntimeException("Unable to resolve switch for "+id+" in "+structName);
			return way.apply(data);
		});
	}
	
	public <T extends Translator> void registerTranslator(@NotNull Class<T> directTranslation){
		registerTranslator(directTranslation.getSimpleName(), directTranslation);
	}
	
	public <T extends Translator> void registerTranslator(@NotNull String structName, @NotNull Class<T> directTranslation){
		registerTranslator(structName, classToNew(directTranslation));
	}
	
	public <T extends Translator> void registerTranslator(@NotNull Supplier<T> translator){
		T t=translator.get();
		registerTranslator(t.getClass().getSimpleName(), translator);
	}
	
	public <T extends Translator> void registerTranslator(@NotNull String structName, @NotNull Supplier<T> translator){
		registerTranslator(structName, data->translator.get());
	}
	
	public <T extends Translator> void registerTranslator(@NotNull String structName, @NotNull Function<Struct.Instance, T> translator){
		Objects.requireNonNull(structName);
		Objects.requireNonNull(translator);
		translators.put(structName, translator);
	}
	
	private final Map<Struct.Instance, Struct.Instance> translationLocks=new HashMap<>();
	
	@SuppressWarnings({"unchecked"})
	public <T extends Translator> T translate(Struct.Instance instance){
		if(instance==null) return null;
		
		Assert(instance.blend==this);
		synchronized(translationLocks){
			instance=translationLocks.computeIfAbsent(instance, k->k);
		}
		synchronized(instance){//need to synchronize over each instance separately to enable reading unrelated objects at the same time to preserve a high level of parallelism
			T t=(T)translationCache.get(instance);
			if(t==null){
				Function<Struct.Instance, T> translator=(Function<Struct.Instance, T>)translators.get(instance.struct().type.name);
				if(translator==null) throw new RuntimeException("No translator for "+instance.struct().type.name);
				
				t=translator.apply(instance);
				translationCache.put(instance, t);
				t.translate(instance);
			}
			return t;
		}
	}
	
	public Stream<FileBlockHeader> blocksByCode(BlockCode code){
		return Arrays.stream(blocks).filter(b->b.code==code);
	}
	
	public Stream<Struct.Instance> readSingleBlockByCode(BlockCode code){
		return blocksByCode(code).map(this::readBlockSingle);
	}
	
	public Struct.Instance readBlockSingle(FileBlockHeader blockHeader){
		return (Struct.Instance)readBlock(blockHeader);
	}
	
	Object readBlock(long pointer) throws BlendFileMissingBlock{
		return readBlock(getBlock(pointer));
	}
	
	private Object parseBlock(FileBlockHeader blockHeader){
		Struct struct=blockHeader.getStruct();
		
		TriFunction<Struct, FileBlockHeader, BlendFile, TypeOptimizations.InstanceComposite> optimization=typeOptimizations.get(struct.type.name);
		if(optimization!=null){
			return optimization.apply(struct, blockHeader, this);
		}
		
		if(blockHeader.count==1){
			return struct.new Instance(blockHeader, this);
		}
		if(blockHeader.count>100){
			//noinspection AutoBoxing
			LogUtil.println(struct.type, "should probably be optimized. Block has", blockHeader.count, "objects!");
			LogUtil.println(struct);
		}
		
		return new BlockArrayList(blockHeader, this);
	}
	
	@NotNull
	Object readBlock(FileBlockHeader blockHeader){
		synchronized(this){
			if(blockHeader.bodyCache==null){
				Object obj=parseBlock(blockHeader);
				blockHeader.bodyCache=new WeakReference<>(obj);
				return obj;
			}
			
			Object obj=blockHeader.bodyCache.get();
			if(obj!=null) return obj;
			
			blockHeader.bodyCache=null;
			
			return readBlock(blockHeader);
		}
	}
	
	Map<Thread, BlendInputStream> inCacheMap=new HashMap<>();
	
	
	private BlendInputStream getSourceAt(long pos) throws IOException{
		Thread ct=Thread.currentThread();
		
		BlendInputStream inCache=inCacheMap.get(ct);
		
		if(inCache==null){
			inCache=reopen();
			inCacheMap.put(ct, inCache);
		}
		
		long toSkip=pos-inCache.position();
		
		if(toSkip<0){
			inCache.close();
			inCacheMap.remove(ct);
			return getSourceAt(pos);
		}
		
		if(toSkip>0){
			inCache.skipNBytes(toSkip);
		}
		
		BlendInputStream c=inCache;
		inCacheMap.remove(ct);
		return c;
	}
	
	private void putSource(BlendInputStream c) throws IOException{
		BlendInputStream inCache=inCacheMap.put(Thread.currentThread(), c);
		if(inCache!=null) inCache.close();
	}
	
	<T> T reopen(long pos, UnsafeFunction<BlendInputStream, T, IOException> session) throws IOException{
		BlendInputStream s=getSourceAt(pos);
		try{
			return session.apply(s);
		}finally{
			putSource(s);
		}
	}
	
	void reopen(long pos, UnsafeConsumer<BlendInputStream, IOException> session) throws IOException{
		BlendInputStream s=getSourceAt(pos);
		try{
			session.accept(s);
		}finally{
			putSource(s);
		}
	}
	
	private InputStream openSource() throws IOException{
		InputStream s1=source.apply(name);
		if(s1==null) throw new RuntimeException("missing "+name);
		return s1;
	}
	
	private ByteBuffer  fileCache;
	private InputStream cacheFiller;
	
	private BlendInputStream reopen() throws IOException{
		InputStream in;
		
		if(header.compressed){
			if(cacheFiller==null){
				cacheFiller=new GZIPInputStream(openSource());
				fileCache=ByteBuffer.allocate(1<<16);
			}
			
			class CacheReader extends InputStream{
				int pos;
				
				@SuppressWarnings("SynchronizeOnNonFinalField")
				private void ensure(int newBytes) throws IOException{
					synchronized(fileCache){
						int newLimit=pos+newBytes;
						int toRead  =newLimit-fileCache.position();
						
						while(toRead>0){
							if(!fileCache.hasRemaining()){//buffer is full, need to grow
								
								ByteBuffer old=fileCache;
								fileCache=ByteBuffer.allocate(old.capacity()<<1);
								old.flip();
								fileCache.put(old);
								continue;
							}
							
							int read=cacheFiller.read(fileCache.array(), fileCache.position(), fileCache.remaining());
							fileCache.position(fileCache.position()+read);
							
							if(read<=0){
								cacheFiller.close();
								cacheFiller=null;
								break;
							}
							toRead-=read;
							
						}
					}
				}
				
				@Override
				public int read(@NotNull byte[] b, int off, int len) throws IOException{
					ensure(off+len);
					System.arraycopy(fileCache.array(), pos, b, off, len);
					pos+=len;
					return len;
				}
				
				@Override
				public long skip(long n) throws IOException{
					ensure((int)n);
					pos+=n;
					return n;
				}
				
				@Override
				public int read() throws IOException{
					ensure(1);
					byte b=fileCache.get(pos);
					pos++;
					return b;
				}
			}
			in=new CacheReader();
		}else in=openSource();
		
		return new BlendInputStream(in, header);
	}
	
	@SuppressWarnings("AutoBoxing")
	FileBlockHeader getBlock(long ptr) throws BlendFileMissingBlock{
		FileBlockHeader b=blockPtrIndex.get(ptr);
		if(b==null){
			throw new BlendFileMissingBlock("invalid block ptr: "+ptr);
		}
		return b;
	}
	
	
	@Override
	public void close() throws IOException{
		if(cacheFiller!=null){
			cacheFiller.close();
			cacheFiller=null;
			fileCache=null;
		}
		
		for(BlendInputStream inCache : inCacheMap.values()){
			inCache.close();
		}
		inCacheMap=null;
	}
	
	@SuppressWarnings("AutoBoxing")
	Struct.Instance strayPointer(long ptr){
		return strayPointerType.new Instance(new Object[]{ptr}, this){
			@Override
			public int hashCode(){
				return Long.hashCode(ptr);
			}
		};
	}
	
	@Override
	public int compareTo(@NotNull BlendFile o){
		return o==this?0:name.compareTo(o.name);
	}
	
	@Override
	public String toString(){
		return name;
	}
	
	@Override
	protected void finalize() throws IOException{
		close();
	}

//	public void ay(){
//
//		try{
//			var tableField=HashMap.class.getDeclaredField("table");
//			tableField.setAccessible(true);
//
//			var table =(Object[])tableField.get(translationCache);
//			var counts=new Integer[table.length];
//
//			var entryClass=table.getClass().getComponentType();
//			var nextField =entryClass.getDeclaredField("next");
//			nextField.setAccessible(true);
//
//			for(int i=0;i<table.length;i++){
//				var e    =table[i];
//				int count=0;
//				if(e!=null){
//					do{
//						count++;
//					}while((e=nextField.get(e))!=null);
//				}
//				counts[i]=count;
//			}
//
//			LogUtil.println(translationCache.size(), counts.length);
//			LogUtil.printGraph(counts, 20, false, new LogUtil.Val<>("ay", '+', i->i+1));
//		}catch(ReflectiveOperationException e){
//			e.printStackTrace();
//		}
//	}
}
