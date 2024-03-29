package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileMissingValue;
import com.lapissea.blendfileparser.flags.FlagEnum;
import com.lapissea.util.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.*;

public class Struct{
	
	static{
		TextUtil.IN_TABLE_TO_STRINGS.register(Struct.Instance.class, (Instance inst)->{
			if(inst.struct().type.name.equals("ID")) return inst.name();
			if(!inst.isAllocated()) return inst.toString();
			String name=inst.name();
			if(name!=null) return name;
			
			return inst.entrySet().stream().filter(e->!e.getKey().startsWith("_pad")&&e.getValue()!=null&&!"null".equals(e.getValue()))
			           .map(e->e.getKey()+": "+TextUtil.IN_TABLE_TO_STRINGS.toString(e.getValue()))
			           .collect(Collectors.joining(", "));
		});
	}
	
	static class UnknownData{
		private BlendFile       blend;
		private FileBlockHeader dataSource;
		private Object          data;
		
		UnknownData(BlendFile blend, FileBlockHeader dataSource){
			this.blend=blend;
			this.dataSource=dataSource;
		}
		
		@Override
		public String toString(){
			if(blend==null) return TextUtil.toString(data);
			return dataSource.code+"("+dataSource.getStruct().type+", count="+dataSource.count+", at "+dataSource.bodyFilePos+")";
		}
		
		private synchronized Object read(Struct struct){
			
			if(dataSource.bodySize%struct.length!=0||dataSource.bodySize/dataSource.count!=struct.length)
				throw new IllegalArgumentException("Struct "+struct.type.name+" with size of "+struct.length+" can not fit in to "+dataSource.bodySize+", "+dataSource.count+" "+TextUtil.plural("time", dataSource.count));
			
			if(data==null){
				try{
					data=blend.reopen(dataSource.bodyFilePos, in->{
						if(dataSource.count==1) return DataParser.parse(struct.type, in, blend);
						Object[] arr=new Object[dataSource.count];
						for(int i=0;i<arr.length;i++){
							arr[i]=DataParser.parse(struct.type, in, blend);
						}
						return ArrayViewList.create(arr).obj2;
					});
					blend=null;
					dataSource=null;
				}catch(IOException e){
					throw UtilL.uncheckedThrow(e);
				}
			}
			
			return data;
		}
	}
	
	public static final List<String> IGNORE_VALUES=new ArrayList<>(
			Arrays.asList("runtime",
			              "_pad", "_pad0", "_pad1", "_pad2", "_pad3", "_pad4",
			              "py_instance"
			             ));
	
	public static final Struct.Instance[] INS={};
	
	private static final boolean LOG_ALOC      =false;
	private static final boolean VALIDATE_READS=true;
	
	private static final Stack<Instance> INSTANCE_STACK=new Stack<>();
	private static final String          NO_NAME       =new String(new char[0]);
	private static final ID<?>           NO_ID         =new ID<>(new Object());
	
	private static final List<String> TYPE_NAMES_2_PREFIX=Arrays.asList("Object", "Mesh", "Material", "bNodeTree", "Scene", "World",
	                                                                    "wmWindowManager", "WorkSpace", "bScreen", "FreestyleLineStyle",
	                                                                    "Lamp", "Brush", "Collection", "Camera", "Image", "bGPdata", "Key",
	                                                                    "Library");
	
	public class Instance implements Map<String, Object>{
		
		private final long      dataStart;
		public final  BlendFile blend;
		
		private List<Object> values;
		
		private int hash;
		
		public Instance(Object[] values, BlendFile blend){
			this(values, blend, -1);
		}
		
		public Instance(Object[] values, BlendFile blend, long dataStart){
			this.dataStart=dataStart;
			allocateDone(values);
			this.blend=blend;
		}
		
		public Instance(FileBlockHeader blockHeader, BlendFile blend){
			this(blockHeader.bodyFilePos, blend);
		}
		
		public Instance(long dataStart, BlendFile blend){
			this.dataStart=dataStart;
			this.blend=blend;
		}
		
		public boolean isAllocated(){
			return values!=null;
		}
		
		protected long validLength(){
			return length;
		}
		
