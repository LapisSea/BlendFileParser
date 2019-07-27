package com.lapissea.blendfileparser.exceptions;

public class BlendFileMissingValue extends RuntimeException{
	
	public BlendFileMissingValue(){
	}
	
	public BlendFileMissingValue(String message){
		super(message);
	}
	
	public BlendFileMissingValue(String message, Throwable cause){
		super(message, cause);
	}
	
	public BlendFileMissingValue(Throwable cause){
		super(cause);
	}
}
