package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileMissingBlock;
import com.lapissea.util.LogUtil;
import com.lapissea.util.NotImplementedException;
import com.lapissea.util.TextUtil;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

class DataParser{
	
	
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
	
	@SuppressWarnings("AutoBoxing")
	private static final List<TypeParser> PRIMITIVES=Arrays.asList(
			new TypeParserL(DnaType::isPointer, (type, data, blend)->{//first depointify anything
				var ptr=data.readPtr();
				if(ptr==0) return null;
				else try{
					return switch(type.pointerLevel){
						case 1 -> blend.readBlock(ptr);
						case 2 -> {
							var block=blend.getBlock(ptr);
							
							int count=block.bodySize/blend.header.ptrSize*block.count;
							var list =new ArrayList<>(count);
							blend.reopen(block.bodyFilePos, in->{
								for(int i=0;i<count;i++){
									var p=in.readPtr();
									if(p==0) list.add(null);
									else list.add(blend.readBlock(p));
								}
							});
							break Collections.unmodifiableList(list);
						}
						default -> throw new NotImplementedException();
					};
				}catch(BlendFileMissingBlock e){
					return blend.strayPointer(ptr);
				}
			}),
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
				
				String nullTerminatedCharArray(byte[] dat, BlendInputStream data) throws IOException{
					
					data.readNBytes(dat, 0, dat.length);
					
					StringBuilder sb=new StringBuilder(dat.length >> 1);
					
					for(byte c : dat){
						if(c==0) break;
						sb.append((char)c);
					}
					
					return sb.toString();
				}
				
				@Override
				public Object parse(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
					Objects.requireNonNull(type.arraySize);
					return switch(type.arraySize.size()){
						case 1 -> {
							
							byte[] dat=new byte[type.arraySize.get(0)];
							break nullTerminatedCharArray(dat, data);
						}
						case 2 -> {
							byte[]   dat  =new byte[type.arraySize.get(1)];
							String[] array=new String[type.arraySize.get(0)];
							for(int i=0;i<array.length;i++){
								array[i]=nullTerminatedCharArray(dat, data);
							}
							break array;
						}
						case 3 -> {
							byte[]     dat  =new byte[type.arraySize.get(2)];
							String[][] array=new String[type.arraySize.get(0)][type.arraySize.get(1)];
							for(String[] ar2 : array){
								for(int i=0;i<ar2.length;i++){
									ar2[i]=nullTerminatedCharArray(dat, data);
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
					return new StructLinkedList((Struct.Instance)arr, blend);
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
					Object[] array=new Struct.Instance[type.arraySize.get(0)];
					for(int i=0;i<array.length;i++){
						array[i]=DataParser.parse(type.dearrify(), data, blend);
					}
					return array;
				}
			});
	
	private static final boolean VALIDATE=true;
	private static final boolean PRINT   =false;
	
	static Struct.Instance parseStruct(DnaType type, BlendInputStream data, BlendFile blend) throws IOException{
		var struct=blend.dna.getStruct(type);
		
		var values=new ArrayList<>(struct.fields.size());
		
		long   startPos, p;
		String tab;
		
		if(VALIDATE){
			startPos=data.position();
			if(PRINT){
				tab=TextUtil.stringFill(Thread.currentThread().getStackTrace().length-19, ' ');
				LogUtil.println(tab, "======", type, "======");
			}
		}
		
		for(Field field : struct.fields){
			if(VALIDATE) p=data.position();

//			Object v;
//			if(field.type.name.endsWith("_Runtime")){
//				data.skipNBytes(field.type.size(blend));
//				v=null;
//			}else v=field.read(data, blend);
			Object v=field.read(data, blend);
			
			if(!VALIDATE&&!(v instanceof Number)&&field.name.startsWith("_pad")) v=null;
			
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
						if(v instanceof String){
							for(var n : ((String)v).toCharArray()){
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
						}else throw new RuntimeException();
						v=null;
					}
				}
				
				if(fail!=null){
					
					LogUtil.println("\nFailed to allocate:", struct);
					LogUtil.println("Reason:", fail);
					int pos=0;
					for(int i=0;i<values.size();i++){
						var vl=values.get(i);
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
			
			
			values.add(v);
		}
		
		if(VALIDATE&&PRINT) LogUtil.println();
		
		return struct.new Instance(values, blend);
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
