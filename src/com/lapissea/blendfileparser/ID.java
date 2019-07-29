package com.lapissea.blendfileparser;

import com.lapissea.util.NotNull;
import com.lapissea.util.TextUtil;
import com.lapissea.util.UtilL;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class ID<T> extends AbstractList<T>{
	
	private T[] src;
	private int hash;
	
	
	@SafeVarargs
	public ID(T... data){
		if(data.length==0) throw new NullPointerException();
		setSrc(data);
	}
	
	protected ID(){ }
	
	protected void setSrc(@NotNull T[] src){
		for(Object o : src){
			Objects.requireNonNull(o);
		}
		this.src=src;
		hash=Arrays.hashCode(src);
	}
	
	@Override
	public T get(int index){
		return src[index];
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
	public ID<?> makeSubId(Object t){
		Class    tc  =UtilL.findClosestCommonSuper(src.getClass().getComponentType(), t.getClass());
		Object[] tArr=UtilL.array(tc, src.length+1);
		System.arraycopy(src, 0, tArr, 0, src.length);
		tArr[src.length]=t;
		return new ID(tc, tArr);
	}
	
	@Override
	public int hashCode(){
		Objects.requireNonNull(src);
		return hash;
	}
	
	@Override
	public int size(){
		return src.length;
	}
	
	@Override
	public String toString(){
		return "ID"+stream().map(TextUtil::toString).collect(Collectors.joining("->"));
	}
}
