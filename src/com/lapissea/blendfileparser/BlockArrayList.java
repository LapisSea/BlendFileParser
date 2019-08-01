package com.lapissea.blendfileparser;

import com.lapissea.util.UtilL;

import java.io.IOException;
import java.util.AbstractList;
import java.util.Objects;

import static com.lapissea.util.UtilL.*;

class BlockArrayList<T> extends AbstractList<T>{
	
	private final Object[]  data;
	private       int       readPos;
	private final long      bodyFilePos;
	private       BlendFile blend;
	private       Struct    struct;
	
	public BlockArrayList(FileBlockHeader block, BlendFile blend){
		Assert(block.count>1);
		bodyFilePos=block.bodyFilePos;
		struct=block.getStruct();
		data=new Object[block.count];
		this.blend=blend;
	}
	
	private synchronized void readTo(int index){
		if(index<readPos) return;
		
		int step=Math.max(size()/10, 2);
		try{
			blend.reopen(bodyFilePos+struct.length*readPos, in->{
				while(index>=readPos){
					for(int i=0;i<step;i++){
						data[readPos]=DataParser.parse(struct.type, in, blend);
						readPos++;
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
		Objects.checkIndex(index, size());
		readTo(index);
		return (T)data[index];
	}
	
	@Override
	public int size(){
		return data.length;
	}
}
