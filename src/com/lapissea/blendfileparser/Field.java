package com.lapissea.blendfileparser;

import com.lapissea.util.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Field{
	
	@NotNull
	public final DnaType type;
	
	@NotNull
	public final String name;
	
	Field(@NotNull String type, @NotNull String name){
		
		int nameStart=0;
		int nameEnd  =name.length();
		
		List<Integer> arraySize;
		int           pointerLevel=0;
		while(name.charAt(nameStart)=='*'){
			pointerLevel++;
			nameStart++;
		}
		
		if(name.charAt(nameEnd-1)==']'){
			arraySize=new ArrayList<>(2);
			do{
				nameEnd--;
				int count=0;
				int siz  =0;
				while(name.charAt(--nameEnd)!='['){
					int point=1;
					for(int i=0;i<count;i++){
						point*=10;
					}
					count++;
					
					siz+=Character.getNumericValue(name.charAt(nameEnd))*point;
				}
				//noinspection AutoBoxing
				arraySize.add(0, siz);
			}while(name.charAt(nameEnd-1)==']');
			
			((ArrayList<Integer>)arraySize).trimToSize();
			arraySize=Collections.unmodifiableList(arraySize);
		}else arraySize=null;
		
		this.type=new DnaType(type, pointerLevel, arraySize);
		this.name=name.substring(nameStart, nameEnd);
	}
	
	
	Object read(BlendInputStream data, BlendFile blend) throws IOException{
		return DataParser.parse(type, data, blend);
	}
	
	@Override
	public String toString(){
		return name+"="+type;
	}
	
	@Override
	public boolean equals(Object o){
		if(this==o) return true;
		if(o instanceof String){
			return name.equals(o);
		}
		if(!(o instanceof Field)) return false;
		Field field=(Field)o;
		return type.equals(field.type)&&
		       name.equals(field.name);
	}
	
	@Override
	public int hashCode(){
		return name.hashCode();
	}
}
