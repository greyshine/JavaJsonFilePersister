package de.greyshine.jsonpersister.util;

import java.io.File;

public class Assert {
	
	private Assert() {} 

	public static void notNull(String inMessage, Object inObject ) {
		if ( inObject == null ) { throw new NullPointerException( Utils.isNotBlank(inMessage) ? inMessage.trim() : "Given Object is null"); }
	}
	
	public static void isDirectory(File inFile) {
		if ( inFile == null || !inFile.isDirectory()) {
			throw new IllegalArgumentException("No directory: "+ Utils.getCanonicalPath( inFile ) );
		}		
	} 
	
}
