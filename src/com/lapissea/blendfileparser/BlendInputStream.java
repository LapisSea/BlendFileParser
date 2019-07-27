package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileIOException;
import gnu.trove.list.array.TByteArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class BlendInputStream extends InputStream{
	
	private final InputStream     in;
	public final  BlendFileHeader header;
	
	private final ByteBuffer bb2;
	private final ByteBuffer bb4;
	private final ByteBuffer bb8;
	
	private long position;
	
	public BlendInputStream(InputStream in, BlendFileHeader header){
		this.in=in;
		this.header=header;
		
		bb2=ByteBuffer.allocate(2).order(header.order);
		bb4=ByteBuffer.allocate(4).order(header.order);
		bb8=ByteBuffer.allocate(8).order(header.order);
	}
	
	ByteBuffer read(ByteBuffer bb) throws IOException{
		var a   =bb.array();
		var read=read(a);
		if(read==-1) throw new BlendFileIOException("Unexpected file end");
		if(read!=a.length){
			bb.position(read);
			while(bb.hasRemaining()){
				int i1=read();
				if(i1==-1) throw new BlendFileIOException("Unexpected file end");
				bb.put((byte)i1);
			}
		}
		
		return bb.position(0);
	}
	
	String read4ByteString() throws IOException{
		return new String(read(bb4).array());
	}
	
	short read2BInt() throws IOException{
		return read(bb2).getShort();
	}
	
	int read4BInt() throws IOException{
		return read(bb4).getInt();
	}
	
	float read4BFloat() throws IOException{
		return read(bb4).getFloat();
	}
	
	double read8BFloat() throws IOException{
		return read(bb8).getDouble();
	}
	
	long read8BInt() throws IOException{
		return read(bb8).getLong();
	}
	
	long readPtr() throws IOException{
		return header.ptrSize==4?read4BInt():read8BInt();
	}
	
	boolean readBool() throws IOException{
		return read1BInt()==1;
	}
	
	int read1BInt() throws IOException{
		var b=read();
		if(b==-1) throw new BlendFileIOException("Unexpected file end");
		return b;
	}
	
	public short[] readShortArray(int arraySize) throws IOException{
		short[] array=new short[arraySize];
		for(int i=0;i<array.length;i++){
			array[i]=read2BInt();
		}
		return array;
	}
	
	public String[] readNullTerminatedUTF8Array(int arraySize) throws IOException{
		String[] array=new String[arraySize];
		
		var l=new TByteArrayList(64);
		for(int i=0;i<array.length;i++){
			int code;
			while((code=read())!=0){
				l.add((byte)code);
			}
			array[i]=new String(l.toArray(), Charset.forName("UTF-8"));
			l.clear();
		}
		return array;
	}
	
	@Override
	public int read() throws IOException{
		var b=in.read();
		if(b!=-1) position++;
		return b;
	}
	
	@Override
	public int read(@NotNull byte[] b, int off, int len) throws IOException{
		var read=in.read(b, off, len);
		if(read!=-1) position+=read;
		return read;
	}
	
	@Override
	public void close() throws IOException{
		in.close();
	}
	
	@Override
	public long skip(long n) throws IOException{
		var skipped=in.skip(n);
		position+=skipped;
		return skipped;
	}
	
	@Override
	public int available() throws IOException{
		return in.available();
	}
	
	public long position(){
		return position;
	}
}
