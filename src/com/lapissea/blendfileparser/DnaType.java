package com.lapissea.blendfileparser;

import com.lapissea.util.NotNull;
import com.lapissea.util.Nullable;
import com.lapissea.util.TextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DnaType{
	@NotNull
	public final String  name;
	public final int     pointerLevel;
	public final boolean isFunc;
	
	@Nullable
	public final List<Integer> arraySize;
	
	public DnaType(@NotNull String name, int pointerLevel, boolean isFunc, @Nullable List<Integer> arraySize){
		this.name=name;
		this.pointerLevel=pointerLevel;
		this.isFunc=isFunc;
		this.arraySize=arraySize;
	}
	
	public boolean isArray(){
		return arraySize!=null;
	}
	
	@Override
	public String toString(){
		return name+(arraySize!=null?arraySize.stream().map(i->"["+i+"]").collect(Collectors.joining()):"")+TextUtil.stringFill(pointerLevel, '*');
	}
	
	@Override
	public boolean equals(Object o){
		if(!(o instanceof DnaType)) return false;
		return equals((DnaType)o);
	}
	
	public boolean equals(DnaType o){
		if(this==o) return true;
		return pointerLevel==o.pointerLevel&&
		       name.equals(o.name)&&
		       Objects.equals(arraySize, o.arraySize);
	}
	
	@Override
	public int hashCode(){
		int result=31+name.hashCode();
		result=31*result+Integer.hashCode(pointerLevel);
		result=31*result+(arraySize!=null?arraySize.hashCode():0);
		return result;
	}
	
	public DnaType depointify(){
		if(!isPointer()) return this;
		return new DnaType(name, pointerLevel-1, isFunc, arraySize);
	}
	
	@SuppressWarnings("ConstantConditions")
	public DnaType dearrify(){
		if(!isArray()) return this;
		
		ArrayList<Integer> newSiz=new ArrayList<>(arraySize);
		newSiz.remove(0);
		newSiz.trimToSize();
		
		return new DnaType(name, pointerLevel, isFunc, newSiz.isEmpty()?null:Collections.unmodifiableList(newSiz));
	}
	
	public boolean isPointer(){
		return pointerLevel>0;
	}
	
	public boolean is(String type){
		return name.equals(type);
	}
	
	public Struct getStruct(BlendFile blend){
		return blend.dna.getStruct(this);
	}
	
	public int size(BlendFile blend){
		if(isPointer()) return blend.header.ptrSize;
		
		int s;
		if(isFunc) s=blend.header.ptrSize;
		else{
			switch(name){
			case "void":
				s=blend.header.ptrSize;
				break;
			case "char":
			case "uchar":
				s=1;
				break;
			case "short":
			case "ushort":
				s=2;
				break;
			case "int":
			case "float":
				s=4;
				break;
			case "uint64_t":
			case "int64_t":
			case "long":
			case "double":
				s=8;
				break;
			default:
				s=getStruct(blend).length;
				break;
			}
		}
		
		if(arraySize!=null){
			for(Integer i : arraySize){
				s*=i;
			}
		}
		
		return s;
	}
	
	public DnaType castTo(String newTypeName){
		return new DnaType(newTypeName, pointerLevel, isFunc, arraySize);
	}
}