		private void allocateDone(Object[] values){
			if(values.length!=fields.size()) throw new RuntimeException();//bruh moment
			
			this.values=new AbstractList<Object>(){
				boolean[] safe=new boolean[values.length];
				
				Object secure(Object v){
					if(v==null) return null;
					
					if(v instanceof Instance){
						Instance inst=(Instance)v;
						if(inst.is("ID")){
							Library lib=inst.getInstanceTranslated("lib");
							if(lib!=null) return lib.get(inst.getString("name"));
						}
					}
					
					return v;
				}
				
				@Override
				public Object get(int index){
					if(!safe[index]){
						values[index]=secure(values[index]);
						safe[index]=true;
						
						safe:
						{
							for(boolean b : safe){
								if(!b) break safe;
							}
							Instance.this.values=ArrayViewList.create(values).obj2;
						}
					}
					return values[index];
				}
				
				@Override
				public int size(){
					return values.length;
				}
			};
			hash=calcHashCode();
		}
		
		public synchronized Instance allocate(){
			if(isAllocated()) return this;
			try{
				blend.reopen(dataStart, in->{
					long pos=in.position();
					
					allocateDone(DataParser.parseStructValues(struct(), in, blend));
					
					if(VALIDATE_READS){
						
						long read=in.position()-pos;
						
						short dataSize=struct().length;
						
						if(in.position()>dataStart+dataSize){
							LogUtil.printlnEr("Likely corruption of "+(name()==null?type:type+"("+name()+")")+"? outside block body", dataStart+dataSize+" / "+in.position());
							LogUtil.println(this);
							//noinspection AutoBoxing
							LogUtil.println(dataStart, dataSize);
							System.exit(0);
						}
						long length=validLength();
						if(read!=length){
							//noinspection AutoBoxing
							LogUtil.printlnEr("Likely corruption of "+(name()==null?type:type+"("+name()+")")+"? read:", read, "but type size is:", length);
							LogUtil.println(this);
							System.exit(0);
						}
					}
					
				});
			}catch(IOException e){
				throw UtilL.uncheckedThrow(e);
			}
			
			if(LOG_ALOC){
				double d=struct().length;
				
				int level=0;
				while(d>1024){
					d/=1024F;
					level++;
				}
				
				String ds=Double.toString(Math.round(d*100.0)/100.0);
				if(ds.endsWith(".0")) ds=ds.substring(0, ds.length()-2);
				
				LogUtil.printTable("Allocated subject", struct().type.name+"("+name()+")", "Start at", "0x"+Long.toHexString(dataStart), "Object size", ds+Arrays.asList("b", "Kb", "Mb", "GB", "TB").get(level));
			}
			
			return this;
		}
		
		private String nameCache, fullNameCache;
		private ID idCache;
		
		public ID id(){
			if(idCache==null){
				String n=fullName();
				idCache=n==null?NO_ID:new ID<>(blend, n);
			}
			return idCache==NO_ID?null:idCache;
		}
		
		public String fullName(){
			
			if(fullNameCache==null){
				String fullName;
				try{
					Instance id=getInstance("id");
					fullName=id.getString("name");
				}catch(BlendFileMissingValue|NullPointerException e){
					try{
						fullName=getString("name");
					}catch(BlendFileMissingValue e1){
						fullName=null;
					}
				}
				
				if(fullName==null) fullName=NO_NAME;
				
				fullNameCache=fullName;
			}
			
			//noinspection StringEquality
			return fullNameCache==NO_NAME?null:fullNameCache;
		}
		
		public String name(){
			if(nameCache==null){
				String nameFull=fullName();
				if(nameFull==null){
					nameCache=NO_NAME;
					return null;
				}
				
				if(TYPE_NAMES_2_PREFIX.contains(struct().type.name)){
					nameCache=nameFull.substring(2);
				}else{
					nameCache=nameFull;
				}
			}
			
			//noinspection StringEquality
			return nameCache==NO_NAME?null:nameCache;
		}
		
