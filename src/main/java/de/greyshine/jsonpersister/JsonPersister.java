package de.greyshine.jsonpersister;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.greyshine.jsonpersister.annotations.Id;
import de.greyshine.jsonpersister.util.Assert;
import de.greyshine.jsonpersister.util.Utils;
import de.greyshine.jsonpersister.util.Utils.Wrapper;

public class JsonPersister {

	private static final Logger LOG = LoggerFactory.getLogger(JsonPersister.class);

	private final File baseDir;

	private final BackupHandler backupHandler = new BackupHandler(this);
	
	private AtomicBoolean block =  new AtomicBoolean(false);
	private AtomicInteger concurrentAccesses = new AtomicInteger(0);
	
	private final Storage storage = new Storage();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	private IIdProvider idProvider = new IIdProvider() {
		@Override
		public String getId(Object inObject) {
			return UUID.randomUUID().toString();
		}
	};

	private final Map<Class<?>, Field> idFields = new HashMap<>(0);

	public JsonPersister(File path) {

		Assert.notNull("No base directory", path);

		if (!path.exists()) {
			path.mkdirs();
		}

		Assert.isDirectory(path);

		baseDir = Utils.getCanonicalFile(path);

		LOG.info("storage: {}", baseDir.getAbsolutePath());
	}
	
	public File getBaseDir() {
		return baseDir;
	}

	public void setIdProvider(IIdProvider idProvider) {
		if (idProvider == null) {
			throw new IllegalArgumentException(IIdProvider.class.getTypeName() + " must not be null");
		}
		this.idProvider = idProvider;
	}

	/**
	 * Reads an Object from the file System
	 * 
	 * @param inClass
	 * @param inId
	 * @return
	 * @throws IOException
	 */
	public <T> T read(Class<T> clazz, String id) throws IOException {

		if (clazz == null || id == null) {
			return null;
		}

		final File file = getFile(clazz, id);

		concurrentAccesses.incrementAndGet();
		
		try {
			
			synchronized (file.getCanonicalPath().intern()) {

				if (file == null || !file.isFile()) {
					return null;
				}

				try (FileReader reader = new FileReader(file)) {

					final T result = gson.fromJson(reader, clazz);

					LOG.info("read [id={}]:\n{}", id, result);

					return result;
				}
			}
			
		} finally {
			concurrentAccesses.decrementAndGet();
		}
	}

	public <T> String upsert(T object) throws IOException {

		if (object == null) {
			throw new IllegalArgumentException("No object specified.");
		}

		final Field idField = getIdField(object.getClass());

		String id = Utils.getFieldValue(idField, object);

		if (Utils.isBlank(id)) {

			id = idProvider.getId(object);

			if (id == null) {
				throw new IllegalStateException("Id for object is null [object=" + object + "]");
			}

			Utils.setFieldValue(idField, object, id);
		}

		final File file = getFile(object.getClass(), id);
		final String jsonString = gson.toJson(object);

		LOG.info("upsert [object={}]:\n{}", object, jsonString);
		
		Utils.wait( block, ()->block.get(), ()->concurrentAccesses.incrementAndGet());
		
		try {
		
			synchronized (file.getCanonicalPath().intern()) {
				Utils.writeFile(file, jsonString, Utils.CHARSET_UTF8);
			}
			
		} finally {
			
			concurrentAccesses.decrementAndGet();
			Utils.notify(block);
		}

		return id;
	}

	public boolean delete(Object object) throws IOException {

		if (object == null) {
			return false;
		}

		final Field idField = getIdField(object.getClass());

		final String id = Utils.getFieldValue(idField, object);

		if (id == null) {
			return false;
		}

		return delete(object.getClass(), id);
	}

	public boolean delete(Class<?> inClass, String inId) throws IOException {

		if (inClass == null || inId == null) {
			return true;
		}

		final File file = getFile(inClass, inId);

		if (!file.exists()) {
			return false;
		}
		
		Utils.wait( block, ()->block.get(), ()->concurrentAccesses.incrementAndGet());
		
		try {
			
			synchronized (file.getCanonicalPath().intern()) {
				file.delete();
			}			
			
		} finally {
			concurrentAccesses.decrementAndGet();
			Utils.notify( block );
		}

		return !file.exists();
	}

	public Storage getStorage() {
		return storage;
	}

	private File getDir(Class<?> inClass) {
		return new File(baseDir, inClass.getTypeName());
	}

	private File getDir(Class<?> inClass, String id) {

		if (inClass == null || Utils.isBlank(id)) {
			return null;
		}

		final String pathExtension = Utils.getHash(id.trim(), 5, "0123456789abcdefghijklmnopqrstuvwxyz");

		return new File(getDir(inClass), pathExtension);
	}

