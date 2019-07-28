package com.lapissea.blendfileparser;

import com.lapissea.util.NotNull;
import com.lapissea.util.UtilL;
import com.lapissea.util.function.TriFunction;
import com.lapissea.vec.Vec3f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"unchecked", "PointlessArithmeticExpression"})
public class TypeOptimizations{
	
	public abstract static class InstanceComposite<SELF extends InstanceComposite<SELF>>{
		private   FileBlockHeader blockHeader;
		protected BlendFile       blend;
		protected Struct          struct;
		
		public final int count;
		
		public InstanceComposite(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			count=blockHeader.count;
			this.blockHeader=blockHeader;
			this.blend=blend;
			this.struct=struct;
		}
		
		protected abstract void readValues(int count, BlendInputStream in) throws IOException;
		
		public SELF allocate(){
			if(blend!=null){
				try{
					blend.reopen(blockHeader.bodyFilePos, in->{ readValues(count, in); });
				}catch(IOException e){UtilL.uncheckedThrow(e);}
				blockHeader=null;
				blend=null;
			}
			return (SELF)this;
		}
		
		
	}
	
	public static class MPoly extends InstanceComposite<MPoly> implements Iterable<MPoly.View>{
		
		public int[]   loopstart;
		public int[]   totloop;
		public short[] mat_nr;
		
		public MPoly(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			super(struct, blockHeader, blend);
		}
		
		@Override
		protected void readValues(int count, BlendInputStream in) throws IOException{
			loopstart=new int[count];
			totloop=new int[count];
			mat_nr=new short[count];
			
			for(int i=0;i<count;i++){
				loopstart[i]=in.read4BInt();
				totloop[i]=in.read4BInt();
				mat_nr[i]=in.read2BInt();
				in.skipNBytes(1+1);
			}
			
		}
		
		
		public int getLoopStart(int i){
			return loopstart[i];
		}
		
		
		public int getTotLoop(int i){
			return totloop[i];
		}
		
		public short getMatNr(int i){
			return mat_nr[i];
		}
		
		public class View implements Iterator<View>{
			private int pos=-1;
			
			@Override
			public boolean hasNext(){
				return pos+1<count;
			}
			
			@Override
			public View next(){
				pos++;
				return this;
			}
			
			
			public int getLoopStart(){
				return MPoly.this.getLoopStart(pos);
			}
			
			
			public int getTotLoop(){
				return MPoly.this.getTotLoop(pos);
			}
			
			public short getMatNr(){
				return MPoly.this.getMatNr(pos);
			}
		}
		
		@NotNull
		@Override
		public Iterator<View> iterator(){
			return new View();
		}
	}
	
	public static class MEdge extends InstanceComposite<MEdge> implements Iterable<MEdge.View>{
		private int[] v1;
		private int[] v2;
		
		public MEdge(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			super(struct, blockHeader, blend);
		}
		
		@Override
		protected void readValues(int count, BlendInputStream in) throws IOException{
			v1=new int[count];
			v2=new int[count];
			
			for(int i=0;i<count;i++){
				v1[i]=in.read4BInt();
				v2[i]=in.read4BInt();
				in.skipNBytes(1+1+2);
			}
			
		}
		
		public int getV1(int i){
			return v1[i];
		}
		
		public int getV2(int i){
			return v2[i];
		}
		
		
		public class View implements Iterator<MEdge.View>{
			private int pos=-1;
			
			@Override
			public boolean hasNext(){
				return pos+1<count;
			}
			
			@Override
			public MEdge.View next(){
				pos++;
				return this;
			}
			
			
			public int getV1(){
				return MEdge.this.getV1(pos);
			}
			
			
			public int getV2(){
				return MEdge.this.getV2(pos);
			}
		}
		
		@NotNull
		@Override
		public Iterator<MEdge.View> iterator(){
			return new MEdge.View();
		}
	}
	
	public static class MVert extends InstanceComposite<MVert> implements Iterable<MVert.View>{
		public float[] co;
		public float[] no;
		
		public MVert(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			super(struct, blockHeader, blend);
		}
		
		@Override
		protected void readValues(int count, BlendInputStream in) throws IOException{
			var   buffB=ByteBuffer.allocate(struct.length).order(blend.header.order);
			var   arr  =buffB.array();
			var   buff =buffB.asFloatBuffer();
			Vec3f vec  =new Vec3f();
			
			co=new float[count*3];
			no=new float[count*3];
			for(int i=0;i<count;i++){
				in.readNBytes(arr, 0, arr.length);
//				LogUtil.println(arr);
				int off=i*3;
//				buff.position(0).get(co, off, 3);
				buffB.position(0);
				co[off+0]=buffB.getFloat();
				co[off+1]=buffB.getFloat();
				co[off+2]=buffB.getFloat();
//				buffB.position(12);
				vec.set(buffB.getShort(), buffB.getShort(), buffB.getShort()).normalise();
				no[off+0]=vec.x();
				no[off+1]=vec.y();
				no[off+2]=vec.z();
			}

//			LogUtil.println(co);
			
		}
		
