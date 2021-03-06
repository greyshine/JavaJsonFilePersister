package de.greyshine.jsonpersister.util;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
	
	private static final Logger LOG = LoggerFactory.getLogger( Utils.class );

	public static final Class<?>[] EMPTY_CLASSES = new Class<?>[0];
	public static final Object[] EMPTY_OBJECTS = new Object[0];
	public static final String[] EMPTY_STRINGS = new String[0];
	public static final byte[] EMPTY_BYTES = new byte[0];
	public static final File[] EMPTY_FILES = new File[0];
	public static final InputStream EMPTY_INPUTSTREAM = new ByteArrayInputStream(new byte[0]);

	public static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");

	/**
	 * Carriage Return and LineFeed Remember: [\r]etur[\n] :-)
	 * https://stackoverflow.com/a/6539810/845117
	 */
	public static final String CRLF = "\r\n".intern();
	
	public static final int EOF = -1;
	
	public static final Comparator<File> FILE_COMPARATOR = new Comparator<File>() {

		@Override
		public int compare(File f1, File f2) {

			if (f1.isDirectory() && f2.isFile()) {

				return 1;

			} else if (f1.isFile() && f2.isDirectory()) {

				return -1;
			}

			return f1.getName().compareTo(f2.getName());
		}
	};

	private Utils() {
	};

	public static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	public static boolean isNotBlank(String s) {
		return s != null && !s.trim().isEmpty();
	}

	public static <T> T defaultIfNull(T inValue, T inDefault) {
		return inValue == null ? inDefault : inValue;
	}

	public static String defaultIfBlank(String inValue, String inDefault) {
		return inValue == null || inValue.trim().isEmpty() ? inDefault : inValue;
	}

	public static String nullIfBlank(String inValue, String inDefault) {
		return inValue == null || inValue.trim().isEmpty() ? null : inValue;
	}

	public static String emptyIfBlank(String inValue, String inDefault) {
		return inValue == null || inValue.trim().isEmpty() ? "" : inValue;
	}

	public static String getCanonicalPath(File inFile) {
		try {
			return inFile == null ? null : inFile.getCanonicalPath();
		} catch (IOException e) {
			return inFile.getAbsolutePath();
		}
	}

	public static File getCanonicalFile(File inFile) {
		try {
			return inFile == null ? null : inFile.getCanonicalFile();
		} catch (IOException e) {
			return inFile.getAbsoluteFile();
		}
	}

	public static boolean isFile(File inFile) {
		return inFile != null && inFile.isFile();
	}

	public static boolean isDir(File inFile) {
		return inFile != null && inFile.isDirectory();
	}

	public static boolean isFile(String inFilePath) {
		return isNotBlank(inFilePath) && isFile(new File(inFilePath));
	}

	/**
	 * 
	 * @param inFile
	 * @return <code>true</code> if the file does not exist, otherwise false
	 */
	public static boolean delete(File inFile) {

		if (inFile == null) {
			return true;
		} else if (isFile(inFile)) {
			inFile.delete();
			return !inFile.exists();

		}
		final List<File> theDirs = new ArrayList<>();
		theDirs.add(inFile);

		while (!theDirs.isEmpty()) {

			File theFile = theDirs.remove(theDirs.size() - 1);

			final List<File> theFiles = list(theFile);

			if (theFiles.isEmpty()) {

				theFile.delete();

			} else {

				theDirs.add(inFile);
				theFiles.stream().forEach((aFile) -> {

					if (isFile(aFile)) {

						aFile.delete();

					} else {
						theDirs.add(aFile);
					}
				});

			}
		}

		return !inFile.exists();
	}

	/**
	 * List files in the give directory. no further traversing into sub
	 * directories.
	 * 
	 * @param inFile
	 * @return
	 */
	public static List<File> list(File inFile) {
		return list(inFile, (f) -> {
			return true;
		});
	}

	public static List<File> list(File inFile, Predicate<File> inFilter) {

		if (!Utils.isDir(inFile)) {
			return new ArrayList<File>(0);
		}

		inFilter = defaultIfNull(inFilter, (x) -> {
			return true;
		});

		final List<File> theFiles = new ArrayList<>();

		for (final File aFile : defaultIfNull(inFile.listFiles(), EMPTY_FILES)) {

			if (!inFilter.test(aFile)) {
				continue;
			}

			theFiles.add(aFile);
		}

		Collections.sort(theFiles, FILE_COMPARATOR);

		return theFiles;
	}

	/**
	 * @param inTargetFile
	 * @return the target file if the parent dir exists, otherwise
	 *         <code>null</code>
	 */
	public static File mkParentDirs(String inTargetFile) {
		if (isBlank(inTargetFile)) {
			return null;
		}
		final File theFile = new File(inTargetFile);
		mkParentDirs(theFile);
		return isDir(theFile.getParentFile()) ? theFile : null;
	}

	public static boolean mkParentDirs(File inTarget) {

		if (inTarget == null) {

			return false;

		} else if (inTarget.exists()) {

			return true;
		}

		return inTarget.getParentFile().mkdirs();
	}

	/**
	 * Lists only files; no traversal.
	 * 
	 * @param inFile
	 * @return
	 */
	public static List<File> listFiles(File inFile) {
		return list(inFile, (f) -> {
			return isFile(inFile);
		});
	}

	public static int writeFile(File inFile, String inValue) throws IOException {

		return writeFile(inFile, inValue, CHARSET_UTF8);
	}

	public static int writeFile(File inFile, String inValue, Charset inCharset) throws IOException {

		mkParentDirs(inFile);

		inValue = inValue == null ? "" : inValue;
		inCharset = inCharset == null ? CHARSET_UTF8 : inCharset;

		final byte[] bytes = inValue.getBytes(inCharset);

		try (FileOutputStream fos = new FileOutputStream(inFile)) {

			fos.write(bytes);
			fos.flush();
		}

		return bytes.length;
	}

	public static String readFileToString(File inFile, Charset inCharset) throws IOException {
		return readToString(inFile, inCharset);
	}

	public static String readToString(File inFile, Charset inCharset) throws IOException {

		if (!isFile(inFile)) {
			return null;
		}

		try (FileInputStream fis = new FileInputStream(inFile)) {
			return readToString(fis, inCharset);
		}
	}

	public static String readToString(String inFile, Charset inCharset) throws IOException {

		if (!isFile(inFile)) {
			return null;
		}

		try (FileInputStream fis = new FileInputStream(inFile)) {
			return readToString(fis, inCharset);
		}
	}

	public static String readToString(InputStream inputStream, Charset inCharset) throws IOException {

		inCharset = defaultIfNull(inCharset, Charset.defaultCharset());

		final Reader r = new InputStreamReader(inputStream, inCharset);

		final StringBuilder theSb = new StringBuilder();

		while (r.ready()) {
			theSb.append((char) r.read());
		}

		return theSb.toString();
	}

	public static String getHash(String inValue, int inLen, String inAlphabet) {

		try {

			return inValue == null ? null : getHash(inValue.getBytes("UTF-8"), inLen, inAlphabet.toCharArray());

		} catch (final UnsupportedEncodingException e) {

			throw new RuntimeException(e);
		}
	}

	public static String getHash(byte[] inValues, int inLen, char[] inAlphabet) {

		if (inLen < 1 || inAlphabet.length == 0 || inValues == null || inValues.length < 1) {
			return "";
		}

		final char[] theChars = new char[inLen];
		int idxValues = 0;
		final int theAmtIters = Math.max(inValues.length, theChars.length);

		for (int i = 0; i < theAmtIters; i++, idxValues++) {

			idxValues = idxValues % inValues.length == 0 ? 0 : idxValues;

			final int idxHash = i % theChars.length;
			idxValues = idxValues == inValues.length ? 0 : idxValues;

			int idxAlphabet = inValues[idxValues] * (i + 1) * 13;

			idxAlphabet *= 1 + i + theChars[idxHash == 0 ? theChars.length - 1 : idxHash - 1];
			idxAlphabet = Math.abs(idxAlphabet) % inAlphabet.length;

			final char theChar = inAlphabet[idxAlphabet];

			theChars[idxHash] = theChar;
		}

		return new String(theChars);
	}

	public static class Wrapper<T> {
		public T value;

		public Wrapper() {
			this(null);
		}

		public Wrapper(T inValue) {
			this.value = inValue;
		}

		public boolean isNull() {
			return value == null;
		}

		public boolean isNotNull() {
			return value != null;
		}
	}

	public static RuntimeException toRuntimeException(Exception inException) {

		if (inException == null || inException instanceof RuntimeException) {
			return (RuntimeException) inException;
		}

		return new RuntimeException(inException);
	}

	@SuppressWarnings("unchecked")
	public static <T> T getFieldValue(Field field, Object object) {
		field.setAccessible(true);
		try {
			return (T)field.get(object);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> void setFieldValue(Field inField, Object inObject, Object inValue) {
		inField.setAccessible(true);
		try {
			inField.set(inObject, inValue);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static void travers(File file, FileHandler traverser) throws Exception {

		if (file == null || traverser == null) {
			return;
		}
		if (file.isFile()) {
			traverser.handle(file);
		}

		final List<File> dirs = new ArrayList<>();
		dirs.add(file);

		final Wrapper<Exception> we = new Wrapper<Exception>();

		while (we.isNull() && !dirs.isEmpty()) {

			final File dir = dirs.remove(0);
			try {
				traverser.handle(dir);
			} catch (Exception e) {
				we.value = e;
				continue;
			}

			Stream.of(dir.listFiles()).filter((f) -> we.value == null).forEach((f) -> {
				if (f.isDirectory()) {
					dirs.add(0, f);
				} else if (f.isFile()) {
					try {
						traverser.handle(f);
					} catch (Exception e) {
						we.value = e;
					}
				}
			});
		}
	}

	@FunctionalInterface
	public interface FileHandler {
		void handle(File file) throws Exception;
	}

	public static long copy(InputStream in, OutputStream out) throws IOException {
		return copy(in, out, null);
	}
	
	
	public static long copy(InputStream in, OutputStream out, long maxBytes, MessageDigest md) throws IOException {
		
		if (in == null || out == null) {
			return -1;
		}
		
		if ( maxBytes < 1 ) { return 0; }
		
		long count = 0;
		
		int r;
		
		while( maxBytes > 0 && EOF < (r=in.read()) ) {
			
			if ( md != null ) {
        		md.update( (byte)r );
        	}
			
			maxBytes--;
			out.write( r );
			count++;
		}
		
		return count;
	}
		
	public static long copy(InputStream in, OutputStream out, MessageDigest md) throws IOException {

		if (in == null || out == null) {
			return -1;
		}
		
		final byte[] buffer = new byte[1024*2];
		long count = 0;
        int n;
        while ( EOF != (n = in.read(buffer)) ) {
            
        	if ( md != null ) {
        		md.update(buffer, 0, n);
        	}
        	
        	out.write(buffer, 0, n);
            count += n;
        }
        return count;

	}

	public static void closeSafe(Closeable c) {
		try { ((Flushable)c).flush(); }  catch(Exception e) { /*intended ignore*/ }
		try { c.close(); }  catch(Exception e) { /*intended ignore*/ }
	}
	
	public static void notify(Object syncronizedObject) {
		notify(syncronizedObject, null);
	}
	
	public static void notify(Object syncronizedObject, Runnable doBeforeNotify) {
	
		if ( syncronizedObject == null ) { return; }
		
		synchronized (syncronizedObject) {
			if ( doBeforeNotify != null ) { doBeforeNotify.run(); }
			syncronizedObject.notifyAll();
		}
	}

	public static void wait( Object syncronizedObject, BooleanSupplier supplierTrueWaits) {
		wait( syncronizedObject, supplierTrueWaits, null );
	}
	
	public static void wait( Object syncronizedObject, BooleanSupplier supplierTrueWaits, Runnable doAfterWaitOnFocus ) {
		
		if ( syncronizedObject == null ) { throw new IllegalArgumentException("object for synchronisation must not be null"); }
		if ( supplierTrueWaits == null ) { throw new IllegalArgumentException("supplier for waiting must not be null"); }
		
		synchronized ( syncronizedObject ) {
			
			while( supplierTrueWaits.getAsBoolean() ) {
				try {
					syncronizedObject.wait( 500 );
				} catch (InterruptedException e) {
					LOG.warn("waiting was interrupted: {}", e);
				}
			}
			
			if ( doAfterWaitOnFocus != null ) {
				doAfterWaitOnFocus.run();
			}
		}
	}

}