	private File getFile(Class<?> clazz, String id) {

		if (clazz == null || id == null) {
			return null;
		}
		
		return new File(getDir(clazz, id), id + ".json");
	}

	public boolean isExisting(Class<?> clazz, String id) {

		if (clazz == null) {
			throw new IllegalArgumentException("No class specified.");
		} else if (Utils.isBlank(id)) {
			return false;
		}

		return getFile(clazz, id).exists();
	}

	private <T> Field getIdField(Class<?> clazz) {

		final Field idField = idFields.get(clazz);

		if (idField != null) {
			return idField;
		}

		final Utils.Wrapper<Field> fieldWrapper = new Utils.Wrapper<>(null);

		Stream.of(clazz.getDeclaredFields()).filter((f) -> f.getDeclaredAnnotation(Id.class) != null).filter((f) -> {
			if (f.getType() != String.class) {
				throw new IllegalArgumentException("@Id field must be a String");
			}
			if (Modifier.isStatic(f.getModifiers())) {
				throw new IllegalArgumentException("@Id field must not be static");
			}
			if (Modifier.isFinal(f.getModifiers())) {
				throw new IllegalArgumentException("@Id field must not be final");
			}
			return true;
		}).forEach((f) -> {
			if (fieldWrapper.value != null) {
				throw new IllegalArgumentException("Only one @Id field allowed");
			}
			fieldWrapper.value = f;
		});

		if (fieldWrapper.isNull()) {
			throw new IllegalArgumentException(clazz.getTypeName() + " does not declare an @Id-String field");
		}

		idFields.put(clazz, fieldWrapper.value);
		return fieldWrapper.value;
	}

	public <T> List<T> getList(Class<T> clazz, Function<T, Boolean> addDecision) {

		if (clazz == null) {
			return null;
		}
		
		final List<T> list = new ArrayList<>();

		list(clazz, (T) -> {

			final Boolean r;

			try {
				r = addDecision == null || addDecision.apply(T);
			} catch (Exception e) {
				throw Utils.toRuntimeException(e);
			}

			if (Boolean.TRUE.equals(r)) {
				list.add(T);
			}

			return r;
		});

		return list;
	}

	public <T> void list(Class<T> clazz, Function<T, Boolean> addItemDecision) {

		if (clazz == null) {
			return;
		}
		
		final List<T> results = new ArrayList<>();
		final Wrapper<Boolean> quitFlag = new Wrapper<>(null);
		final Wrapper<Exception> exceptionWrapper = new Wrapper<>(null);
		
		this.storage.traversObjects(clazz, (file) -> {

			if (Boolean.TRUE.equals(quitFlag.value) || exceptionWrapper.value != null) {
				return;
			}

			final int indexFileEnding = file.getName().lastIndexOf(".json");
			if (indexFileEnding < 1) {
				return;
			}
			final String id = file.getName().substring(0, indexFileEnding);

			try {

				final T object = read(clazz, id);
				
				Boolean r = true;
				
				if ( addItemDecision != null ) {
					
					r = addItemDecision.apply(object); 
					
					if (r == null) {
						quitFlag.value = true;
						return;
					}
				}
				
				if (Boolean.TRUE.equals(r)) {
					results.add(object);
				}

			} catch (Exception e) {
				exceptionWrapper.value = e;
				return;
			}

		});

		if (exceptionWrapper.value != null) {
			throw Utils.toRuntimeException(exceptionWrapper.value);
		}
	}

	private interface IIdProvider {
		String getId(Object inObject);
	}

	public void writeBackup(OutputStream out) throws IOException {
		
		Utils.wait( block, ()->block.get(), ()->block.set(true) );
		backupHandler.writeBackup(out);
		Utils.notify( block, ()->block.set(false) );
	}
	
	public void readBackup(InputStream in, boolean additive) throws IOException {
		
		Utils.wait( block, ()->block.get(), ()->block.set(true) );
		backupHandler.readBackup(in, additive);
		Utils.notify( block, ()->block.set(false) );
	}

	public class Storage {
		
		public static final String VERSION = "1.0";
		
		public File getBaseDir() {
			return baseDir;
		}

		/**
		 * @param inType
		 * @param inHandler
		 *            return value determines whether to keep on traversing
		 */
		public <T> void traversObjects(Class<T> clazz, Consumer<File> fileConsumer) {

			if (clazz == null || fileConsumer == null) {
				return;
			}

			Stream.of( getDir(clazz).listFiles() )
				.filter(  (f1) -> f1.isDirectory())
				.forEach( (f1) -> {
									Stream.of( f1.listFiles())
										.filter((f2) -> f2.isFile() && f2.getName().toLowerCase().endsWith(".json"))
										.forEach((f2) -> fileConsumer.accept(f2));
								  }
				);
		}
	}

	
}
