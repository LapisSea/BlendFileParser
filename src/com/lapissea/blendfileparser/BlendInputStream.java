package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileIOException;
import com.lapissea.util.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BlendInputStream extends InputStream{
	
	private final InputStream     in;
	public final  BlendFileHeader header;
	
	private final ByteBuffer bb2;
	private final ByteBuffer bb4;
	private final ByteBuffer bb8;
	
	private long position;
	
	public BlendInputStream(InputStream in, BlendFileHeader header, long start){
		this(in, header);
		position=start;
	}
	
	public BlendInputStream(InputStream in, BlendFileHeader header){
		this.in=in;
		this.header=header;
		
		bb2=ByteBuffer.allocate(2).order(header.order);
		bb4=ByteBuffer.allocate(4).order(header.order);
		bb8=ByteBuffer.allocate(8).order(header.order);
	}
	
	private ByteBuffer read(ByteBuffer bb) throws IOException{
		byte[] a   =bb.array();
		int    read=read(a);
		if(read==-1) throw new BlendFileIOException("Unexpected file end");
		if(read!=a.length){
			bb.position(read);
			while(bb.hasRemaining()){
				int i1=read();
				if(i1==-1) throw new BlendFileIOException("Unexpected file end");
				bb.put((byte)i1);
			}
		}
		bb.position(0);
		return bb;
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
		int b=read();
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
		String[]   array=new String[arraySize];
		ByteBuffer bb   =ByteBuffer.allocate(64);
		for(int i=0;i<array.length;i++){
			int code;
			while((code=read())!=0){
				if(!bb.hasRemaining()){
					ByteBuffer old=bb;
					bb=ByteBuffer.allocate(bb.capacity()<<1);
					old.flip();
					bb.put(old);
				}
				bb.put((byte)code);
			}
			array[i]=new String(bb.array(), 0, bb.position(), StandardCharsets.UTF_8);
			bb.clear();
		}
		return array;
	}
	
	@Override
	public int read() throws IOException{
		int b=in.read();
		if(b!=-1) position++;
		return b;
	}
	
	@Override
	public int read(@NotNull byte[] b, int off, int len) throws IOException{
		int read=in.read(b, off, len);
		if(read!=-1) position+=read;
		return read;
	}
	
	@Override
	public void close() throws IOException{
		in.close();
	}
	
	@Override
	public long skip(long n) throws IOException{
		long skipped=in.skip(n);
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
	
	
	public void skipNBytes(long n) throws IOException{
		if(n<=0) return;
		
		long ns=skip(n);
		if(ns >= 0&&ns<n){
			n-=ns;
			while(n>0&&read()!=-1){//TODO: can maybe swap with continuous skipping?
				n--;
			}
			if(n!=0){
				throw new EOFException("Unexpected EOF");
			}
		}else if(ns!=n){
			throw new IOException("Unable to skip exactly");
		}
	}
}
