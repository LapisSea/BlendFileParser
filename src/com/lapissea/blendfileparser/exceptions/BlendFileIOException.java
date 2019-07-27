package com.lapissea.blendfileparser.exceptions;

import java.io.IOException;

public class BlendFileIOException extends IOException{
	
	public BlendFileIOException(){
	}
	
	public BlendFileIOException(String message){
		super(message);
	}
	
	public BlendFileIOException(String message, Throwable cause){
		super(message, cause);
	}
	
	public BlendFileIOException(Throwable cause){
		super(cause);
	}
}
