package com.lapissea.blendfileparser;

import com.lapissea.blendfileparser.exceptions.BlendFileIOException;

import java.util.Arrays;

public enum BlockCode{
	BRUSH("BR\0\0"),
	CAMERA("CA\0\0"),
	COLLECTION("GR\0\0"),
	DATA("DATA"),
	DNA1("DNA1"),
	END("ENDB"),
	FREESTYLE_LINE_STYLE("LS\0\0"),
	GLOBAL("GLOB"),
	IMAGE("IM\0\0"),
	LAMP("LA\0\0"),
	MATERIAL("MA\0\0"),
	MESH("ME\0\0"),
	NODE_TREE("NT\0\0"),
	OBJECT("OB\0\0"),
	RENDER("REND"),
	SCENE("SC\0\0"),
	SCREEN("SN\0\0"),
	TEST("TEST"),
	WINDOW_MANAGER("WM\0\0"),
	WORK_SPACE("WS\0\0"),
	GREESE_PENCIL_DATA("GD\0\0"),
	SHAPE_KEY("KE\0\0"),
	PARTICLE_SETTINGS("PA\0\0"),
	WORLD("WO\0\0"),
	ACTION("AC\0\0"),
	LINK("LI\0\0"),
	ID("ID\0\0");
	
	public final String id;
	
	BlockCode(String id){
		this.id=id;
	}
	
	public static BlockCode getById(String id) throws BlendFileIOException{
		return Arrays.stream(BlockCode.values())
		             .filter(b->b.id.equals(id))
		             .findAny()
		             .orElseThrow(()->new BlendFileIOException("Unknown BlockCode: \""+id+"\""));
	}
	
}
