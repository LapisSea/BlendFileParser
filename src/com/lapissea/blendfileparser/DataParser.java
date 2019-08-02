package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileMissingBlock;
import com.lapissea.util.ArrayViewList;
import com.lapissea.util.LogUtil;
import com.lapissea.util.TextUtil;
import com.lapissea.util.UtilL;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

class DataParser{
	
	private static final boolean VALIDATE=false;
	private static final boolean PRINT   =false;
	
	
	private interface TypeParser{
		boolean canParse(DnaType type);
		
		Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException;
	}
	
	private static class TypeParserL implements TypeParser{
		
		private interface Parse{
			Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException;
		}
		
		private final Predicate<DnaType> canParse;
		private final Parse              parse;
		
		private TypeParserL(Predicate<DnaType> canParse, Parse parse){
			this.canParse=canParse;
			this.parse=parse;
		}
		
		@Override
		public boolean canParse(DnaType type){
			return canParse.test(type);
		}
		
		@Override
		public Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
			return parse.parse(type, data, blend);
		}
	}
	
	private static boolean type(DnaType type, String name){
		return !type.isArray()&&name.equals(type.name);
	}
	
	private static boolean typeArray(DnaType type, String name){
		return type.isArray()&&name.equals(type.name);
	}
	
	private static Predicate<DnaType> type(String name){
		return type->type(type, name);
	}
	
	private static Predicate<DnaType> typeArray(String name){
		return type->typeArray(type, name);
	}
	
	private static final List<TypeParser> PRIMITIVES;
	
	static{
		PRIMITIVES=Arrays.asList(
				new TypeParserL(DnaType::isPointer, (type, data, blend)->{//first depointify anything
					var ptr=data.readPtr();
					if(ptr==0) return null;
					else try{
						
						if(type.pointerLevel>1){
							var block=blend.getBlock(ptr);
							
							int count=block.bodySize/blend.header.ptrSize*block.count;
							var raw  =type.depointify();
							
							Object[] arr=new Object[count];
							blend.reopen(block.bodyFilePos, in->{
								for(int i=0;i<count;i++){
									arr[i]=DataParser.parse(raw, in, blend);
								}
							});
							return ArrayViewList.create(arr).obj2;
						}
						
						var block =blend.getBlock(ptr);
						var struct=block.getStruct();
						
						if(!type.is(struct.type.name)){
							
							if(!type.is("void")){
								var raw      =type.depointify();
								var knownSize=raw.size(blend);
								
								if(block.bodySize%knownSize!=0){
//								LogUtil.println(type, struct.type.name);
//								LogUtil.println(block, knownSize, block.bodySize%knownSize);
//								LogUtil.println(raw);
//								type=type.castTo(struct.type.name);
									
									return blend.readBlock(block);
//								throw new RuntimeException();//wtf??
								}
								
								Object[] arr=new Object[block.bodySize/knownSize];
								blend.reopen(block.bodyFilePos, in->{
									for(int i=0;i<arr.length;i++){
										arr[i]=DataParser.parse(raw, in, blend);
									}
								});
								
								return ArrayViewList.create(arr).obj2;
							}
							
							if(block.getStruct().id==0){
								return new Struct.UnknownData(blend, block);
							}
						}
						
						return blend.readBlock(block);
						
					}catch(BlendFileMissingBlock e){
						return blend.strayPointer(ptr);
					}
				}),
				new TypeParserL(t->t.isFunc, (t, d, b)->{
					var ptr=d.readPtr();
					if(ptr==0) return null;
					throw new RuntimeException(ptr+"");
//				return b.readBlock(ptr);
				}),
				new TypeParserL(type("void"), (t, d, b)->d.readPtr()),
				new TypeParser(){
					@Override
					public boolean canParse(DnaType type){ return typeArray(type, "short"); }
					
					@Override
					public Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
						Objects.requireNonNull(type.arraySize);
						return switch(type.arraySize.size()){
							case 1 -> {
								var     num  =type.arraySize.get(0);
								short[] array=new short[num];
								for(int i=0;i<array.length;i++){
									array[i]=data.read2BInt();
								}
								break array;
							}
							case 2 -> {
								short[][] array=new short[type.arraySize.get(0)][type.arraySize.get(1)];
								for(short[] ai : array){
									for(int j=0, j1=ai.length;j<j1;j++){
										ai[j]=data.read2BInt();
									}
								}
								break array;
							}
							case 3 -> {
								short[][][] array=new short[type.arraySize.get(0)][type.arraySize.get(1)][type.arraySize.get(2)];
								for(short[][] ai : array){
									for(short[] aij : ai){
										for(int k=0, k1=aij.length;k<k1;k++){
											aij[k]=data.read2BInt();
										}
									}
								}
								break array;
							}
							default -> throw new RuntimeException(type.arraySize.size()+" "+type.arraySize);
						};
					}
				},
				new TypeParser(){
					@Override
					public boolean canParse(DnaType type){ return typeArray(type, "int"); }
					
					
					@Override
					public Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
						Objects.requireNonNull(type.arraySize);
						return switch(type.arraySize.size()){
							case 1 -> {
								var   num  =type.arraySize.get(0);
								int[] array=new int[num];
								for(int i=0;i<array.length;i++){
									array[i]=data.read4BInt();
								}
								break array;
							}
							case 2 -> {
								int[][] array=new int[type.arraySize.get(0)][type.arraySize.get(1)];
								for(int[] ai : array){
									for(int j=0, j1=ai.length;j<j1;j++){
										ai[j]=data.read4BInt();
									}
								}
								break array;
							}
							case 3 -> {
								int[][][] array=new int[type.arraySize.get(0)][type.arraySize.get(1)][type.arraySize.get(2)];
								for(int[][] ai : array){
									for(int[] aij : ai){
										for(int k=0, k1=aij.length;k<k1;k++){
											aij[k]=data.read4BInt();
										}
									}
								}
								break array;
							}
							default -> throw new RuntimeException(type.arraySize.size()+" "+type.arraySize);
						};
					}
				},
				new TypeParser(){
					@Override
					public boolean canParse(DnaType type){ return typeArray(type, "char"); }
					
					@Override
					public Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
						Objects.requireNonNull(type.arraySize);
						return switch(type.arraySize.size()){
							case 1 -> {
								byte[] dat=new byte[type.arraySize.get(0)];
								data.readNBytes(dat, 0, dat.length);
								break dat;
							}
							case 2 -> {
								byte[]   dat  =new byte[type.arraySize.get(1)];
								byte[][] array=new byte[type.arraySize.get(0)][dat.length];
								for(byte[] bytes : array){
									data.readNBytes(bytes, 0, bytes.length);
								}
								break array;
							}
							case 3 -> {
								byte[]     dat  =new byte[type.arraySize.get(2)];
								byte[][][] array=new byte[type.arraySize.get(0)][type.arraySize.get(1)][dat.length];
								for(byte[][] ar2 : array){
									for(byte[] bytes : ar2){
										data.readNBytes(bytes, 0, bytes.length);
									}
								}
								break array;
							}
							default -> throw new RuntimeException(type.arraySize.size()+" "+type.arraySize);
						};
					}
				},
				new TypeParser(){
					@Override
					public boolean canParse(DnaType type){ return typeArray(type, "float"); }
					
					@Override
					public Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
						Objects.requireNonNull(type.arraySize);
						return switch(type.arraySize.size()){
							case 1 -> {
								var     num  =type.arraySize.get(0);
								float[] array=new float[num];
								for(int i=0;i<array.length;i++){
									array[i]=data.read4BFloat();
								}
								break array;
							}
							case 2 -> {
								float[][] array=new float[type.arraySize.get(0)][type.arraySize.get(1)];
								for(float[] ai : array){
									for(int j=0, j1=ai.length;j<j1;j++){
										ai[j]=data.read4BFloat();
									}
								}
								break array;
							}
							case 3 -> {
								float[][][] array=new float[type.arraySize.get(0)][type.arraySize.get(1)][type.arraySize.get(2)];
								for(float[][] ai : array){
									for(float[] aij : ai){
										for(int k=0, k1=aij.length;k<k1;k++){
											aij[k]=data.read4BFloat();
										}
									}
								}
								break array;
							}
							default -> throw new RuntimeException(type.arraySize.size()+" "+type.arraySize);
						};
					}
				},
				new TypeParserL(type("void"), (t, d, b)->d.readPtr()),
				new TypeParserL(type("int"), (t, d, b)->d.read4BInt()),
				new TypeParserL(type("char"), (t, d, b)->d.read()),
				new TypeParserL(type("uchar"), (t, d, b)->d.read()),
				new TypeParserL(type("short"), (t, d, b)->d.read2BInt()),
				new TypeParserL(type("ushort"), (t, d, b)->d.read2BInt()),
				new TypeParserL(type("long"), (t, d, b)->d.read8BInt()),
				new TypeParserL(type("uint64_t"), (t, d, b)->d.read8BInt()),
				new TypeParserL(type("int64_t"), (t, d, b)->d.read8BInt()),
				new TypeParserL(type("float"), (t, d, b)->d.read4BFloat()),
				new TypeParserL(type("double"), (t, d, b)->d.read8BFloat()),
				new TypeParserL(type("ListBase"), (type, data, blend)->{//allocate linked list
					long first=data.readPtr();
					long last =data.readPtr();
					if(first==0) return List.of();
					try{
						var arr=blend.readBlock(first);
						if(arr instanceof List) throw new RuntimeException();
						return new StructLinkedList((Struct.Instance)arr);
					}catch(BlendFileMissingBlock e){
						return blend.strayPointer(first);
					}
				}),
				new TypeParser(){
					@Override
					public boolean canParse(DnaType type){ return type.isArray(); }//struct array
					
					@SuppressWarnings("ConstantConditions")
					@Override
					public Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
						Object[] array=new Object[type.arraySize.get(0)];
						
						for(int i=0;i<array.length;i++){
							array[i]=DataParser.parse(type.dearrify(), data, blend);
						}
						
						Class<?> allType=Arrays.stream(array).filter(Objects::nonNull).map(o->(Class)o.getClass()).reduce(UtilL::findClosestCommonSuper).orElse(Object.class);
						if(allType==Object.class) return array;
						
						var typed=UtilL.array(allType, array.length);
						System.arraycopy(array, 0, typed, 0, array.length);
						return typed;
					}
				});
	}
	
	private static Struct.Instance parseStruct(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
		Struct struct   =blend.dna.getStruct(type);
		long   dataStart=data.position();
		var    values   =parseStructValues(struct, data, blend);//read data now as it is not a pointer and data is at easy access
		return struct.new Instance(values, blend, dataStart);
	}
	
	static Object[] parseStructValues(Struct struct, BlendInputStream data, BlendFile blend) throws IOException{
		
		var values=new Object[struct.fields.size()];
		
		long   startPos, p;
		String tab;
		
		if(VALIDATE){
			startPos=data.position();
			if(PRINT){
				tab=TextUtil.stringFill(Thread.currentThread().getStackTrace().length, ' ');
				LogUtil.println(tab, "======", struct.type, "======");
			}
		}
		
		List<Field> fields=struct.fields;
		
		for(int i1=0, j=fields.size();i1<j;i1++){
			Field field=fields.get(i1);
			
			if(Struct.IGNORE_VALUES.contains(field.name)){
				data.skipNBytes(field.type.size(blend));
				values[i1]=null;
				continue;
			}
			
			if(VALIDATE) p=data.position();
			
			Object v=field.read(data, blend);
			
			if(VALIDATE){
				long newP=data.position(),
						read=newP-p,
						s=field.type.size(blend);
				if(PRINT){
					//noinspection AutoBoxing
					LogUtil.println(tab, s, read, newP-startPos, field, "=", (v instanceof Struct.Instance?((Struct.Instance)v).struct().type:v));
				}
				
				String fail=null;
				if(s!=read) fail="Buffer position change indicates that number of allocated bytes ("+read+" bytes) is not equal to size of "+field+" ("+s+" bytes)\n"+
				                 "Likely incorrectly read value: "+v;
				else if(field.name.startsWith("_pad")&&v!=null){
					if(v instanceof Number){
						if(((Number)v).intValue()!=0) fail=field.name+" has to be 0";
						
					}else{
						if(v instanceof byte[]){
							for(var n : (byte[])v){
								if(n!=0){
									fail=field.name+" has to be all 0";
									break;
								}
							}
						}else if(v instanceof int[]){
							for(var n : (int[])v){
								if(n!=0){
									fail=field.name+" has to be all 0";
									break;
								}
							}
						}else throw new RuntimeException(v.getClass().getName());
						v=null;
					}
				}
				
				if(fail!=null){
					
					LogUtil.println("\nFailed to allocate:", struct);
					LogUtil.println("Reason:", fail);
					int pos=0;
					for(int i=0;i<values.length;i++){
						var vl=values[i];
						if(vl instanceof Struct.Instance){
							vl=((Struct.Instance)vl).struct().type;
						}
						LogUtil.printTable("field                              ", struct.fields.get(i),
						                   "value                              ", vl,
						                   "range                              ", pos+" to "+(pos+=field.type.size(blend)));
					}
					LogUtil.println();
					//noinspection AutoBoxing
					LogUtil.println("allocate:", newP-startPos, "out of:"+struct.length);
					byte[] mem=blend.reopen(startPos, in->{
						return in.readNBytes(struct.length);
					});
					LogUtil.println("Memory dump:", mem);
					new RuntimeException().printStackTrace();
					System.exit(0);
				}
			}
			
			values[i1]=v;
		}
		
		if(VALIDATE&&PRINT) LogUtil.println();
		
		return values;
	}
	
	static Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
		
		for(TypeParser parser : PRIMITIVES){
			if(parser.canParse(type)){
				return parser.parse(type, data, blend);
			}
		}
		
		return parseStruct(type, data, blend);
	}
	
}
