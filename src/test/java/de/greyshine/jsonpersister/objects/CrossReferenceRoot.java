package de.greyshine.jsonpersister.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.greyshine.jsonpersister.annotations.Id;

public class CrossReferenceRoot {
	
	@Id
	public String id;
	
	public CcListElement singleElement;
	
	public final List<CcListElement> ccListElementsList = new ArrayList<>();
	
	public final CcListElement[] ccListElementsArray = new CcListElement[3];
	
	public final Map<String,CcListElement> map1 = new HashMap<>();
	
	public final Map<CcListElement,String> map2 = new HashMap<>();

}
