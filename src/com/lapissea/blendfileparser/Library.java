package com.lapissea.blendfileparser;

import java.io.IOException;

public class Library implements BlendFile.Translator{
	
	private String name;
	private String path;
	
	private BlendFile file;
	
	private BlendFile thisBlend;
	
	@Override
	public void translate(Struct.Instance data){
		name=data.name();
		Struct.Instance id=data.getInstance("id");
		path=data.getString("name");
		thisBlend=data.blend;
	}
	
	public String getName(){
		return name;
	}
	
	public String getPath(){
		return path;
	}
	
	public synchronized Struct.Instance get(String name){
		if(file==null){
			if(thisBlend==null) return null;
			allocate(name);
			if(file==null) return null;
		}
		BlockCode code;
		findCode:
		{
			for(BlockCode c : BlockCode.values()){
				if(c.id.regionMatches(0, name, 0, 2)){
					code=c;
					break findCode;
				}
			}
			throw new IllegalArgumentException("Name "+name+" does not have block code header");
		}
		
		return file.readSingleBlockByCode(code)
		           .filter(i->i.fullName().equals(name))
		           .findAny()
		           .orElse(null);
	}
	
	private void allocate(String name){
		try{
			if(path.startsWith("//")){
				file=BlendFile.read(thisBlend.source, path.substring(2));
				thisBlend.translators.forEach(file::registerTranslator);
				return;
			}
			throw new RuntimeException(path);
		}catch(IOException e){
			e.printStackTrace();
		}finally{
			thisBlend=null;
		}
	}
}
