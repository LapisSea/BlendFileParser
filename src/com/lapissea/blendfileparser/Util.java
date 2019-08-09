package com.lapissea.blendfileparser;

import java.io.IOException;
import java.io.InputStream;

class Util{
	
	static byte[] readNBytes(InputStream is, int n) throws IOException{
		byte[] b=new byte[n];
		return readNBytes(is, b);
	}
	
	static byte[] readNBytes(InputStream is, byte[] b) throws IOException{
		int readTotal=0;
		
		while(b.length>readTotal){
			int toRead=b.length-readTotal;
			int read  =is.read(b, readTotal, toRead);
			if(read<=0) break;
			
			readTotal+=read;
		}
		
		if(b.length>readTotal) throw new IOException("Unexpected EOF");
		
		return b;
	}
	
}