		@Override
		public String toString(){
			if(!isAllocated()) return type+"<0x"+Long.toHexString(dataStart)+">";
			if(is("StrayPointer")) return "0x"+Long.toHexString(getLong("badPtr"))+" -> ?";
			String result=type+"{\n";
			synchronized(INSTANCE_STACK){
				INSTANCE_STACK.push(this);
				try{
					result+=IntStream.range(0, values.size()).filter(i->!IGNORE_VALUES.contains(fields.get(i).name)).mapToObj(i->{
						Field  f=fields.get(i);
						Object v=values.get(i);
						if(v instanceof String){
							v="\""+((String)v).replace("\n", "\\n")+'"';
						}
						
						
						if(v instanceof byte[]){
							v="\""+getString(f.name)+'"';
						}
						
						
						prettyfy:
						{
							if(this==v){
								v="<self>";
								break prettyfy;
							}
							
							if(v instanceof Instance){
								Instance inst=(Instance)v;
								
								if(INSTANCE_STACK.contains(inst)){
									v="<circular_reference>";
									break prettyfy;
								}
								
								if(!inst.isAllocated()){
									v=v.toString();
									break prettyfy;
								}
								Struct s=((Instance)v).struct();
								if(INSTANCE_STACK.stream().anyMatch(i1->i1.struct().equals(s))) v=((Instance)v).struct().type.name+"<hidden>";
							}
						}
						
						return "    "+f.name+" = "+TextUtil.toString(v).replace("\n", "\n    ");
					}).collect(Collectors.joining(",\n"));
				}finally{
					INSTANCE_STACK.pop();
				}
			}
			return result+"\n}";
		}
		
		@Override
		public int size(){
			return fields.size();
		}
		
		@Override
		public boolean isEmpty(){
			return size()==0;
		}
		
		@Override
		public boolean containsKey(Object key){
			return fieldIndex.containsKey(key);
		}
		
		@Override
		public boolean containsValue(Object value){
			return values().contains(value);
		}
		
		public <T extends BlendFile.Translator> List<T> getInstanceListTranslated(Object key){
			List<Instance> l=getInstanceList(key);
			return l==null?null:l.stream().map(blend::<T>translate).collect(Collectors.toList());
		}
		
		@SuppressWarnings("unchecked")
		public List<Instance> getInstanceList(Object key){
			Object o=get(key);
			if(o instanceof Instance) return Collections.singletonList((Instance)o);
			if(o instanceof Instance[]) return Arrays.asList((Instance[])o);
			return (List<Instance>)o;
		}
		
		public <T extends BlendFile.Translator> T getInstanceTranslated(Object key, String typeName){
			return getInstance(key, typeName).translate();
		}
		
		public <T extends BlendFile.Translator> T getInstanceTranslated(Object key, Struct struct){
			return getInstance(key, struct).translate();
		}
		
		public Instance getInstance(Object key, String typeName){
			return (Instance)get(key, typeName);
		}
		
		public Instance getInstance(Object key, Struct struct){
			return (Instance)get(key, struct);
		}
		
		public Object get(Object key, String typeName){
			return get(key, blend.dna.getStruct(typeName));
		}
		
		public Object get(Object key, Struct struct){
			Object o=get(key);
			if(o==null) return null;
			
			if(o instanceof UnknownData){
				UnknownData ud=(UnknownData)o;
				return ud.read(struct);
			}
			
			Instance i=(Instance)o;
			if(!i.struct().equals(struct)) throw new ClassCastException(this.struct().type.name+"."+key+" is not "+struct.type.name);
			return i;
		}
		
		public Instance getInstance(Object key){
			Object o=get(key);
			if(o instanceof UnknownData) throw new RuntimeException("This data has no known type");
			return (Instance)o;
		}
		
		public <T extends BlendFile.Translator> T getInstanceTranslated(Object key){
			Instance inst=getInstance(key);
			return inst==null?null:inst.translate();
		}
		
		public <T extends BlendFile.Translator> T translate(){
			return blend.translate(this);
		}
		
		public String getString(String key){
			byte[] dat=type(key);
			
			int i=0;
			for(;i<dat.length;i++){
				if(dat[i]==0) break;
			}
			return new String(dat, 0, i, UTF_8);
		}
		
		@SuppressWarnings("unchecked")
		public <T> T type(Object key){
			return (T)get(key);
		}
		
		@Override
		public Object get(Object key){
			Integer id=fieldIndex.get(key);
			if(id==null) throw new BlendFileMissingValue("\""+key+"\" field missing in \""+type+"\"{"+fields.stream().map(field->field.name).collect(Collectors.joining(", "))+"}");
			
			Object v=values().get(id);
			if(v instanceof Instance){
				Instance i=(Instance)v;
				if(i.is("StrayPointer")) throw new BlendFileMissingValue("\""+key+"\" is a stray pointer!");
			}
			return v;
		}
		
