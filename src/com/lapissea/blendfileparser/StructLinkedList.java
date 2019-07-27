package com.lapissea.blendfileparser;

import com.lapissea.util.TextUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class StructLinkedList implements List<Struct.Instance>{
	
	public static final Struct.Instance[] INS=new Struct.Instance[0];
	
	private       List<Struct.Instance> data;
	private       boolean               done;
	private final BlendFile             blend;
	
	public StructLinkedList(Struct.Instance first, BlendFile blend){
		this.blend=blend;
		
		if(first==null){
			data=List.of();
			done=true;
		}
		
		data=new LinkedList<>();
		data.add(first);
	}
	
	private void readTo(int size){
		while(!done&&size >= data.size()){
			read();
		}
	}
	
	private void readAll(){
		while(!done){
			read();
		}
	}
	
	
	private void finish(){
		data=new ArrayList<>(data);
		done=true;
	}
	
	private void read(){
		var last=data.get(data.size()-1);
		var next=last.getInstance("next");
		
		if(next==null){
			finish();
			return;
		}
		
		data.add(next);
		if(next.isAllocated()) read();
	}
	
	@Override
	public int size(){
		readAll();
		return data.size();
	}
	
	@Override
	public boolean isEmpty(){
		return data.isEmpty();
	}
	
	@Override
	public boolean contains(Object o){
		readAll();
		return data.contains(o);
	}
	
	@Override
	public @NotNull Iterator<Struct.Instance> iterator(){
		readAll();
		return data.iterator();
	}
	
	@Override
	public @NotNull Struct.Instance[] toArray(){
		readAll();
		return data.toArray(INS);
	}
	
	@SuppressWarnings("SuspiciousToArrayCall")
	@Override
	public @NotNull <T> T[] toArray(@NotNull T[] a){
		readAll();
		return data.toArray(a);
	}
	
	
	@Override
	public boolean containsAll(@NotNull Collection<?> c){
		readAll();
		return data.containsAll(c);
	}
	
	@Override
	public Struct.Instance get(int index){
		readTo(index);
		return data.get(index);
	}
	
	@Override
	public int indexOf(Object o){
		int index;
		do{
			index=data.indexOf(o);
			if(index!=-1) return index;
			read();
		}while(!done);
		return -1;
	}
	
	@Override
	public int lastIndexOf(Object o){
		readAll();
		return data.lastIndexOf(o);
	}
	
	@Override
	public @NotNull ListIterator<Struct.Instance> listIterator(){
		readAll();
		return data.listIterator();
	}
	
	@Override
	public @NotNull ListIterator<Struct.Instance> listIterator(int index){
		readAll();
		return data.listIterator(index);
	}
	
	@Override
	public @NotNull List<Struct.Instance> subList(int fromIndex, int toIndex){
		readTo(toIndex);
		return data.subList(fromIndex, toIndex);
	}
	
	@Override
	public boolean equals(Object obj){
		if(!(obj instanceof List)) return false;
		List l=(List)obj;
		readTo(l.size());
		return data.equals(obj);
	}
	
	@Override
	public int hashCode(){
		readAll();
		return data.hashCode();
	}
	
	@Override
	public String toString(){
		if(done) return data.toString();
		
		StringBuilder sb=new StringBuilder();
		sb.append('[');
		for(Struct.Instance datum : data){
			sb.append(TextUtil.toString(datum)).append(", ");
		}
		return sb.append("...]").toString();
	}
	
	@Override
	public boolean add(Struct.Instance instance){ throw new UnsupportedOperationException(); }
	
	@Override
	public boolean remove(Object o){ throw new UnsupportedOperationException(); }
	
	@Override
	public boolean addAll(@NotNull Collection<? extends Struct.Instance> c){ throw new UnsupportedOperationException(); }
	
	@Override
	public boolean addAll(int index, @NotNull Collection<? extends Struct.Instance> c){ throw new UnsupportedOperationException(); }
	
	@Override
	public boolean removeAll(@NotNull Collection<?> c){ throw new UnsupportedOperationException(); }
	
	@Override
	public boolean retainAll(@NotNull Collection<?> c){ throw new UnsupportedOperationException(); }
	
	@Override
	public void clear(){ throw new UnsupportedOperationException(); }
	
	@Override
	public Struct.Instance set(int index, Struct.Instance element){ throw new UnsupportedOperationException(); }
	
	@Override
	public void add(int index, Struct.Instance element){ throw new UnsupportedOperationException(); }
	
	@Override
	public Struct.Instance remove(int index){ throw new UnsupportedOperationException(); }
	
	
}