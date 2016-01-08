package aQute.impl.store.mongo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

import aQute.lib.converter.Converter;
import aQute.open.store.api.Cursor;

public class MongoCursorImpl<T> implements Iterable<T>, Cursor<T> {
	static Pattern QUERY = Pattern.compile("(\\w+):([^()=><]+)");

	enum Ops {
		INC, SET, UNSET, ADD, REMOVE, APPEND;
	}

	static DBObject			EMPTY		= new BasicDBObject();
	static Converter		converter	= new Converter();
	final MongoStoreImpl<T>	store;
	List<T>					objects;
	DBObject				where;
	DBObject				select;
	DBObject				sort;
	DBObject				update;
	int						skip;
	int						limit;
	T						target;

	public MongoCursorImpl(MongoStoreImpl<T> store) {
		this.store = store;
	}

	public MongoCursorImpl(MongoStoreImpl<T> store, T target) throws Exception {
		this(store);
		or(target);
	}

	public MongoCursorImpl<T> where(String ldap, Object... args) throws Exception {
		if (ldap == null)
			return this;

		combine("$and", store.filter(ldap, args));
		return this;
	}

	public MongoCursorImpl<T> or(T t) throws Exception {
		if (objects == null)
			objects = new ArrayList<T>();
		objects.add(t);

		combine("$or", store.filter(t));
		return this;
	}

	public MongoCursorImpl<T> select(String... keys) {
		if (select == null)
			select = new BasicDBObject();
		for (String key : keys)
			select.put(key, 1);
		return this;
	}

	public MongoCursorImpl<T> slice(String key, int count) {
		if (select == null)
			select = new BasicDBObject();
		select.put(key, new BasicDBObject("$slice", count));
		return this;
	}

	public MongoCursorImpl<T> limit(int limit) {
		this.limit = limit;
		return this;
	}

	public MongoCursorImpl<T> skip(int skip) {
		this.skip = skip;
		return this;
	}

	public MongoCursorImpl<T> ascending(String field) {
		return sort(field, 1);
	}

	public MongoCursorImpl<T> descending(String field) {
		return sort(field, -1);
	}

	public Optional<T> first() {
		limit = 1;
		Iterator<T> iterator = iterator();
		if (iterator.hasNext())
			return Optional.of(iterator.next());
		else
			return Optional.empty();
	}

