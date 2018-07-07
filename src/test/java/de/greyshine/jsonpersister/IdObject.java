package de.greyshine.jsonpersister;

import de.greyshine.jsonpersister.annotations.Id;

public class IdObject {
	
	@Id
	public String id;
	
	public long time = System.currentTimeMillis();

	public IdObject() {
	}
	
	public IdObject(String id) {
		this.id=id;
	}
	
	public String toString() {
		return "IdObject [id="+id+", time="+ time +"]";
	}

	public IdObject id(String id) {
		this.id = id;
		return this;
	}
	
}
