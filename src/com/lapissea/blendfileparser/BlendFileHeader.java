package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileIOException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.nio.ByteOrder.*;

public class BlendFileHeader{
	
	public static final int BYTE_SIZE=7+1+1+3;
	
	public final int       ptrSize;
	public final ByteOrder order;
	public final byte[]    version;
	public final boolean   compressed;
	
	BlendFileHeader(InputStream in, boolean compressed) throws IOException{
		this.compressed=compressed;
		
		if(!Arrays.equals(in.readNBytes(7), "BLENDER".getBytes(StandardCharsets.US_ASCII))){
			throw new BlendFileIOException("Not a blend file");
		}
		
		ptrSize=switch(in.read()){
			case '-' -> 8;
			case '_' -> 4;
			default -> throw new BlendFileIOException("Unknown pointer size");
		};
		
		order=switch(in.read()){
			case 'V' -> BIG_ENDIAN;
			case 'v' -> LITTLE_ENDIAN;
			default -> throw new BlendFileIOException("Unknown byte order");
		};
		
		version=new byte[]{(byte)(in.read()-'0'), (byte)(in.read()-'0'), (byte)(in.read()-'0')};
	}
}
