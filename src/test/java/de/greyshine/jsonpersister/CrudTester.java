package de.greyshine.jsonpersister;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import de.greyshine.jsonpersister.objects.SimpleObject;
import de.greyshine.jsonpersister.util.Utils;

public class CrudTester {
	
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
	public void createReadUpdateDelete() throws IOException {
		
		final SimpleObject createSimpleObject = new SimpleObject();
		createSimpleObject.id = System.currentTimeMillis();
		createSimpleObject.text = "Hallo";
		createSimpleObject.text2 = null;
		
		final String id = jp.insert( createSimpleObject );
		Assert.assertNotNull( id );
		jp.isExisting( SimpleObject.class, id );
		
		final SimpleObject readSimpleObject = jp.read( SimpleObject.class, id );
		Assert.assertEquals( createSimpleObject.id , readSimpleObject.id);
		
		readSimpleObject.text = "Hallo2";
		jp.upsert( id, readSimpleObject );
		final SimpleObject readSimpleObject2 = jp.read( SimpleObject.class, id );
		Assert.assertEquals( readSimpleObject.id , readSimpleObject2.id);
		Assert.assertEquals( "Hallo2" , readSimpleObject2.text);
		
		jp.delete( SimpleObject.class, id );
		final SimpleObject readSimpleObject3 = jp.read( SimpleObject.class, id );
		Assert.assertNull( readSimpleObject3 );
	} 
	
	@Test
	public void testPresetId() throws IOException {
		
		final IdObject createIdObject = new IdObject();
		
		String id = jp.insert( createIdObject );
		System.out.println( "ID: "+ id );
		System.out.println( createIdObject );
		
		final IdObject createIdObject2 = new IdObject();
		createIdObject2.id = "4711";
		
		id = jp.insert( createIdObject2 );
		System.out.println( createIdObject2 );
		
		Assert.assertEquals( "4711" , id);
	}
	
	@Test
	public void testTravers() throws IOException {
		
		jp.insert( new IdObject().id("1") );
		jp.insert( new IdObject().id("2") );
		jp.insert( new IdObject().id("3") );
		jp.insert( new IdObject().id("4") );
		jp.insert( new IdObject().id("5") );
		
		final Set<String> ids = new HashSet<>();
		ids.add( "1" );
		ids.add( "3" );
		ids.add( "5" );
		
		// sort out even numbers (IdObject.id)
		final List<IdObject> idObjects = jp.getList( IdObject.class, (idObject)->{
			System.out.println( "called "+ idObject +" "+ idObject.id );
			try {
				return Long.valueOf( idObject.id ) % 2 == 1; 
			} catch(Exception e) {
				System.out.println( "fail "+ idObject.id +" "+ e );
				return false;
			}
		} );
		
		idObjects.forEach( (idObject)->ids.remove(idObject.id) );
		Assert.assertTrue( ids.isEmpty() );
	}

}
