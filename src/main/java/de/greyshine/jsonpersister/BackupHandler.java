package de.greyshine.jsonpersister;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.greyshine.jsonpersister.JsonPersister.Storage;
import de.greyshine.jsonpersister.util.Utils;

public class BackupHandler {
	
	private static final Logger LOG = LoggerFactory.getLogger( BackupHandler.class );
	
	private final JsonPersister jp;
	
	BackupHandler( JsonPersister jsonPersister ) {
		this.jp = jsonPersister;
	}
	
	public void writeBackup(OutputStream out) throws IOException {

		if ( out == null ) { throw new IllegalArgumentException( "OutputStream is null" ); }
		
		final Map<String,Integer> itemCounts = new HashMap<>();
		
		try {
		
			// https://stackoverflow.com/a/3103722
			final MessageDigest md = MessageDigest.getInstance("SHA-256");
			
			//final GZIPOutputStream gzipOut = new GZIPOutputStream( out );
			//final DataOutputStream dos = new DataOutputStream( gzipOut );
			final DataOutputStream dos = new DataOutputStream( out );
			
			dos.writeUTF("v:"+Storage.VERSION+"\n");
			
			Utils.travers( jp.getBaseDir(), (file)->{
				
				if ( file.isDirectory() ) { return; }
				
				LOG.info( "store: {} ...", file );
				
				final String itemCountKey = file.getParentFile().getParentFile().getName();
				itemCounts.putIfAbsent( itemCountKey, 0);
				itemCounts.put( itemCountKey, itemCounts.get( itemCountKey ).intValue()+1 );
				
				final String path = file.getAbsolutePath().substring( jp.getBaseDir().getAbsolutePath().length() );
				dos.writeUTF( "F:"+path );
				dos.writeLong( file.length() );
				
				final long copiedBytes = Utils.copy( new FileInputStream(file), dos, md );
				
				if ( file.length()!=copiedBytes ) {
					throw new IOException("copy error; unequal size");
				}
				LOG.debug( "bytes: {}", copiedBytes );
			} );
						
			final String checksum = String.format("%064x", new BigInteger(1, md.digest())).toLowerCase();
			LOG.debug( "checksum: {}", checksum );
			dos.writeUTF("$:"+checksum );
			
			//gzipOut.finish();
			dos.flush();
			
			itemCounts.forEach( (k,v)->LOG.debug( "item count '{}': {}", k, v ) );
			
		} catch(IOException e) {
			throw e;
		} catch(Exception e) {
			throw new IOException(e);
		} 
	}
	
	/**
	 * 
	 * @param in
	 * @param additive if backup will not delete all existing files but just replace existing files. Existing files not also being a backup file will still live on.
	 * @throws IOException
	 */
	public void readBackup(InputStream in, boolean additive) throws IOException {

		if ( in == null ) { return; }
		
		final Map<String,Integer> itemCounts = new HashMap<>();
		
		try {
			// https://stackoverflow.com/a/3103722
			final MessageDigest md = MessageDigest.getInstance("SHA-256");
			
			final DataInputStream dis = new DataInputStream( in );	
			
			String version = dis.readUTF().trim();
			if ( !version.startsWith( "v:" ) ) { throw new IOException("Expected version string 'v:<version>'"); }
			version = version.substring(2);
			LOG.debug( "reading version: {}", version );
			
			String checksum = null;
			String fileLine = null;
			while( in.available() > 0 ) {
				
				fileLine = dis.readUTF();
				LOG.debug( "line: {}", fileLine );
				
				if ( fileLine.startsWith( "F:/" ) ) {
					
					final String typeName = fileLine.substring(3, fileLine.indexOf('/', 3) );
					final Class<?> clazz = Class.forName( typeName );
					
					final long sizeToRead = dis.readLong();
					
					final File fileToSafe = new File( jp.getBaseDir(), fileLine.substring(3) );
					LOG.debug( "Saving file (exist={}): {}", fileToSafe.exists(), fileToSafe.getAbsolutePath() );
					
					if ( !fileToSafe.getParentFile().isDirectory() ) {
						fileToSafe.getParentFile().mkdirs();
					}
					
					final FileOutputStream fos = new FileOutputStream( fileToSafe );
					Utils.copy(in, fos, sizeToRead, md);
					Utils.closeSafe( fos );
					
					itemCounts.putIfAbsent( clazz.getTypeName(), 0);
					itemCounts.put( clazz.getTypeName(), itemCounts.get( clazz.getTypeName() )+1 );
					
				} else if ( fileLine.startsWith( "$:" ) ) {
					
					checksum = fileLine.substring(2);
					LOG.debug( "checksum: {}; line={}", checksum , fileLine );
					break;
				
				} else {
				
					LOG.error( "read illegal line: "+ fileLine );
					throw new IOException("read error; bad file.");
				}
			} 
			
			if ( Utils.isBlank( checksum ) ) {
				throw new IOException("Bad checksum line: "+ checksum);
			} else if ( !checksum.equalsIgnoreCase( String.format("%064x", new BigInteger(1, md.digest())) ) ) {
				throw new IOException("Bad checksum calculated="+ String.format("%064x", new BigInteger(1, md.digest())) +"; expected="+ checksum   );
			}
			
		} catch(IOException e) {
			throw e;
		} catch(Exception e) {	
			throw new IOException(e);
		} 
	}

}
