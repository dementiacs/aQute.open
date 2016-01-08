package aQute.open.store.api;

/**
 * An object store data base with multiple collections. The collections are
 * typed by a class. The API is independent of Mongodb but very much modeled
 * after this store.
 */
public interface DB {
	/**
	 * Get a store object
	 * 
	 * @param clazz the type associated with the store
	 * @param name the name of the store.
	 * @return A Store object
	 */
	<T> Store<T> getStore(Class<T> clazz, String name) throws Exception;

	/**
	 * Drop the current db. This method drops the database. It is likely not
	 * functional in most cases, only for testing databases.
	 */
	void drop() throws SecurityException;
}