		public MVert getCo(int i, float[] dest){
			System.arraycopy(co, i*3, dest, 0, 3);
			return this;
		}
		
		public MVert getNo(int i, float[] dest){
			System.arraycopy(no, i*3, dest, 0, 3);
			return this;
		}
		
		
		public class View implements Iterator<MVert.View>{
			private int pos=-1;
			
			@Override
			public boolean hasNext(){
				return pos+1<count;
			}
			
			@Override
			public MVert.View next(){
				pos++;
				return this;
			}
			
			
			public MVert getCo(float[] dest){
				return MVert.this.getCo(pos, dest);
			}
			
			
			public MVert getNo(float[] dest){
				return MVert.this.getNo(pos, dest);
			}
		}
		
		@NotNull
		@Override
		public Iterator<MVert.View> iterator(){
			return new MVert.View();
		}
	}
	
	public static class MLoop extends InstanceComposite<MLoop> implements Iterable<MLoop.View>{
		public int[] v;
		public int[] e;
		
		public MLoop(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			super(struct, blockHeader, blend);
		}
		
		@Override
		protected void readValues(int count, BlendInputStream in) throws IOException{
			v=new int[count];
			e=new int[count];
			
			for(int i=0;i<count;i++){
				v[i]=in.read4BInt();
				e[i]=in.read4BInt();
			}
			
		}
		
		public int getV(int i){
			return v[i];
		}
		
		public int getE(int i){
			return e[i];
		}
		
		
		public class View implements Iterator<MLoop.View>{
			private int pos=-1;
			
			@Override
			public boolean hasNext(){
				return pos+1<count;
			}
			
			@Override
			public MLoop.View next(){
				pos++;
				return this;
			}
			
			
			public int getV(){
				return MLoop.this.getV(pos);
			}
			
			
			public int getE(){
				return MLoop.this.getE(pos);
			}
		}
		
		@NotNull
		@Override
		public Iterator<MLoop.View> iterator(){
			return new MLoop.View();
		}
	}
	
	public static class MLoopUV extends InstanceComposite<MLoopUV> implements Iterable<MLoopUV.View>{
		private float[] uv;
		
		public MLoopUV(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			super(struct, blockHeader, blend);
		}
		
		@Override
		protected void readValues(int count, BlendInputStream in) throws IOException{
			uv=new float[count*2];
			
			for(int i=0;i<count;i++){
				uv[i*2]=in.read4BInt();
				uv[i*2+1]=in.read4BInt();
				in.skipNBytes(4);
			}
			
		}
		
		public MLoopUV getUv(int i, float[] dest){
			System.arraycopy(uv, i*2, dest, 0, 2);
			return this;
		}
		
		
		public class View implements Iterator<MLoopUV.View>{
			private int pos=-1;
			
			@Override
			public boolean hasNext(){
				return pos+1<count;
			}
			
			@Override
			public MLoopUV.View next(){
				pos++;
				return this;
			}
			
			
			public MLoopUV getV(float[] dest){
				return MLoopUV.this.getUv(pos, dest);
			}
			
		}
		
		@NotNull
		@Override
		public Iterator<MLoopUV.View> iterator(){
			return new MLoopUV.View();
		}
	}
	
	private static void register(Dna1 dna,
	                             Map<String, TriFunction<Struct, FileBlockHeader, BlendFile, InstanceComposite>> map, String name,
	                             TriFunction<Struct, FileBlockHeader, BlendFile, InstanceComposite> func,
	                             String... names){
		var struct=dna.getStruct(name);
		if(names.length==0) throw new RuntimeException();
		//noinspection EqualsBetweenInconvertibleTypes
		if(!struct.fields.equals(List.of(names))){
			throw new RuntimeException("Non standard data! Need:\n"+List.of(names)+"\nGot:\n"+struct);
		}
		
		map.put(name, func);
	}
	
	public static Map<String, TriFunction<Struct, FileBlockHeader, BlendFile, InstanceComposite>> get(Dna1 dna){
		Map<String, TriFunction<Struct, FileBlockHeader, BlendFile, InstanceComposite>> result=new HashMap<>();
		
		register(dna, result, "MVert", MVert::new, "co", "no", "flag", "bweight");
		register(dna, result, "MLoop", MLoop::new, "v", "e");
		register(dna, result, "MLoopUV", MLoopUV::new, "uv", "flag");
		register(dna, result, "MEdge", MEdge::new, "v1", "v2", "crease", "bweight", "flag");
		register(dna, result, "MPoly", MPoly::new, "loopstart", "totloop", "mat_nr", "flag", "_pad");
		
		return result;
	}
	
}
