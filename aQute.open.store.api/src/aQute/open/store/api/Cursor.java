package aQute.open.store.api;

import java.util.*;
import java.util.stream.Stream;

/**
 * A cursor on the store. This cursor is used to create a selection and can then
 * be used as iterator. A cursor is a one time type like an iterator. This
 * cursor uses a builder interface to construct the selection.
 *
 * @param <T>
 *            The collection's type
 */
public interface Cursor<T> extends Iterable<T> {

	/**
	 * A Visitor on to the collection for the current selection.
	 * 
	 * @param <T>
	 */
	interface Visitor<T> {
		boolean visit(T t) throws Exception;
	}

	/**
	 * Select the given top level fields in the return object.
	 * 
	 * @param key
	 *            the field names
	 * @return A new cursor
	 */
	Cursor<T> select(String... keys) throws Exception;

	/**
	 * Restrict the selection with an OSGi filter selection. The filter
	 * expression is first formatted so you can specify arguments. The filter is
	 * a superset of OSGi filters. It supports the all the variations of the
	 * less and greater operations and the ~= operator is implemented as a
	 * regular expression. Enclosing the ldap expression in parentheses is optional.
	 * 
	 * @param ldap
	 *            The filter expression, uses String.format with the given args
	 * @param args
	 *            Any arguments
	 * @return A new cursor
	 */
	Cursor<T> where(String ldap, Object... args) throws Exception;

	/**
	 * Add an or expression selecting the given object's id.
	 * 
	 * @param t
	 *            the target object
	 * @return A new cursor
	 */
	Cursor<T> or(T t) throws Exception;

	/**
	 * The slice function controls the number of items of an array that a query
	 * returns. For example {@code slice( "array", 4 )} will return the first 4
	 * elements. {@code slice( "array", -4 )} returns the last 4 elements.
	 * 
	 * @param key
	 *            the field name
	 * @param count
	 *            the number of elements. Positive is first elements, negative
	 *            is last elements.
	 * @return A new cursor
	 */
	Cursor<T> slice(String key, int count) throws Exception;

	/**
	 * Limit the number of returned objects.
	 * 
	 * @param limit
	 *            nr of returned objects
	 * @return A new cursor
	 */
	Cursor<T> limit(int limit) throws Exception;

	/**
	 * Skip the first set of elements.
	 * 
	 * @param skip
	 *            nr of elements to skip
	 * @return A new cursor
	 */
	Cursor<T> skip(int skip) throws Exception;

	/**
	 * Sort ascending on given field
	 * 
	 * @param key
	 *            field name
	 * @return A new cursor
	 */
	Cursor<T> ascending(String key) throws Exception;

	/**
	 * Sort descending on given field.
	 * 
	 * @param key
	 *            name
	 * @return A new cursor
	 */
	Cursor<T> descending(String key) throws Exception;

	/**
	 * Check if field is in the given values
	 * 
	 * @param field
	 *            field name
	 * @param values
	 *            values
	 * @return A new cursor
	 */
	Cursor<T> in(String field, Object... values) throws Exception;

	/**
	 * Check if field is in the given values
	 * 
	 * @param field
	 *            field name
	 * @param values
	 *            values
	 * @return A new cursor
	 */
	Cursor<T> in(String field, Collection<?> values) throws Exception;

	/**
	 * Check of the given field is equal to the given value
	 * 
	 * @param field
	 *            field name
	 * @param values
	 *            values
	 * @return A new cursor
	 */
	Cursor<T> eq(String field, Object value);

	/**
	 * Check of the given field is greater than the given value
	 * 
	 * @param field
	 *            field name
	 * @param values
	 *            values
	 * @return A new cursor
	 */
	Cursor<T> gt(String field, Object value);;

	/**
	 * Check of the given field is less than the given value
	 * 
	 * @param field
	 *            field name
	 * @param values
	 *            values
	 * @return A new cursor
	 */
	Cursor<T> lt(String field, Object value);

	/**
	 * Check of the given field is greater or equal to the given value
	 * 
	 * @param field
	 *            field name
	 * @param values
	 *            values
	 * @return A new cursor
	 */
	Cursor<T> gte(String field, Object value);

	/**
	 * Check of the given field is less than or equal to the given value
	 * 
	 * @param field
	 *            field name
	 * @param values
	 *            values
	 * @return A new cursor
	 */
	Cursor<T> lte(String field, Object value);