		public long getLong(Object key){
			Object k=get(key);
			if(k instanceof Long) return (Long)k;
			else return ((Number)k).longValue();
		}
		
		public int getInt(Object key){
			Object k=get(key);
			if(k instanceof Integer) return (Integer)k;
			else return ((Number)k).intValue();
		}
		
		public byte getByte(Object key){
			Object k=get(key);
			if(k instanceof Byte) return (Byte)k;
			else return ((Number)k).byteValue();
		}
		
		public boolean getBoolean(Object key){
			return getByte(key)==1;
		}
		
		public short getShort(Object key){
			Object k=get(key);
			if(k instanceof Short) return (Short)k;
			else return ((Number)k).shortValue();
		}
		
		public float getFloat(Object key){
			Object k=get(key);
			if(k instanceof Float) return (Float)k;
			else return ((Number)k).floatValue();
		}
		
		@Override
		public Object put(String key, Object value){
			throw new UnsupportedOperationException();
		}
		
		@Override
		public Object remove(Object key){
			throw new UnsupportedOperationException();
		}
		
		@Override
		public void putAll(@NotNull Map<? extends String, ?> m){
			throw new UnsupportedOperationException();
		}
		
		@Override
		public void clear(){
			throw new UnsupportedOperationException();
		}
		
		@NotNull
		@Override
		public Set<String> keySet(){
			return fieldIndex.keySet();
		}
		
		@NotNull
		@Override
		public List<Object> values(){
			allocate();
			return values;
		}
		
		
		private class S implements Set<Entry<String, Object>>{
			
			@SuppressWarnings("unchecked")
			Entry<String, Object>[] data=IntStream.range(0, size()).mapToObj(i->new Entry<String, Object>(){
				@Override
				public String getKey(){
					return fields.get(i).name;
				}
				
				@Override
				public Object getValue(){
					return values().get(i);
				}
				
				@Override
				public Object setValue(Object value){
					throw new UnsupportedOperationException();
				}
			}).toArray(Entry[]::new);
			
			@Override
			public int size(){
				return Instance.this.size();
			}
			
			@Override
			public boolean isEmpty(){
				return Instance.this.isEmpty();
			}
			
			@Override
			public boolean contains(Object o){
				if(o==null) return false;
				for(Entry<String, Object> e : data){
					if(e.equals(o)) return true;
				}
				return false;
			}
			
			
			private class I implements Iterator<Entry<String, Object>>{
				int pos;
				
				@Override
				public boolean hasNext(){
					return pos<Instance.this.size();
				}
				
				@Override
				public Entry<String, Object> next(){
					return set.data[pos++];
				}
			}
			
			I i;
			
			@NotNull
			@Override
			public Iterator<Entry<String, Object>> iterator(){
				if(i==null) i=new I();
				return i;
			}
			
			@NotNull
			@Override
			public Object[] toArray(){
				return data.clone();
			}
			
			@NotNull
			@Override
			public <T> T[] toArray(@NotNull T[] a){
				Object[] arr;
				if(a.length==size()) arr=a;
				else arr=(Object[])Array.newInstance(a.getClass().getComponentType(), size());
				
				System.arraycopy(data, 0, arr, 0, a.length);
				
				//noinspection unchecked
				return (T[])arr;
			}
			
			@Override
			public boolean add(Entry<String, Object> stringObjectEntry){
				throw new UnsupportedOperationException();
			}
			
			@Override
			public boolean remove(Object o){
				throw new UnsupportedOperationException();
			}
			
			@Override
			public boolean containsAll(@NotNull Collection<?> c){
				throw new UnsupportedOperationException();
			}
			
			@Override
			public boolean addAll(@NotNull Collection<? extends Entry<String, Object>> c){
				throw new UnsupportedOperationException();
			}
			
			@Override
			public boolean retainAll(@NotNull Collection<?> c){
				throw new UnsupportedOperationException();
			}
			
			@Override
			public boolean removeAll(@NotNull Collection<?> c){
				throw new UnsupportedOperationException();
			}
			
			@Override
			public void clear(){
				throw new UnsupportedOperationException();
			}
		}
		
		private S set;
		
