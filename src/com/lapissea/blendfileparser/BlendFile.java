package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileMissingBlock;
import com.lapissea.util.LogUtil;
import com.lapissea.util.NotNull;
import com.lapissea.util.UtilL;
import com.lapissea.util.function.TriFunction;
import com.lapissea.util.function.UnsafeConsumer;
import com.lapissea.util.function.UnsafeFunction;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.lapissea.blendfileparser.BlockCode.*;

public class BlendFile implements AutoCloseable, Comparable<BlendFile>{
	
	@NotNull
	public static BlendFile read(Supplier<InputStream> fileSource, String path) throws IOException{
		return new BlendFile(fileSource, path);
	}
	
	private final Supplier<InputStream> source;
	private final String                path;
	public final  ID<?>                 id;
	
	final         BlendFileHeader            header;
	public final  Dna1                       dna;
	private final Map<Long, FileBlockHeader> blockMap;
	private final FileBlockHeader[]          blocks;
	private       Struct                     strayPointerType;
	
	private final Map<String, TriFunction<Struct, FileBlockHeader, BlendFile, TypeOptimizations.InstanceComposite>> typeOptimizations;
	
	private final Map<Struct.Instance, Translator>                             translationCache=new HashMap<>();
	private final Map<String, Function<Struct.Instance, ? extends Translator>> translators     =new HashMap<>();
	
	public interface Translator{
		void translate(Struct.Instance data);
	}
	
