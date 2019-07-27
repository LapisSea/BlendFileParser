package com.lapissea.blendfileparser;

import com.lapissea.util.ArrayViewList;
import com.lapissea.util.NotNull;
import com.lapissea.util.TextUtil;
import com.lapissea.util.UtilL;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ID<T>{
	@NotNull
	public final  List<T> data;
	private final T[]     src;
	private final int     hash;
	
	@SafeVarargs
	public ID(T... data){
		this(computeType(data), data);
	}
	
	@SuppressWarnings("unchecked")
	private static <T> Class<T> computeType(T[] data){
		if(data.length==0) return null;
		Class c=data[0].getClass();
		for(int i=1;i<data.length;i++){
			c=UtilL.findClosestCommonSuper(c, data[i].getClass());
		}
		return c;
	}
	
	@SafeVarargs
	public ID(Class<T> type, T... data){
		if(data.length==0) throw new NullPointerException();
		
		for(Object o : data){
			Objects.requireNonNull(o);
			if(!UtilL.instanceOf(o, type)) throw new ClassCastException(o.toString()+" is not instanceof "+type);
		}
		
		this.data=ArrayViewList.create(data).obj2;
		src=data;
		hash=Arrays.hashCode(src);
	}
	
	@Override
	public boolean equals(Object o){
		if(!(o instanceof ID)) return false;
		return equals((ID)o);
	}
	
	public boolean equals(ID id){
		if(this==id) return true;
		return Arrays.equals(src, id.src);
	}
	
	/**
	 * <code>
	 * var thisId=new ID<>("a", "b", "c");<br/>
	 * var otherId=new ID<>("a", "b");<br/>
	 * <br/>
	 * thisId.isSubId(otherId) = true<br/>
	 * otherId.isSubId(thisId) = false<br/>
	 * </code>
	 */
	
	public boolean isSubId(ID<T> other){
		if(other==this) return true;
		
		Object[] a1=src;
		Object[] a2=other.src;
		
		if(a1.length<a2.length) return false;
		
		for(int i=0;i<src.length;i++){
			if(!Objects.equals(a1[i], a2[i]))
				return false;
		}
		return Arrays.equals(src, other.src);
	}
	
	@SuppressWarnings("unchecked")
	public ID<T> makeSubId(T t){
		var tc  =(Class<T>)src.getClass().getComponentType();
		T[] tArr=UtilL.array(tc, src.length+1);
		System.arraycopy(src, 0, tArr, 0, src.length);
		tArr[src.length]=t;
		return new ID<>(tc, tArr);
	}
	
	@Override
	public int hashCode(){
		return hash;
	}
	
	@Override
	public String toString(){
		return "ID"+data.stream().map(TextUtil::toString).collect(Collectors.joining("->"));
	}
}
