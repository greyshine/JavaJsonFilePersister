package de.greyshine.jsonpersister;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import de.greyshine.jsonpersister.objects.IdObject;
import de.greyshine.jsonpersister.objects.SimpleObject;
import de.greyshine.jsonpersister.util.Utils;

public class BackupTests {
	
private static final File STORAGE = new File( "target/test/storage/"+ BackupTests.class.getSimpleName() );
	
	final JsonPersister jp = new JsonPersister( STORAGE );
	
	@BeforeClass
	public static void beforeClass() {
		
		if ( STORAGE.exists() ) {
			Utils.delete( STORAGE );
		} 
		
		Assert.assertFalse( STORAGE.exists() );
		Assert.assertFalse( STORAGE.isDirectory() );
		
		STORAGE.mkdirs();
		
		Assert.assertTrue( STORAGE.isDirectory() );
	}
	
	@Test
	public void test() throws IOException {
		
		prepare();
		
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		jp.writeBackup( baos );
		baos.close();
		
		System.out.println( "data:\n"+ baos.toString( "UTF-8" ) );
		
		// delete 
		beforeClass();
		
		final ByteArrayInputStream bais = new ByteArrayInputStream( baos.toByteArray() );
		jp.readBackup(bais, false);
		bais.close();
	}

	private List<Object> prepare() throws IOException {
		
		final List<Object> objects = new ArrayList<>();
		
		final IdObject o1 = new IdObject("1");
		o1.time = 1;
		jp.upsert( o1 );
		objects.add( o1 );
		
		final SimpleObject o2 = new SimpleObject();
		o2.id = "1";
		o2.text = "text1";
		o2.text2 = "text2";
		jp.upsert( o2 );
		objects.add( o2 );
		
		return objects;
		
	}

}