	private BlendFile(Supplier<InputStream> fileSource, String path) throws IOException{
		source=fileSource;
		this.path=path;
		id=new ID<>(this);
		
		try(var blendFile=openSource()){
			
			if(!blendFile.markSupported()) throw new RuntimeException();
			
			blendFile.mark(BlendFileHeader.BYTE_SIZE);
			BlendFileHeader header;
			try{
				header=new BlendFileHeader(blendFile, false);
			}catch(IOException e){
				blendFile.reset();
				GZIPInputStream zipped=new GZIPInputStream(blendFile);
				header=new BlendFileHeader(zipped, true);
			}
			this.header=header;
		}
		
		strayPointerType=new Struct(-1, (short)header.ptrSize, new DnaType("StrayPointer", 0, false, null), List.of(new Field("void", "badPtr")));
		
		
		final Dna1[]   dna      ={null};
		Consumer<Dna1> dnaSetter=d->dna[0]=d;
		
		var blockBuilder=new LinkedList<FileBlockHeader>();
		
		try(var in=reopen()){
			in.skipNBytes(BlendFileHeader.BYTE_SIZE);
			while(true){
				var block=new FileBlockHeader(in, dnaSetter);
				if(block.code==END) break;
				blockBuilder.add(block);
			}
		}
		
		this.dna=Objects.requireNonNull(dna[0]);

//		LogUtil.println(this.dna.getStruct("bNodeSocketValueFloat"));
//		System.exit(0);
		
		typeOptimizations=TypeOptimizations.get(this.dna);
		
		blocks=blockBuilder.toArray(FileBlockHeader[]::new);
		
		blockMap=new HashMap<>(blocks.length);
		for(FileBlockHeader b : blocks){
			blockMap.put(b.oldPtr, b);
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
			var waysName      ="TRANSLATOR_SWITCH";
			var identifierName="translatorIdentifier";
			
			var ways=switchClass.getDeclaredField(waysName);
			ways.setAccessible(true);
			var identifier=switchClass.getDeclaredMethod(identifierName, Struct.Instance.class);
			identifier.setAccessible(true);
			
			if(ways.getType()!=Map.class) throw new RuntimeException(waysName+" needs to be type of "+Map.class.getName()+": "+switchClass);
			
			
			var generics=(ParameterizedType)ways.getGenericType();
			var args    =generics.getActualTypeArguments();
			if(args[0]!=identifier.getGenericReturnType()) throw new RuntimeException(waysName+" key generic type needs to be the same as "+identifierName+" generic type: "+switchClass);
			
			boolean isFunc;
			
			var keyBase=args[1];
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
				var k1=(ParameterizedType)keyBase;
				
				var k1raw=k1.getRawType();
				
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
			var id =identifier.apply(data);
			var way=ways.get(id);
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
	
	public <T extends Translator> void registerTranslator(@NotNull String structName, @NotNull Supplier<T> translator){
		registerTranslator(structName, data->translator.get());
	}
	
	public <T extends Translator> void registerTranslator(@NotNull String structName, @NotNull Function<Struct.Instance, T> translator){
		Objects.requireNonNull(structName);
		Objects.requireNonNull(translator);
		translators.put(structName, translator);
	}
	
	@SuppressWarnings({"unchecked", "SynchronizationOnLocalVariableOrMethodParameter"})
	public <T extends Translator> T translate(Struct.Instance instance){
		synchronized(instance){//need to synchronize over each instance separately to enable reading unrelated objects at the same time to preserve a high level of parallelism
			T t=(T)translationCache.get(instance);
			if(t==null){
				var translator=(Function<Struct.Instance, T>)translators.get(instance.struct().type.name);
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
		var struct=dna.getStruct(blockHeader.sdnaIndex);
		
		var optimization=typeOptimizations.get(struct.type.name);
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
		
		var data=new Struct.Instance[blockHeader.count];
		for(int i=0;i<data.length;i++){
			data[i]=struct.new Instance(blockHeader.bodyFilePos+struct.length*i, blockHeader.bodySize, this);
		}
		
		return List.of(data);
	}
	
	@NotNull
	Object readBlock(FileBlockHeader blockHeader){
		
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
	
	BlendInputStream inCache;
	
	
	private BlendInputStream getSourceAt(long pos) throws IOException{
		
		if(inCache==null){
			inCache=reopen();
		}
		
		long toSkip=pos-inCache.position();
		
		if(toSkip<0){
			inCache.close();
			inCache=null;
			return getSourceAt(pos);
		}
		
		if(toSkip>0){
			inCache.skipNBytes(toSkip);
		}
		var c=inCache;
		inCache=null;
		return c;
	}
	
	private void putSource(BlendInputStream c) throws IOException{
		if(inCache!=null) inCache.close();
		inCache=c;
	}
	
	synchronized <T> T reopen(long pos, UnsafeFunction<BlendInputStream, T, IOException> session) throws IOException{
		var s=getSourceAt(pos);
		try{
			return session.apply(s);
		}finally{
			putSource(s);
		}
	}
	
	synchronized void reopen(long pos, UnsafeConsumer<BlendInputStream, IOException> session) throws IOException{
		var s=getSourceAt(pos);
		inCache=null;
		try{
			session.accept(s);
		}finally{
			putSource(s);
		}
	}
	
	private InputStream openSource(){
		var s1=source.get();
		if(s1==null) throw new RuntimeException("missing "+path);
		return s1;
	}
	
	private static class Cache{
		byte[] data=new byte[1<<12];
		int    size=0;
		
		int size(){
			return size;
		}
		
		void ensureCapacity(int newCap){
			if(data.length >= newCap) return;
			
			byte[] old=data;
			
			data=new byte[old.length<<1];
			System.arraycopy(old, 0, data, 0, size);
		}
		
		byte[] getData(){
			return data;
		}
		
		public void add(byte[] src, int start, int read){
			ensureCapacity(size+read);
			System.arraycopy(src, 0, data, size, read);
			size+=read;
		}
	}
	
	private Cache       blendFileCache;
	private InputStream cacheFiller;
	
	private BlendInputStream reopen() throws IOException{
		InputStream in;
		
		if(header.compressed){
			if(cacheFiller==null){
				cacheFiller=new GZIPInputStream(openSource());
				blendFileCache=new Cache();
			}
			
			
			in=new InputStream(){
				int pos;
				byte[] bulk=new byte[1024*4];
				
				private void ensure(int newBytes) throws IOException{
					synchronized(cacheFiller){
						int newLimit=pos+newBytes;
						int toRead  =newLimit-blendFileCache.size();
						if(toRead<=0) return;
						
						blendFileCache.ensureCapacity(newLimit);
						while(toRead>0){
							var read=cacheFiller.read(bulk);
							if(read<=0){
								cacheFiller.close();
								cacheFiller=null;
								break;
							}
							toRead-=read;
							blendFileCache.add(bulk, 0, read);
						}
					}
				}
				
				@Override
				public int read(@NotNull byte[] b, int off, int len) throws IOException{
					ensure(off+len);
					System.arraycopy(blendFileCache.getData(), pos, b, off, len);
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
					var b=blendFileCache.getData()[pos];
					pos++;
					return b;
				}
			};
		}else in=openSource();
		
		return new BlendInputStream(in, header);
	}
	
	FileBlockHeader getBlock(long ptr) throws BlendFileMissingBlock{
		var b=blockMap.get(ptr);
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
		}
		
		blendFileCache=null;
		inCache.close();
		inCache=null;
	}
	
	@SuppressWarnings("AutoBoxing")
	Struct.Instance strayPointer(long ptr){
		return strayPointerType.new Instance(List.of(ptr), this){
			@Override
			public int hashCode(){
				return Long.hashCode(ptr);
			}
		};
	}
	
	@Override
	public int compareTo(@NotNull BlendFile o){
		return o==this?0:path.compareTo(o.path);
	}
	
	@Override
	public String toString(){
		return path;
	}
	
	@SuppressWarnings("deprecation")
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
