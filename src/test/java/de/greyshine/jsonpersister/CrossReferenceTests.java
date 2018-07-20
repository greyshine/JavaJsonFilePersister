package de.greyshine.jsonpersister;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import de.greyshine.jsonpersister.objects.CcListElement;
import de.greyshine.jsonpersister.objects.CrossReferenceRoot;
import de.greyshine.jsonpersister.util.Utils;

public class CrossReferenceTests {
	
	private static final File storage = new File( "target/storage" );
	
	final JsonPersister jp = new JsonPersister( storage );
	
	@BeforeClass
	public static void beforeClass() {
		
		if ( storage.exists() ) {
			Utils.delete( storage );
		} 
		
		storage.mkdirs();
		
		Assert.assertTrue( storage.isDirectory() );
	}
	
	
	@Test
	public void test() throws IOException {
	
		// TODO idea is to build references, but i am missing the use case so far
		// one idea is to have an entity A reference entity B as well as entity c references entity B 
		
		CrossReferenceRoot crr = new CrossReferenceRoot();
		crr.ccListElementsList.add( new CcListElement() );
		crr.ccListElementsArray[0] = new CcListElement();
		crr.ccListElementsArray[2] = new CcListElement();
		
		crr.map1.put( "number1", new CcListElement());
		crr.map2.put( new CcListElement(), "number2");
		
		jp.upsert( crr );
		
	}

}
