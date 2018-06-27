package de.greyshine.jsonpersister;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.greyshine.jsonpersister.annotations.Id;
import de.greyshine.jsonpersister.util.Assert;
import de.greyshine.jsonpersister.util.Utils;
import de.greyshine.jsonpersister.util.Utils.Wrapper;

public class JsonPersister {
	
	private final File baseDir;
	
	private final Storage storage = new Storage();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	
	public JsonPersister( File inPath ) {
		
		Assert.notNull("No base directory", inPath);
		
		if ( !inPath.exists() ) {
			inPath.mkdirs();
		}
		
		Assert.isDirectory(inPath);
		
		baseDir = Utils.getCanonicalFile( inPath );
	}
	
	public Storage getStorage() {
		return storage;
	}
	
	private File getDir(Class<?> inClass) {
		return new File( baseDir, inClass.getTypeName() );
	}
	
	private File getDir(Class<?> inClass, String inId) {
		
		if ( inClass == null ||  inId == null ) {
			return null;
		}
		
		final String pathExtension = Utils.getHash(inId, 5, "0123456789abcdefghijklmnopqrstuvwxyz");
		
		return new File( getDir(inClass), pathExtension );
	}
	
	private File getFile(Class<?> inClass, String inId) {
		
		if ( inClass == null ||  inId == null ) {
			return null;
		} 
		
		return new File(getDir( inClass, inId ), inId +".json" );
	}
	
	public boolean isExisting(Class<?> inClass, String inId) {
		
		if ( inClass == null ) { throw new IllegalArgumentException("No class specified."); }
		else if ( Utils.isBlank( inId ) ) { return false; }
		
		return getFile(inClass, inId).exists();
	}

	public String insert(Object inObject) throws IOException {
		return upsert( null, inObject );
	}
	
	public <T> T read(Class<T> inClass, String inId) throws IOException {
		
		if ( inClass == null || inId == null ) { return null; }
		
		final File file = getFile( inClass, inId );
		
		synchronized ( file.getCanonicalPath().intern() ) {
		
			if ( !file.isFile() ) { return null; }
			
			try (FileReader reader = new FileReader(file)) {
				return gson.fromJson( reader, inClass);	
			}	
		}
	}
	
	private <T> Field getIdField(T inObject) {
		
		final Utils.Wrapper<Field> field = new Utils.Wrapper<>(null);
		
		Stream.of( inObject.getClass().getDeclaredFields() )
			.filter( (f)->f.getDeclaredAnnotation( Id.class ) != null )
			.filter( (f)->{
				if ( f.getType() != String.class ) {
					throw new IllegalStateException("@Id field must be a String");
				}
				if ( Modifier.isStatic( f.getModifiers() ) ) {
					throw new IllegalStateException("@Id field must not be static");
				}
				if ( Modifier.isFinal( f.getModifiers() ) ) {
					throw new IllegalStateException("@Id field must not be final");
				}
				return true;
			} )
			.forEach( (f)->{
				if ( field.value != null ) {
					throw new IllegalStateException("Only one @Id field allowed");
				}
				field.value = f;
			} );
		
		return field.value;
	}
	
	private <T> String getAnnotatedId(T inObject) {
		
		final Field idField = getIdField(inObject);
		
		if ( idField == null ) {
			// no @Id found
			return null;
		} 
		
		String theId;
		try {
			theId = (String) idField.get( inObject );
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new IllegalStateException( "Unable to access @Id" , e );
		}
		
		return theId;
	}
	
	private <T> void setAnnotatedId(T inObject, String inId) {
		
		final Field idField = getIdField(inObject);
		if ( idField == null ) { return; }
		try {
			idField.set(inObject, inId);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new IllegalStateException( "Unable to access @Id" , e );
		}
	}
	
	public <T> String upsert(String inId, T inObject) throws IOException {
		
		if ( inObject == null ) { throw new IllegalArgumentException("No object specified."); }
		
		if ( Utils.isNotBlank( inId ) ) {
			setAnnotatedId( inObject, inId );
		} else {
			inId = getAnnotatedId( inObject );
		}
		
		inId = inId != null ? inId : UUID.randomUUID().toString();
		setAnnotatedId(inObject, inId);
		
		final File file = getFile(inObject.getClass(), inId);
		final String jsonString = gson.toJson( inObject );
		
		synchronized ( file.getCanonicalPath().intern() ) {
			Utils.writeFile( file, jsonString, Utils.CHARSET_UTF8);		
		}
		
		return inId;
	}
	
	public boolean delete(Class<?> inClass, String inId) throws IOException {
		
		if ( inClass == null || inId == null ) { return true; }
		
		final File file = getFile(inClass, inId);
		
		synchronized ( file.getCanonicalPath().intern() ) {
			file.delete();
		}
		
		return !file.exists();
	}

	public class Storage {
		
		public File getBaseDir() { return baseDir; }
		
		/**
		 * @param inType
		 * @param inHandler return value determines whether to keep on traversing
		 */
		public <T> void traversObjects( Class<T> inType, Consumer<File> inHandler ) {
			
			if ( inType == null || inHandler == null ) { return; }

			Stream.of( getDir( inType ).listFiles() )
				.filter( (f1)->f1.isDirectory() )
				.forEach( (f1)->{
					Stream.of( f1.listFiles() )
						.filter( (f2)->f2.isFile()&&f2.getName().toLowerCase().endsWith( ".json" ) )
						.forEach( (f2)->inHandler.accept(f2) );
				} );
		}
	}

	public <T> List<T> getList( Class<T> inClass, Function<T, Boolean> inFunction ) {
		
		if ( inClass == null ) { return null; }
		if ( inFunction == null ) { return new ArrayList<>(0); }
		
		final List<T> list = new ArrayList<>();
		
		list( inClass, (T)->{ 
			
			final Boolean r;
			
			try {
				r = inFunction.apply( T );
			} catch (Exception e) {
				throw Utils.toRuntimeException(e);
			}
			
			if ( Boolean.TRUE.equals( r ) ) {
				list.add( T );
			}
					
			return r;
		});
		
		return list;
	}
	
	public <T> void list( Class<T> inClass, Function<T, Boolean> inFunction ) {
		
		if ( inClass == null ) { return; }
		if ( inFunction == null ) { return; }
		
		final List<T> results = new ArrayList<>();
		final Wrapper<Boolean> quitFlag = new Wrapper<>(null);
		final Wrapper<Exception> exceptionWrapper = new Wrapper<>(null);
		
		this.storage.traversObjects(inClass, (file)->{
			
			if ( Boolean.TRUE.equals( quitFlag.value ) || exceptionWrapper.value != null ) { return; }
			
			final int indexFileEnding = file.getName().lastIndexOf(".json");
			if ( indexFileEnding < 1 ) { return; }
			final String id = file.getName().substring(0, indexFileEnding );
			
			try {
				
				final T object = read(inClass, id);
				final Boolean r = inFunction.apply( object );
				
				if ( r == null ) { 
					quitFlag.value = true;
					return;
				}
				
				if ( Boolean.TRUE.equals( r ) ) {
					results.add( object );
				}
				
				
			} catch (Exception e) {
				exceptionWrapper.value = e;
				return;
			}
			
		} );
		
		if ( exceptionWrapper.value != null ) {
			throw Utils.toRuntimeException(exceptionWrapper.value);
		}
	}
}
