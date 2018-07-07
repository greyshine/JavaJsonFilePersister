package de.greyshine.jsonpersister.exceptions;

public class BadVersionException extends RuntimeException {

	private static final long serialVersionUID = 3114333617700987233L;

	public final Object object;
	public final long expectedVersion;
	
	public BadVersionException(Object object, long expectedVersion) {
		this.object = object;
		this.expectedVersion = expectedVersion;
	}
	
}
