package com.lapissea.blendfileparser;

import com.lapissea.util.NotNull;
import com.lapissea.util.UtilL;

import java.io.IOException;
import java.util.*;

import static com.lapissea.util.UtilL.*;

class BlockArrayList<T> extends AbstractList<T> implements RandomAccess{
	
	private       Object[]  data;
	private final int       size;
	private       int       readPos;
	private final long      bodyFilePos;
	private       BlendFile blend;
	private       Struct    struct;
	
	public BlockArrayList(FileBlockHeader block, BlendFile blend){
		size=block.count;
		Assert(size()>1);
		bodyFilePos=block.bodyFilePos;
		struct=block.getStruct();
		data=new Object[Math.max(size()/10, 2)];
		this.blend=blend;
	}
	
	private void grow(int size){
		Object[] old=data;
		data=new Object[size];
		System.arraycopy(old, 0, data, 0, old.length);
	}
	
	private synchronized void readAll(){
		if(data.length!=size()) grow(size());
		readTo(size()-1);
	}
	
	private synchronized void readTo(int index){
		if(index<readPos) return;
		
		int step=Math.max(size()/10, 2);
		try{
			blend.reopen(bodyFilePos+struct.length*readPos, in->{
				while(index >= readPos){
					for(int i=0;i<step;i++){
						if(data.length==readPos) grow(Math.min(size(), data.length<<1));
						data[readPos++]=DataParser.parse(struct.type, in, blend);
						if(readPos==size()){
							blend=null;
							struct=null;
							return;
						}
					}
				}
			});
		}catch(IOException e){
			throw UtilL.uncheckedThrow(e);
		}
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public T get(int index){
		if(index<0||index >= size()) throw new IndexOutOfBoundsException(index+" "+size());
		readTo(index);
		return (T)data[index];
	}
	
	@Override
	public int size(){
		return size;
	}
	
	@NotNull
	@Override
	public Iterator<T> iterator(){
		readAll();
		return super.iterator();
	}
	
	@Override
	public Spliterator<T> spliterator(){
		readAll();
		return super.spliterator();
	}
}
