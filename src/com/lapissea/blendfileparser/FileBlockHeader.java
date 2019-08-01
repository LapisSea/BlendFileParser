package com.lapissea.blendfileparser;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;

import static com.lapissea.blendfileparser.BlockCode.*;

public class FileBlockHeader{
	
	WeakReference<Object> bodyCache;
	
	public final  BlockCode code;
	public final  int       bodySize;
	public final  long      oldPtr;
	private final int       sdnaIndex;
	public final  int       count;
	public final  long      bodyFilePos;
	
	public FileBlockHeader(BlendInputStream in, Consumer<Dna1> dnaSetter) throws IOException{
		
		code=BlockCode.getById(in.read4ByteString());
		bodySize=in.read4BInt();
		oldPtr=in.readPtr();
		sdnaIndex=in.read4BInt();
		count=in.read4BInt();
		bodyFilePos=in.position();
		
		if(code==DNA1) dnaSetter.accept(new Dna1(in));
		else in.skipNBytes(bodySize);
		
	}
	
	private Struct struct;
	
	void init(Dna1 dna){
		struct=dna.getStruct(sdnaIndex);
	}
	
	public Struct getStruct(){
		return struct;
	}
}
