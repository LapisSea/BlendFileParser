package com.lapissea.blendfileparser;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;

import static com.lapissea.blendfileparser.BlockCode.*;

public class FileBlockHeader{
	
	WeakReference<Object> bodyCache;
	
	final BlockCode code;
	final int       bodySize;
	final long      oldPtr;
	final int       sdnaIndex;
	final int       count;
	final long      bodyFilePos;
	
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
}
