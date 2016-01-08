package aQute.open.store.api;

/**
 * A Store of objects. A store is a typed collection of objects. Objects must be
 * DTOs. The DTO must have one magic field '_id' which is of type byte[]. This field is set by the store
 * and is some kind of unique identifier.
 *
 * @param <T>
 *            The store type
 */
public interface Store<T> {

	/**
	 * Insert a new object in the collection. If the object's already in the
	 * collection, this returns null. Otherwise it will return the object with
	 * the _id set.
	 * 
	 * @param t
	 *            the target object
	 * @return the updated object. If this returns null, the object could not be
	 *         inserted.
	 */
	T insert(T t) throws Exception;


	/**
	 * Update a document for the given fields.
	 * 
	 * @param document the target object
	 * @param fields the fields that need to be updated
	 */
	void update(T document, String... fields) throws Exception;

	/**
	 * Either insert or update depending on the fact if this object is already inserted.
	 * 
	 * @param document the target object
	 */
	void upsert(T document) throws Exception;

	/**
	 * A cursor for all the objects in the collection.
	 */
	Cursor<T> all() throws Exception;

	/**
	 * See {@link Cursor#where(String, Object...)}
	 */
	Cursor<T> find(String where, Object... args) throws Exception;

	/**
	 * Create a cursor on the target object's _id.
	 * 
	 */
	Cursor<T> find(T target) throws Exception;

	/**
	 * Create a cursor which will only return the given fields.
	 * 
	 * @param keys field names
	 * @return
	 */
	Cursor<T> select(String... keys);

	/**
	 * Create a new unique id for the store.
	 * @return the unique id.
	 */
	byte[] uniqueId();

	/**
	 * Use optimistic locking
	 * @param p
	 * @return
	 * @throws Exception
	 */
	Cursor<T> optimistic(T p) throws Exception;

	/**
	 * Drop this collection, databases normally do not allow this though and
	 * throw a {@link SecurityException}
	 * 
	 * @throws SecurityException
	 *             when not allowed
	 */
	void drop() throws SecurityException;

	/**
	 * The size of the full collection
	 * 
	 * @return
	 */
	long count();
}
