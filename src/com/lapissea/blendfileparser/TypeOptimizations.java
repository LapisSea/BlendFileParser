package com.lapissea.blendfileparser;

import com.lapissea.util.NotNull;
import com.lapissea.util.UtilL;
import com.lapissea.util.function.TriFunction;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;

import static com.lapissea.blendfileparser.Util.*;
import static com.lapissea.util.UtilL.*;

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
		
		public synchronized SELF allocate(){
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
			allocate();
			return new View();
		}
	}
	
	public static class MEdge extends InstanceComposite<MEdge> implements Iterable<MEdge.View>{
		private int[] indices;
		
		public MEdge(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			super(struct, blockHeader, blend);
		}
		
		@Override
		protected void readValues(int count, BlendInputStream in) throws IOException{
			indices=new int[count*2];
			
			for(int i=0;i<count;i++){
				indices[i*2+0]=in.read4BInt();
				indices[i*2+1]=in.read4BInt();
				in.skipNBytes(1+1+2);
			}
			
		}
		
		public int getV1(int i){
			return indices[i*2+0];
		}
		
		public int getV2(int i){
			return indices[i*2+1];
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
			allocate();
			return new MEdge.View();
		}
	}
	
	public static class MVert extends InstanceComposite<MVert> implements Iterable<MVert.View>{
		public float[] co;
		public short[] no;
		
		public MVert(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			super(struct, blockHeader, blend);
		}
		
		@Override
		protected void readValues(int count, BlendInputStream in) throws IOException{
			ByteBuffer  buffB=ByteBuffer.allocate(struct.length).order(blend.header.order);
			byte[]      arr  =buffB.array();
			FloatBuffer buff =buffB.asFloatBuffer();
			
			co=new float[count*3];
			no=new short[count*3];
			for(int i=0;i<count;i++){
				readNBytes(in, arr);
				
				int off=i*3;
				buffB.position(0);
				co[off+0]=buffB.getFloat();
				co[off+1]=buffB.getFloat();
				co[off+2]=buffB.getFloat();
				
				no[off+0]=buffB.getShort();
				no[off+1]=buffB.getShort();
				no[off+2]=buffB.getShort();
				
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
			allocate();
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
			allocate();
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
		
		public float[] getUv(){
			return uv;
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
			allocate();
			return new MLoopUV.View();
		}
	}
	
	@SuppressWarnings("PointlessBitwiseExpression")
	public static class MLoopCol extends InstanceComposite<MLoopCol> implements Iterable<MLoopCol.View>{
		private byte[] rgba;
		
		public MLoopCol(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			super(struct, blockHeader, blend);
		}
		
		@Override
		protected void readValues(int count, BlendInputStream in) throws IOException{
			rgba=new byte[count*4];
			ByteBuffer bb=ByteBuffer.wrap(rgba);
			while(bb.hasRemaining()){
				int read=in.read(bb.array(), bb.position(), bb.remaining());
				if(read==-1) throw new IOException("UEOF");
				bb.position(bb.position()+read);
			}
		}
		
		public byte[] getRgba(){
			allocate();
			return rgba;
		}
		
		public MLoopCol getRgba(int i, byte[] dest){
			System.arraycopy(rgba, i*4, dest, 0, 4);
			return this;
		}
		
		public int getRgba(int i){
			i*=4;
			return ((rgba[i++]&0xFF)<<24)|
			       ((rgba[i++]&0xFF)<<16)|
			       ((rgba[i++]&0xFF)<<8)|
			       ((rgba[i]&0xFF)<<0);
		}
		
		public byte getR(int i){
			return rgba[i*4+0];
		}
		
		public byte getG(int i){
			return rgba[i*4+1];
		}
		
		public byte getB(int i){
			return rgba[i*4+2];
		}
		
		public byte getA(int i){
			return rgba[i*4+3];
		}
		
		public class View implements Iterator<MLoopCol.View>{
			private int pos=-1;
			
			@Override
			public boolean hasNext(){
				return pos+1<count;
			}
			
			@Override
			public MLoopCol.View next(){
				pos++;
				return this;
			}
			
			
			public MLoopCol.View getRgba(byte[] dest){
				MLoopCol.this.getRgba(pos, dest);
				return this;
			}
			
			public int getRgba(){
				return MLoopCol.this.getRgba(pos);
			}
			
			public byte getR(){
				return MLoopCol.this.getR(pos);
			}
			
			public byte getG(){
				return MLoopCol.this.getG(pos);
			}
			
			public byte getB(){
				return MLoopCol.this.getB(pos);
			}
			
			public byte getA(){
				return MLoopCol.this.getA(pos);
			}
			
		}
		
		@NotNull
		@Override
		public Iterator<MLoopCol.View> iterator(){
			allocate();
			return new MLoopCol.View();
		}
	}
	
	public static class MDeformVert extends InstanceComposite<MDeformVert> implements Iterable<MDeformVert.View>{
		public float[][] weights;
		public short[][] weightIndices;
		
		public MDeformVert(Struct struct, FileBlockHeader blockHeader, BlendFile blend){
			super(struct, blockHeader, blend);
		}
		
		@Override
		protected void readValues(int count, BlendInputStream in) throws IOException{
			weights=new float[count][];
			weightIndices=new short[count][];
			for(int i=0;i<count;i++){
				Object w        =DataParser.parse(new DnaType("MDeformWeight", 1, false, null), in, blend);
				int    totweight=in.read4BInt();
				int    flag     =in.read4BInt();
				
				if(w instanceof Struct.Instance){
					Assert(totweight==1);
					Struct.Instance ins=((Struct.Instance)w);
					weights[i]=new float[]{ins.getFloat("weight")};
					weightIndices[i]=new short[]{ins.getShort("def_nr")};
				}else{
					List<Struct.Instance> l=(List<Struct.Instance>)w;
					Assert(totweight==l.size());
					Assert(l.size()<Short.MAX_VALUE);
					float[] ws =weights[i]=new float[l.size()];
					short[] ids=weightIndices[i]=new short[l.size()];
					for(int i1=l.size()-1;i1 >= 0;i1--){
						Struct.Instance ins=l.get(i1);
						ws[i1]=ins.getFloat("weight");
						ids[i1]=ins.getShort("def_nr");
					}
				}
				
			}
		}
		
		public float[] getWeights(int i){
			return weights[i];
		}
		
		public short[] getWeightIndices(int i){
			return weightIndices[i];
		}
		
		public class View implements Iterator<MDeformVert.View>{
			private int pos=-1;
			
			@Override
			public boolean hasNext(){
				return pos+1<count;
			}
			
			@Override
			public MDeformVert.View next(){
				pos++;
				return this;
			}
			
			public float[] getWeights(){
				return MDeformVert.this.getWeights(pos);
			}
			
			public short[] getWeightIndices(){
				return MDeformVert.this.getWeightIndices(pos);
			}
			
		}
		
		@NotNull
		@Override
		public Iterator<MDeformVert.View> iterator(){
			allocate();
			return new MDeformVert.View();
		}
	}
	
	private static void register(Dna1 dna,
	                             Map<String, TriFunction<Struct, FileBlockHeader, BlendFile, InstanceComposite>> map, String name,
	                             TriFunction<Struct, FileBlockHeader, BlendFile, InstanceComposite> func,
	                             String... names){
		Struct struct=dna.getStruct(name);
		if(names.length==0) throw new RuntimeException();
		if(!struct.fields.equals(Arrays.asList(names))){
			throw new RuntimeException("Non standard data! Need:\n"+Arrays.asList(names)+"\nGot:\n"+struct);
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
		register(dna, result, "MDeformVert", MDeformVert::new, "dw", "totweight", "flag");
		register(dna, result, "MLoopCol", MLoopCol::new, "r", "g", "b", "a");
		
		return result;
	}
	
}
