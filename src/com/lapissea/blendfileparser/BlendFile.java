package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileMissingBlock;
import com.lapissea.datamanager.IDataSignature;
import com.lapissea.util.LogUtil;
import com.lapissea.util.NotNull;
import com.lapissea.util.UtilL;
import com.lapissea.util.function.TriFunction;
import com.lapissea.util.function.UnsafeConsumer;
import com.lapissea.util.function.UnsafeFunction;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.lapissea.blendfileparser.BlockCode.*;

public class BlendFile implements AutoCloseable, Comparable<BlendFile>{
	
	@NotNull
	@Contract("_ -> new")
	public static BlendFile read(IDataSignature blendFile) throws IOException{
		return new BlendFile(blendFile);
	}
	
	private       Map<Thread, BlendInputStream> inCache=new HashMap<>();
	private final IDataSignature                source;
	
	final         BlendFileHeader                 header;
	final         Dna1                            dna;
	private final TLongObjectMap<FileBlockHeader> blockMap;
	private final FileBlockHeader[]               blocks;
	private       Struct                          strayPointerType;
	
	private final Map<String, TriFunction<Struct, FileBlockHeader, BlendFile, TypeOptimizations.InstanceComposit>> typeOptimizations;
	
	private final Map<Struct.Instance, Object>                   translationCache=new HashMap<>();
	private final Map<String, Function<Struct.Instance, Object>> translators     =new HashMap<>();
	
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
				registerTranslatorSwitchFunc(structName, (Map<Object, Function<Struct.Instance, Object>>)ways.get(null), identifierVal);
			}else{
				registerTranslatorSwitchClass(structName, (Map<Object, Class<Object>>)ways.get(null), identifierVal);
			}
			
		}catch(ReflectiveOperationException e){
			throw UtilL.uncheckedThrow(e);
		}
	}
	
	private static <T> Function<Struct.Instance, Object> classToFunc(Class<T> tClass){
		try{
			Constructor<T> constructor=tClass.getConstructor(Struct.Instance.class);
			return inst->{
				try{
					return constructor.newInstance(inst);
				}catch(ReflectiveOperationException e){
					throw UtilL.uncheckedThrow(e);
				}
			};
		}catch(NoSuchMethodException e){
			throw UtilL.uncheckedThrow(e);
		}
	}
	
	public <T, E> void registerTranslatorSwitchClass(@NotNull String structName, @NotNull Map<E, Class<T>> ways, Function<Struct.Instance, E> identifier){
		registerTranslatorSwitchFunc(structName, ways.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e->classToFunc(e.getValue()))), identifier);
	}
	
	public <T, E> void registerTranslatorSwitchFunc(@NotNull String structName, @NotNull Map<E, Function<Struct.Instance, Object>> ways, Function<Struct.Instance, E> identifier){
		registerTranslator(structName, data->{
			var id =identifier.apply(data);
			var way=ways.get(id);
			if(way==null) throw new RuntimeException("Unable to resolve switch for "+id+" in "+structName);
			return way.apply(data);
		});
	}
	
	public <T> void registerTranslator(@NotNull Class<T> directTranslation){
		registerTranslator(directTranslation.getSimpleName(), directTranslation);
	}
	
	public <T> void registerTranslator(@NotNull String structName, @NotNull Class<T> directTranslation){
		Objects.requireNonNull(structName);
		Objects.requireNonNull(directTranslation);
		
		registerTranslator(structName, classToFunc(directTranslation));
	}
	
	public void registerTranslator(@NotNull String structName, @NotNull Function<Struct.Instance, Object> translator){
		Objects.requireNonNull(structName);
		Objects.requireNonNull(translator);
		translators.put(structName, translator);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T translate(Struct.Instance instance){
		
		T t=(T)translationCache.get(instance);
		if(t==null){
			translationCache.put(instance, t=(T)doTranslation(instance));
		}
		return t;
	}
	
	private Object doTranslation(Struct.Instance instance){
		var translator=translators.get(instance.struct().type.name);
		if(translator==null) throw new RuntimeException("No translator for "+instance.struct().type.name);
		var translated=translator.apply(instance);
		if(translated==null) throw new RuntimeException("Translator for "+instance.struct().type.name+" did not return an object");
		return translated;
	}
	
	private BlendFile(IDataSignature source) throws IOException{
		this.source=source;
		
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
		
		strayPointerType=new Struct(-1, (short)header.ptrSize, new DnaType("StrayPointer", 0, null), List.of(new Field("void", "badPtr")));
		
		
		final Dna1[]   dna      ={null};
		Consumer<Dna1> dnaSetter=d->dna[0]=d;
		
		var blockBuilder=new LinkedList<FileBlockHeader>();
		
		try(var in=reopen()){
			in.skip(BlendFileHeader.BYTE_SIZE);
			while(true){
				var block=new FileBlockHeader(in, dnaSetter);
				if(block.code==END) break;
				blockBuilder.add(block);
			}
		}
		
		this.dna=Objects.requireNonNull(dna[0]);
		
		typeOptimizations=TypeOptimizations.get(this.dna);
		
		blocks=blockBuilder.toArray(FileBlockHeader[]::new);
		
		blockMap=new TLongObjectHashMap<>(blocks.length);
		for(FileBlockHeader b : blocks){
			blockMap.put(b.oldPtr, b);
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
	
	private Object createBlock(FileBlockHeader blockHeader){
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
			Object obj=createBlock(blockHeader);
			blockHeader.bodyCache=new WeakReference<>(obj);
			return obj;
		}
		
		Object obj=blockHeader.bodyCache.get();
		if(obj!=null) return obj;
		
		blockHeader.bodyCache=null;
		
		return readBlock(blockHeader);
	}
	
	private BlendInputStream sourceAt(long pos) throws IOException{
		var ct=Thread.currentThread();
		
		var cached=inCache.get(ct);
		if(cached==null){
			inCache.put(ct, cached=reopen());
		}
		
		long toSkip=pos-cached.position();
		
		if(toSkip<0){
			cached.close();
			inCache.remove(ct);
			return sourceAt(pos);
		}
		
		if(toSkip>0){
			cached.skipNBytes(toSkip);
		}
		
		return cached;
	}
	
	@SuppressWarnings("TryFinallyCanBeTryWithResources")
	<T> T reopen(long pos, UnsafeFunction<BlendInputStream, T, IOException> session) throws IOException{
		var s =sourceAt(pos);
		var ct=Thread.currentThread();
		inCache.remove(ct);
		try{
			return session.apply(s);
		}finally{
			var cached=inCache.get(ct);
			if(cached!=null) cached.close();
			inCache.put(ct, s);
		}
	}
	
	@SuppressWarnings("TryFinallyCanBeTryWithResources")
	void reopen(long pos, UnsafeConsumer<BlendInputStream, IOException> session) throws IOException{
		var s =sourceAt(pos);
		var ct=Thread.currentThread();
		inCache.remove(ct);
		try{
			session.accept(s);
		}finally{
			var cached=inCache.get(ct);
			if(cached!=null){
				cached.close();
			}
			inCache.put(ct, s);
		}
	}
	
	private InputStream openSource(){
		var s1=source.getInStream();
		if(s1==null) throw new RuntimeException("missing "+source.getPath());
		return s1;
	}
	
	private static class Cache extends TByteArrayList{
		public Cache(){
			super(1<<12);
		}
		
		byte[] getData(){
			return _data;
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
					return blendFileCache.get(pos++);
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
		for(BlendInputStream value : inCache.values()){
			value.close();
		}
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
		return o==this?0:source.compareTo(o.source);
	}
	
	@Override
	public String toString(){
		return source.getPath();
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void finalize() throws IOException{
		close();
	}
}
