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

@SuppressWarnings("ALL")
public class Struct{
	
	static{
		TextUtil.IN_TABLE_TO_STRINGS.register(Struct.Instance.class, (Instance inst)->{
			if(inst.struct().type.name.equals("ID")) return inst.name();
			if(!inst.isAllocated()) return inst.toString();
			var name=inst.name();
			if(name!=null) return name;
			
			return inst.entrySet().stream().filter(e->!e.getKey().startsWith("_pad")&&e.getValue()!=null&&!"null".equals(e.getValue()))
			           .map(e->e.getKey()+": "+TextUtil.IN_TABLE_TO_STRINGS.toString(e.getValue()))
			           .collect(Collectors.joining(", "));
		});
	}
	
	private static final boolean LOG_ALOC      =false;
	private static final boolean VALIDATE_READS=true;
	
	private static final Stack<Instance> INSTANCE_STACK=new Stack<>();
	private static final String          NO_NAME       =new String(new char[0]);
	private static final ID              NO_ID         =new ID(new Object());
	
	public class Instance implements Map<String, Object>{
		
		private final long         dataStart;
		private final int          dataSize;
		private       List<Object> values;
		public final  BlendFile    blend;
		
		private int hash;
		
		public Instance(List<Object> values, BlendFile blend){
			dataStart=-1;
			dataSize=-1;
			if(values.size()!=fields.size()) throw new RuntimeException();//bruh moment
			this.values=Collections.unmodifiableList(values);
			this.blend=blend;
		}
		
		public Instance(FileBlockHeader blockHeader, BlendFile blend){
			this(blockHeader.bodyFilePos, blockHeader.bodySize, blend);
		}
		
		public Instance(long dataStart, int dataSize, BlendFile blend){
			this.dataStart=dataStart;
			this.dataSize=dataSize;
			this.blend=blend;
		}
		
		public boolean isAllocated(){
			return values!=null;
		}
		
		protected long validLength() throws IOException{
			return length;
		}
		
		public Instance allocate(){
			if(isAllocated()) return this;
			try{
				if(struct().type.name.equals("Link")){
					blend.reopen(dataStart, in->{
						LogUtil.println(in.readNBytes(dataSize));
					});
				}
				blend.reopen(dataStart, in->{
					long pos=in.position();
					
					values=DataParser.parseStruct(type, in, blend).values;
					
					if(VALIDATE_READS){
						long read=in.position()-pos;
						
						if(in.position()>dataStart+dataSize){
							LogUtil.printlnEr("Likely corruption of "+(name()==null?type:type+"("+name()+")")+"? outside block body", dataStart+dataSize+" / "+in.position());
//							LogUtil.println(this);
//							LogUtil.println(dataStart, dataSize);
//							System.exit(0);
						}
						var length=validLength();
						if(read!=length){
							LogUtil.printlnEr("Likely corruption of "+(name()==null?type:type+"("+name()+")")+"? read:", read, "but type size is:", length);
//							LogUtil.println(this);
//							System.exit(0);
						}
					}
				});
				
				if(LOG_ALOC){
					double d=dataSize;
					
					int level=0;
					while(d>1024){
						d/=1024F;
						level++;
					}
					
					String ds=Double.toString(Math.round(d*100.0)/100.0);
					if(ds.endsWith(".0")) ds=ds.substring(0, ds.length()-2);
					
					LogUtil.printTable("Allocated subject", struct().type.name+"("+name()+")", "Start at", "0x"+Long.toHexString(dataStart), "Object size", ds+List.of("b", "Kb", "Mb", "GB", "TB").get(level));
				}
			}catch(IOException e){
				throw UtilL.uncheckedThrow(e);
			}
			
			hash=calcHashCode();
			
			return this;
		}
		
		private String nameCache, fullNameCache;
		private ID idCache;
		
		public ID id(){
			if(idCache==null){
				var n=fullName();
				idCache=n==null?NO_ID:new ID(blend, n);
			}
			return idCache==NO_ID?null:idCache;
		}
		
		public String fullName(){
			
			if(fullNameCache==null){
				String fullName;
				try{
					var id=getInstance("id");
					fullName=id.type("name");
				}catch(BlendFileMissingValue|NullPointerException e){
					try{
						fullName=type("name");
					}catch(BlendFileMissingValue e1){
						fullName=null;
					}
				}
				
				if(fullName==null) fullName=NO_NAME;
				
				fullNameCache=fullName;
			}
			
			return fullNameCache==NO_NAME?null:fullNameCache;
		}
		
		public String name(){
			if(nameCache==null){
				var nameFull=fullName();
				if(nameFull==null){
					nameCache=NO_NAME;
					return null;
				}
				
				nameCache=nameFull.substring(switch(struct().type.name){
					case "Object", "Mesh", "Material", "bNodeTree", "Scene", "World",
							     "wmWindowManager", "WorkSpace", "bScreen", "FreestyleLineStyle",
							     "Lamp", "Brush", "Collection", "Camera", "Image", "bGPdata", "Key" -> 2;
					default -> 0;
				});
			}
			
			return nameCache==NO_NAME?null:nameCache;
		}
		
