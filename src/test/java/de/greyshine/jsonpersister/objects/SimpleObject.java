package de.greyshine.jsonpersister.objects;

import de.greyshine.jsonpersister.annotations.Id;

public class SimpleObject {

	@Id
	public String id;
	public String text;
	public String text2;
	
	public SimpleObject() {}
	
	public SimpleObject(long id) {
		this.id = String.valueOf(id);
	}
	
}