	public Iterator<T> iterator() {
		final DBCursor cursor = getDBCursor();

		return new Iterator<T>() {

			public boolean hasNext() {
				return cursor.hasNext();
			}

			@SuppressWarnings("unchecked")
			public T next() {
				DBObject object = cursor.next();
				try {
					return (T) store.mcnv.fromMongo(store.type, object);
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}

			public void remove() {
				cursor.remove();
			}
		};
	}


	/**
	 * Answer the distinct values for a given field.
	 * 
	 * @param field
	 * @return
	 * @throws Exception
	 */

	public List<?> distinct(String field) throws Exception {
		assert skip == 0;
		assert limit == 0;
		assert select == null;

		Class<?> to = store.type.getField(field).getType();
		List<?> list;

		// Do we have a sub selection? Then use the filter
		// otherwise use the call without where clause
		if (where == null)
			list = store.collection.distinct(field);
		else
			list = store.collection.distinct(field, where);

		List<Object> result = new ArrayList<Object>(list.size());
		for (Object o : list) {
			result.add(converter.convert(to, o));
		}
		return result;
	}

	private DBCursor getDBCursor() {
		final DBCursor cursor = store.collection.find(where, select);
		if (limit != 0)
			cursor.limit(limit);
		else
			cursor.limit(100);

		if (skip != 0)
			cursor.skip(skip);
		if (sort != null) {
			cursor.sort(sort);
		}
		// System.out.println(where);
		return cursor;
	}

	public int remove() {
		WriteResult result = where == null ? store.collection.remove(new BasicDBObject())
				: store.collection.remove(where);
		store.error(result);
		return result.getN();
	}

	private MongoCursorImpl<T> sort(String field, int i) {
		if (sort == null)
			sort = new BasicDBObject();
		sort.put(field, i);
		return this;
	}

	public int count() {
		DBCursor cursor = getDBCursor();
		return cursor.count();
	}

	public Optional<T> one() {
		limit = 1;
		Iterator<T> one = iterator();
		if (one.hasNext())
			return Optional.of(one.next());
		else
			return Optional.empty();
	}

	void combine(String type, DBObject filter) {
		if (where == null) {
			where = filter;
			return;
		}
		where = new BasicDBObject(type, Arrays.asList(where, filter));
	}

	public MongoCursorImpl<T> set(String field, Object value) throws Exception {
		combineUpdate(field, "$set", store.mcnv.toMongo(value));
		return this;
	}

	public MongoCursorImpl<T> unset(String field) throws Exception {
		combineUpdate(field, "$unset", null);
		return this;
	}

	public MongoCursorImpl<T> append(String field, Object... value) throws Exception {
		combineUpdate(field, "$pushAll", store.mcnv.toMongo(value));
		return this;
	}

	@Override
	public Cursor<T> pull(String field, String value) throws Exception {
		combineUpdate(field, "$pull", store.mcnv.toMongo(value));
		return this;
	}

	public MongoCursorImpl<T> remove(String field, Object... value) throws Exception {
		combineUpdate(field, "$pullAll", store.mcnv.toMongo(value));
		return this;
	}

	public MongoCursorImpl<T> inc(String field, Object value) throws Exception {
		combineUpdate(field, "$inc", store.mcnv.toMongo(value));
		return this;
	}

	public boolean isEmpty() {
		return count() == 0;
	}

	private void combineUpdate(String field, String op, Object value) throws Exception {
		if (update == null)
			update = new BasicDBObject();

		DBObject o = (DBObject) update.get(op);
		if (o == null)
			update.put(op, o = new BasicDBObject());

		assert store.checkField(field, value);
		if (value instanceof Enum)
			value = value.toString();
		o.put(field, value);
	}

	public int update() {
		WriteResult result = store.collection.update(where == null ? EMPTY : where, update, false, true);
		store.error(result);
		return result.getN();
	}

	public MongoCursorImpl<T> in(String field, Object... values) throws Exception {
		return in(field, Arrays.asList(values));
	}

	public MongoCursorImpl<T> in(String field, Collection<?> values) throws Exception {
		if (where == null)
			where = new BasicDBObject();

		BasicDBObject in = new BasicDBObject();
		List<Object> list = new ArrayList<Object>();
		Field f = store.type.getField(field);
		for (Object value : values) {

			// TODO need to consider collection fields ...

			list.add(converter.convert(f.getGenericType(), value));
		}
		where.put(field, in);
		return this;
	}

	@Override
	public Cursor<T> set(String field) throws Exception {
		if (target == null)
			throw new IllegalArgumentException("No target set. Use find(T t) to set target");

		try {
			Field f = target.getClass().getField(field);
			if (f == null)
				throw new IllegalArgumentException("No such field " + field);

			return set(field, f.get(target));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Cursor<T> eq(String key, Object value) {
		if (where == null)
			where = new BasicDBObject();
		where.put(key, value);
		return this;
	}

	@Override
	public Cursor<T> gt(String key, Object value) {
		if (where == null)
			where = new BasicDBObject();

		BasicDBObject vpart = new BasicDBObject();
		vpart.put("$gt", value);
		where.put(key, vpart);
		return this;
	}

	@Override
	public MongoCursorImpl<T> lt(String key, Object value) {
		if (where == null)
			where = new BasicDBObject();

		BasicDBObject vpart = new BasicDBObject();
		vpart.put("$lt", value);
		where.put(key, vpart);
		return this;
	}

	@Override
	public MongoCursorImpl<T> gte(String key, Object value) {
		if (where == null)
			where = new BasicDBObject();

		BasicDBObject vpart = new BasicDBObject();
		vpart.put("$gte", value);
		where.put(key, vpart);
		return this;
	}

	@Override
	public MongoCursorImpl<T> lte(String key, Object value) {
		if (where == null)
			where = new BasicDBObject();

		BasicDBObject vpart = new BasicDBObject();
		vpart.put("$lte", value);
		where.put(key, vpart);
		return this;
	}

	@Override
	public List<T> collect() {
		List<T> l = new ArrayList<T>();
		for (T t : this) {
			l.add(t);
		}
		return l;
	}

	static int BATCH_SIZE = 200;

	/**
	 * Visits the caller so that we can batch the selection.
	 */
	@Override
	public boolean visit(Visitor<T> visitor) throws Exception {
		ascending("_id");
		limit(BATCH_SIZE);
		Object lastid = null;
		while (true) {
			if (lastid != null) {
				gt("_id", lastid);
			}
			int n = 0;
			for (T t : this) {
				if (!visitor.visit(t))
					return false;

				Field f = t.getClass().getField("_id");
				lastid = f.get(t);
				n++;
			}
			if (n != BATCH_SIZE)
				return true;

		}
	}

	/**
	 * Index the text and set the keywords field with the tokenized texts.
	 */
	@Override
	public Cursor<T> text(String text) throws Exception {
		Search search = new Search();
		search.addAll(text);
		for (String s : search.set())
			append("keywords", s);

		return this;
	}

	/**
	 * Index the text and set the keywords field with the tokenized texts.
	 */
	@Override
	public Cursor<T> word(String word) throws Exception {
		Search search = new Search();
		search.add(word);
		for (String s : search.set())
			append("keywords", s);

		return this;
	}

	/**
	 * Create a query based on the query string and the templates.
	 * 
	 * @param query
	 *            A free text query string
	 * @param templates
	 *            The parsers picks out key:value strings and uses the templates
	 *            to replace them
	 */
	@Override
	public Cursor<T> query(String q, Map<String, String> templates) throws Exception {
		assert q != null;
		String parts[] = q.split("\\s+");
		Search positive = new Search();
		Search negative = new Search();

		try (Formatter sb = new Formatter("&(");) {

			for (String p : parts) {
				Matcher m = QUERY.matcher(p);
				if (templates != null && m.matches()) {
					boolean neg = m.group(1) != null;
					String key = m.group(2);
					String type = templates.get(key);

					if (type != null) {
						String word = m.group(3);
						if (neg)
							sb.format("(!(" + type + "))", word);
						else
							sb.format("(" + type + ")", word);
					}
				} else {
					if (p.startsWith("-"))
						negative.addAll(p);
					else
						positive.addAll(p);
				}
			}
			for (String key : positive.set()) {
				sb.format("(keywords=%s)", key);
			}
			for (String key : negative.set()) {
				sb.format("(!(keywords=%s)", key);
			}
			where(sb.toString());
			return this;
		}
	}

	MongoCursorImpl<T> all() {
		return this;
	}

	public String toString() {
		return "MongoCursor: [" + where + "]";
	}

	@Override
	public Stream<T> stream() {
		Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator(), Spliterator.DISTINCT+Spliterator.IMMUTABLE+Spliterator.ORDERED);
		return StreamSupport.stream( spliterator, false);
	}
}