	/**
	 * A pull removes from an existing array all instances of a value
	 * 
	 * @param field
	 *            field name
	 * @param values
	 *            values
	 * @return A new cursor
	 */
	Cursor<T> pull(String field, String value) throws Exception;

	/**
	 * Search for the words in given text.
	 * 
	 * @param text
	 *            a set of white space separated words
	 * @return A new cursor
	 */
	Cursor<T> text(String text) throws Exception;

	/**
	 * Create a query based on templates. A template has a name and should get a
	 * value in the query by appending it with a ':' and the value. Like:
	 * 
	 * <pre>
	 *               foo:bar
	 *               !foo:bar
	 * </pre>
	 * 
	 * The name of the template is then looked up in the map and used to create
	 * a query. The template is a String.format string with 1 parameter, the
	 * word given. A template reference can be prefixed with a ! sign for
	 * negation.
	 * <p>
	 * Any values in the query that are not a template reference are treated as
	 * a keyword search. They can be prefixed with a '-' to make sure the
	 * keyword does not appear in the object.
	 * 
	 * @param query
	 * @param templates
	 * @return
	 * @throws Exception
	 */
	Cursor<T> query(String query, Map<String, String> templates) throws Exception;

	/**************** UPDATE *******************/

	/**
	 * Add a keyword tot he update.
	 * 
	 * @param word
	 * @return
	 * @throws Exception
	 */
	Cursor<T> word(String word) throws Exception;

	/**
	 * Set the given field in all selected objects to the given value.
	 * <p>
	 * Requires update.
	 * 
	 * @param field
	 *            field name
	 * @param value
	 *            value to set
	 * @return A new cursor
	 */
	Cursor<T> set(String field, Object value) throws Exception;

	/**
	 * Can be used after {@link Store#find(Object)}. The value set in the
	 * database will match the value of the field.
	 * <p>
	 * Requires update.
	 * 
	 * @param field
	 *            field name
	 * @return A new cursor
	 */
	Cursor<T> set(String field) throws Exception;

	/**
	 * Unset a field in all objects of the selection.
	 * <p>
	 * Requires update.
	 * 
	 * @param field
	 *            field name
	 * @return A new cursor
	 */
	Cursor<T> unset(String field) throws Exception;

	/**
	 * Append a number of values to an array
	 * <p>
	 * Requires update.
	 * 
	 * @param field
	 *            the array name
	 * @param value
	 *            the values
	 * @return A new cursor
	 */
	Cursor<T> append(String field, Object... value) throws Exception;

	/**
	 * Remove a number of values from an array
	 * <p>
	 * Requires update.
	 * 
	 * @param field
	 *            the array name
	 * @param value
	 *            the values
	 * @return A new cursor
	 */
	Cursor<T> remove(String field, Object... value) throws Exception;

	/**
	 * Increment the field with the given value.
	 * <p>
	 * Requires update.
	 * 
	 * @param field
	 *            field name to increment
	 * @param value
	 *            the amount to use for increment, can be negative
	 * @return A new cursor
	 */
	Cursor<T> inc(String field, Object value) throws Exception;

	/**
	 * If the collection is empty.
	 * 
	 * @return true if empty, otherwise false.
	 */
	boolean isEmpty() throws Exception;

	/**
	 * Update the collection.
	 * 
	 * @return
	 * @throws Exception
	 */
	int update() throws Exception;

	/**
	 * Return the first element in the collection that matches the current
	 * selection.
	 * 
	 * @return the first object in the selected collection or an empty Optional
	 *         if collection is empty
	 */
	Optional<T> first() throws Exception;

	/**
	 * Return an iterator on this collection.
	 */
	Iterator<T> iterator();

	/**
	 * Visit the objects in the collection
	 * 
	 * @param visitor
	 * @return
	 * @throws Exception
	 */
	boolean visit(Visitor<T> visitor) throws Exception;

	/**
	 * Answer the distinct values for a given field.
	 * 
	 * @param field
	 * @return A list of values of field
	 */

	List<?> distinct(String field) throws Exception;

	/**
	 * Remove the selection.
	 * 
	 */
	int remove() throws Exception;

	/**
	 * Return the number of elements in the collection
	 * 
	 */
	int count() throws Exception;

	/**
	 * Return an object from the collection.
	 * 
	 */
	Optional<T> one() throws Exception;

	/**
	 * Collect all selected objects
	 * 
	 * @return
	 */
	List<T> collect();

	/**
	 * Convert to a stream
	 * 
	 * @return
	 */
	Stream<T> stream();
}
