package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileIOException;
import com.lapissea.util.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class Dna1{
	
	private static void require(BlendInputStream in, String data) throws IOException{
		StringBuilder name=new StringBuilder(4);
		int           c;
		while(name.length()<4){
			c=in.read();
			if(c!=0) name.append((char)c);
		}
		String id=name.toString();
		if(!id.equals(data)) throw new BlendFileIOException("Invalid name! Need \""+data+"\", got \""+id+"\"");
	}
	
	private final Struct[]            structs;
	private final Map<String, Struct> structNameLookup;
	
	Dna1(BlendInputStream in) throws IOException{
		require(in, "SDNA");
		
		require(in, "NAME");
		int size =in.read4BInt();
		var names=in.readNullTerminatedUTF8Array(size);
		
		require(in, "TYPE");
		size=in.read4BInt();
		var types=in.readNullTerminatedUTF8Array(size);
		
		require(in, "TLEN");
		var lengths=in.readShortArray(size);
		
		
		require(in, "STRC");
		size=in.read4BInt();
		
		structs=new Struct[size];
		structNameLookup=new HashMap<>(structs.length);
		for(int i=0;i<structs.length;i++){
			var struct=Struct.read(in, i, names, types, lengths);
			structs[i]=struct;
			structNameLookup.put(struct.type.name, struct);
			
		}
		
	}
	
	Struct getStruct(int sdnaIndex){
		return structs[sdnaIndex];
	}
	
	@NotNull
	Struct getStruct(DnaType type){
		return getStruct(type.name);
	}
	
	@NotNull
	Struct getStruct(String name){
		var s=structNameLookup.get(name);
		if(s==null){
			throw new RuntimeException("Unkown type: "+name);
		}
		return s;
	}
}
