package com.lapissea.blendfileparser.exceptions;

public class BlendFileUnknownType extends RuntimeException{
	
	public BlendFileUnknownType(){
	}
	
	public BlendFileUnknownType(String message){
		super(message);
	}
	
	public BlendFileUnknownType(String message, Throwable cause){
		super(message, cause);
	}
	
	public BlendFileUnknownType(Throwable cause){
		super(cause);
	}
}