		@NotNull
		@Override
		public Set<Entry<String, Object>> entrySet(){
			if(set==null) set=new S();
			
			return set;
		}
		
		@Override
		public void forEach(BiConsumer<? super String, ? super Object> action){
			Objects.requireNonNull(action);
			for(int i=0, j=size();i<j;i++){
				action.accept(fields.get(i).name, values().get(i));
			}
		}
		
		@Override
		public boolean equals(Object o){
			if(!(o instanceof Instance)) return false;
			return equals((Instance)o);
		}
		
		public boolean equals(Instance instance){
			if(this==instance) return true;
			if(instance==null) return false;
			
			if(!Objects.equals(struct(), instance.struct())) return false;
			
			if(this.dataStart>0&&instance.dataStart>0&&blend==instance.blend) return this.dataStart==instance.dataStart;
			
			if(!Objects.equals(fullName(), instance.fullName())) return false;
			
			return Objects.equals(values(), instance.values());
		}
		
		private int calcHashCode(){
			
			if(dataStart!=-1){
				//noinspection AutoBoxing
				return Objects.hash(dataStart, struct());
			}
			
			String name=fullName();
			if(name!=null) return name.hashCode();
			
			int result=31+struct().hashCode();
			result=31*result+values().hashCode();
			
			return result;
		}
		
		@Override
		public int hashCode(){
			allocate();
			return hash;
		}
		
		public boolean is(String typeName){
			return struct().is(typeName);
		}
		
		public Struct struct(){
			return Struct.this;
		}
		
		public <T extends Enum<T>&FlagEnum> EnumSet<T> getFlagProps(Class<T> enumType){
			return getFlagProps("flag", enumType);
		}
		
		public <T extends Enum<T>&FlagEnum> EnumSet<T> getFlagProps(String key, Class<T> enumType){
			int        flag  =getInt(key);
			EnumSet<T> result=EnumSet.allOf(enumType);
			result.removeIf(e->!e.matchesFlag(flag));
			return result;
		}
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public final int         id;
	public final short       length;
	public final DnaType     type;
	public final List<Field> fields;
	
	private final Map<String, Integer> fieldIndex;
	private final int                  hash;
	
	private static Map<String, Integer> makeIndex(List<Field> fields){
		HashMap<String, Integer> fieldIndex=new HashMap<>(fields.size());
		
		for(int i=0;i<fields.size();i++){
			Field f=fields.get(i);
			if(fieldIndex.containsKey(f.name)){
				throw new RuntimeException(f.name+"\n"+TextUtil.toTable(fields));
			}
			//noinspection AutoBoxing
			fieldIndex.put(f.name, i);
		}
		
		return fieldIndex;
	}
	
	public static Struct read(BlendInputStream in, int id, String[] names, String[] types, short[] lengths) throws IOException{
		DnaType type;
		short   length;
		{
			short typeId=in.read2BInt();
			type=new DnaType(types[typeId], 0, false, null);
			length=lengths[typeId];
		}
		
		Field[] variables=new Field[in.read2BInt()];
		
		for(int i=0;i<variables.length;i++){
			variables[i]=new Field(types[in.read2BInt()], names[in.read2BInt()]);
		}
		
		return new Struct(id, length, type, ArrayViewList.create(variables).obj2);
	}
	
	public Struct(int id, short length, DnaType type, List<Field> fields){
		this.id=id;
		this.type=Objects.requireNonNull(type);
		this.length=length;
		this.fields=Objects.requireNonNull(fields);
		
		fieldIndex=makeIndex(fields);
		hash=Arrays.hashCode(new int[]{this.type.hashCode(), this.fields.hashCode()});
	}
	
	public boolean is(String typeName){
		return type.is(typeName);
	}
	
	@Override
	public boolean equals(Object o){
		if(this==o) return true;
		if(!(o instanceof Struct)) return false;
		Struct struct=(Struct)o;
		return type.equals(struct.type)&&
		       fields.size()==struct.fields.size()&&
		       fields.equals(struct.fields);
	}
	
	@Override
	public int hashCode(){
		return hash;
	}
	
	@Override
	public String toString(){
		return type.name+"{\n"+fields.stream().map(f->"    "+f.type+" "+f.name+";\n").collect(Collectors.joining())+"}";
	}
}