		@Override
		public String toString(){
			if(!isAllocated()) return type+"<0x"+Long.toHexString(dataStart)+">";
			if(struct().type.name.equals("StrayPointer")) return "0x"+Long.toHexString(getLong("badPtr"));
			String result=type+"{\n";
			synchronized(INSTANCE_STACK){
				INSTANCE_STACK.push(this);
				try{
					result+=IntStream.range(0, values.size()).mapToObj(i->{
						var v=values.get(i);
						if(v instanceof String){
							v="\""+((String)v).replace("\n", "\\n")+'"';
						}
						var f=fields.get(i);
						
						
						prettyfy:
						{
							if(this==v){
								v="<self>";
								break prettyfy;
							}
							
							if(INSTANCE_STACK.contains(v)){
								v="<circular_reference>";
								break prettyfy;
							}
							
							if(v instanceof Instance){
								Instance inst=(Instance)v;
								if(!inst.isAllocated()){
									v=v.toString();
									break prettyfy;
								}
								
								switch(f.name){
								case "next", "prev":{
									v=""+((Instance)v).struct().type.name+"<hidden>";
									break prettyfy;
								}
								}
							}
						}
						
						return "\t"+f.name+" = "+TextUtil.toString(v).replace("\n", "\n\t");
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
		
		public <T> List<T> getInstanceListTranslated(Object key){
			return getInstanceList(key).stream().map(blend::<T>translate).collect(Collectors.toUnmodifiableList());
		}
		
		public List<Instance> getInstanceList(Object key){
			var o=get(key);
			if(key instanceof Instance) return List.of((Instance)key);
			return (List<Instance>)o;
		}
		
		public Instance getInstance(Object key){
			return type(key);
		}
		
		public <T> T getInstanceTranslated(Object key){
			return blend.translate(getInstance(key));
		}
		
		public <T> T type(Object key){
			return (T)get(key);
		}
		
		@Override
		public Object get(Object key){
			try{
				return getExc(key);
			}catch(BlendFileMissingValue e){
				throw UtilL.uncheckedThrow(e);
			}
		}
		
		private Object getExc(Object key) throws BlendFileMissingValue{
			Integer id=fieldIndex.get(key);
			if(id==null) throw new BlendFileMissingValue("\""+key+"\" field missing in \""+type+"\"{"+fields.stream().map(field->field.name).collect(Collectors.joining(", "))+"}");
			
			var v=values().get(id);
			if(v instanceof Struct.Instance&&((Struct.Instance)v).struct().type.name.equals("StrayPointer")){
				throw new BlendFileMissingValue("\""+key+"\" is a stray pointer!");
			}
			return v;
		}
		
		public long getLong(Object key){
			var k=get(key);
			if(k instanceof Long) return (Long)k;
			else return ((Number)k).longValue();
		}
		
		public int getInt(Object key){
			var k=get(key);
			if(k instanceof Integer) return (Integer)k;
			else return ((Number)k).intValue();
		}
		
		public int getShort(Object key){
			var k=get(key);
			if(k instanceof Short) return (Short)k;
			else return ((Number)k).intValue();
		}
		
		public float getFloat(Object key){
			var k=get(key);
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
		
		@Override
		public @NotNull Set<String> keySet(){
			return fieldIndex.keySet();
		}
		
		@Override
		public @NotNull List<Object> values(){
			allocate();
			return values;
		}
		
		
		private class S implements Set<Entry<String, Object>>{
			
			Entry<String, Object>[] data=IntStream.range(0, size()).mapToObj(i->{
				return new Entry<String, Object>(){
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
				};
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
			
			@Override
			public @NotNull Object[] toArray(){
				return data.clone();
			}
			
			@NotNull
			@Override
			public <T> T[] toArray(@NotNull T[] a){
				if(a.length!=size()) a=(T[])Array.newInstance(a.getClass().getComponentType(), size());
				
				for(int i1=0;i1<a.length;i1++){
					a[i1]=(T)data[i1];
				}
				
				return a;
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
		
		@Override
		public @NotNull Set<Entry<String, Object>> entrySet(){
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
		public synchronized boolean equals(Object o){
			if(this==o) return true;
			if(!(o instanceof Instance)) return false;
			Instance instance=(Instance)o;
			
			if(this.dataStart!=-1&&instance.dataStart!=-1) return this.dataStart==instance.dataStart;
			
			if(!Objects.equals(struct(), struct())) return false;
			
			if(!Objects.equals(fullName(), instance.fullName())) return false;
			
			return Objects.equals(values(), instance.values());
		}
		
		private int calcHashCode(){
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
		
		public Struct struct(){
			return Struct.this;
		}
		
		public <T extends Enum<T>&FlagEnum> EnumSet<T> getFlagProps(Class<T> enumType){
			int        flag  =getInt("flag");
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
		var fieldIndex=new HashMap<String, Integer>(fields.size());
		
		for(int i=0;i<fields.size();i++){
			Field f=fields.get(i);
			if(fieldIndex.containsKey(f.name)){
				throw new RuntimeException(f.name+"\n"+TextUtil.toTable(fields));
			}
			fieldIndex.put(f.name, i);
		}
		
		return fieldIndex;
	}
	
	public static Struct read(BlendInputStream in, int id, String[] names, String[] types, short[] lengths) throws IOException{
		DnaType type;
		short   length;
		{
			var typeId=in.read2BInt();
			type=new DnaType(types[typeId], 0, null);
			length=lengths[typeId];
		}
		
		var variables=new Field[in.read2BInt()];
		
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
	
	@Override
	public boolean equals(Object o){
		if(this==o) return true;
		if(!(o instanceof Struct)) return false;
		Struct struct=(Struct)o;
		return type.equals(struct.type)&&
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
