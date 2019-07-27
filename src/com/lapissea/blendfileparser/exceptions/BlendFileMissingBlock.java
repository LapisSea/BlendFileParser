package com.lapissea.blendfileparser.exceptions;

import java.io.IOException;

public class BlendFileMissingBlock extends IOException{
	
	public BlendFileMissingBlock(){
	}
	
	public BlendFileMissingBlock(String message){
		super(message);
	}
	
	public BlendFileMissingBlock(String message, Throwable cause){
		super(message, cause);
	}
	
	public BlendFileMissingBlock(Throwable cause){
		super(cause);
	}
}
