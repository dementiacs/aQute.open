package aQute.impl.store.mongo;

import java.io.EOFException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;
import com.mongodb.gridfs.GridFS;

import aQute.lib.base64.Base64;
import aQute.lib.hex.Hex;
import aQute.open.store.api.Cursor;
import aQute.open.store.api.Store;

@SuppressWarnings("deprecation")
public class MongoStoreImpl<T> implements Store<T> {
	final static Pattern		BINARY_PATTERN	= Pattern
			.compile("\\[h((?:[a-fA-f0-9][a-fA-f0-9])+)]|\\[b([a-zA-Z0-9+/]+={0,2})]");
	final static Pattern		SIMPLE_EXPR		= Pattern.compile("([^=><~*]+)\\s*(=|<=|>=|>|<|~=)\\s*([^\\s]+)");
	final MongoDBImpl			handler;
	final Class<T>				type;
	final DBCollection			collection;
	GridFS						gridfs;
	final Field					_id;
	final Map<String, Field>	unique			= new HashMap<String, Field>();
	final Field					fields[];
	final MongoCodec			mcnv;

	public MongoStoreImpl(MongoDBImpl handler, Class<T> type, DBCollection collection) throws Exception {
		this.mcnv = new MongoCodec(this);
		this.handler = handler;
		this.collection = collection;
		this.type = type;
		fields = type.getFields();
		Field tmp = null;
		for (Field f : fields) {
			if (f.getName().equals("_id"))
				tmp = f;
		}
		if (tmp == null)
			throw new IllegalArgumentException("No _id field, required");

		_id = tmp;
	}

	public MongoStoreImpl<T> unique(String... fields) throws Exception {
		DBObject keys = new BasicDBObject();
		DBObject options = new BasicDBObject().append("unique", true);
		for (String name : fields) {
			type.getField(name);
			keys.put(name, 1);
			unique.put(name, type.getField(name));
		}
		collection.ensureIndex(keys, options);
		return this;
	}

	public T insert(T document) throws Exception {
		Object key = _id.get(document);
		if (key == null) {
			if (_id.getType() == byte[].class)
				_id.set(document, ObjectId.get().toByteArray());
			else if (_id.getType() == String.class)
				_id.set(document, ObjectId.get().toString());
			else
				throw new IllegalArgumentException(
						"Has no _id set and id cann not be created because it is not a byte[] or a String");
		}
		DBObject o = (DBObject) mcnv.toMongo(document);
		try {
			WriteResult result = collection.insert(o);
			CommandResult lastError = result.getLastError();

			if (lastError != null) {
				Integer code = (Integer) lastError.get("code");
				if (code != null && code == 11000)
					return null; // insert failed!

				error(result);
			}
			return document;
		} catch (MongoException.DuplicateKey e) {
			return null;
		}
	}

	public void update(T document, String... fields) throws Exception {
		if (fields == null || fields.length == 0) {
			DBObject o = (DBObject) mcnv.toMongo(document);
			DBObject filter = filter(document);
			error(collection.update(filter, o));
		} else {
			MongoCursorImpl<T> cursor = find(document);

			Class<?> c = document.getClass();
			for (String field : fields) {
				Field f = c.getField(field);
				cursor.set(field, f.get(document));
			}
			cursor.update();
		}
	}

	public void upsert(T document) throws Exception {
		DBObject o = (DBObject) mcnv.toMongo(document);
		DBObject filter = filter(document);
		error(collection.update(filter, o, true, false));
	}

	public MongoCursorImpl<T> all() throws Exception {
		return new MongoCursorImpl<T>(this);
	}

	public MongoCursorImpl<T> find(String where, Object... args) throws Exception {
		return new MongoCursorImpl<T>(this).where(where, args);
	}

	public MongoCursorImpl<T> find(T select) throws Exception {
		return new MongoCursorImpl<T>(this, select);
	}

	void error(WriteResult result) {
		if (result.getLastError() != null && result.getError() != null)
			throw new RuntimeException(result.getError());

	}

	/**
	 * Create a filter out of an LDAP expression.
	 * 
	 * @param where
	 * @param ldap
	 * @return
	 * @throws Exception
	 */
	DBObject filter(String ldap, Object... args) throws Exception {

		for (int i = 0; i < args.length; i++) {
			if (args[i] instanceof byte[])
				args[i] = "[h" + Hex.toHexString((byte[]) args[i]) + "]";

			// TODO more conversions?
		}
		String formatted = String.format(ldap, args);
		if (!formatted.startsWith("("))
			formatted = "(" + formatted + ")";

		Reader r = new StringReader(formatted);
		return expr(r, r.read());
	}

	private DBObject expr(Reader ldap, int c) throws Exception {
		while (Character.isWhitespace(c))
			c = ldap.read();

		assert c == '(';
		DBObject query = new BasicDBObject();

		do {
			c = ldap.read();
		} while (Character.isWhitespace(c));

		switch (c) {
		case '&': {
			List<DBObject> exprs = exprs(ldap);
			query.put("$and", exprs);
			break;
		}

		case '|': {
			List<DBObject> exprs = exprs(ldap);
			query.put("$or", exprs);
			break;
		}

		case '!': {
			List<DBObject> exprs = exprs(ldap);
			query.put("$nor", exprs);
			break;
		}

		case -1:
			throw new EOFException();

		default:
			while (Character.isWhitespace(c))
				c = ldap.read();

			StringBuilder sb = new StringBuilder();
			boolean regex = false;

			while (true) {
				if (c < 0)
					throw new EOFException();

				if (c == '\\') {
					c = ldap.read();
					if (c < 0)
						throw new EOFException();
				} else if (c == '*') {
					regex = true;
					sb.append(".");
				} else if (c == ')')
					break;

				sb.append((char) c);
				c = ldap.read();
			}
			Matcher m = SIMPLE_EXPR.matcher(sb);
			if (!m.matches())
				throw new IllegalArgumentException("Not a valid LDAP expression " + sb);

			String key = m.group(1);
			String op = m.group(2);
			String value = m.group(3);

			if (op.equals("=")) {
				if (".*".equals(value))
					query.put(key, new BasicDBObject("$exists", true));
				else if ("[]".equals(value)) {
					query.put(key, Collections.EMPTY_LIST);
				} else {
					Matcher matcher = BINARY_PATTERN.matcher(value);
					if (matcher.matches()) {
						if (matcher.group(2) != null) // [b matched
							query.put(key, Base64.decodeBase64(matcher.group(2)));
						else
							// [h matched
							query.put(key, Hex.toByteArray(matcher.group(1)));
					} else if (regex) {
						query.put(key, new BasicDBObject("$regex", "^" + value));
						// TODO ensure valid regex for value
					} else
						query.put(key, fromBson(key, value));
				}
			} else if (op.equals(">"))
				query.put(key, new BasicDBObject("$gt", fromBson(key, value)));
			else if (op.equals(">="))
				query.put(key, new BasicDBObject("$gte", fromBson(key, value)));
			else if (op.equals("<"))
				query.put(key, new BasicDBObject("$lt", fromBson(key, value)));
			else if (op.equals("<="))
				query.put(key, new BasicDBObject("$lte", fromBson(key, value)));
			else if (op.equals("~="))
				query.put(key, new BasicDBObject("$regex", fromBson(key, value)).append("$options", "i"));
			// TODO ensure valid regex for value
			else
				throw new IllegalArgumentException("Unknown operator " + op);

			// TODO optimize by recognizing patterns that map to better
			// operators
		}
		return query;
	}

	private Object fromBson(String key, String value) throws Exception {
		Object result = value;
		if ("null".equals(result)) {
			result = null;
		} else if ("true".equals(result)) {
			result = true;
		} else if ("false".equals(result)) {
			result = false;
		}

		try {
			Field field = type.getField(key);
			if (field.getType() == byte[].class) {
				if (value.matches("([0-9a-fA-F][0-9a-fA-F])+"))
					result = Hex.toByteArray(value);
				else if (value.matches("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)"))
					result = Base64.decodeBase64(value);
			}
			// if (result == null)
			else
				result = mcnv.converter.convert(field.getGenericType(), result);
		} catch (Exception e) {
			// ignore
		}

		result = mcnv.toMongo(result);
		if (result == null)
			return result;

		// In a query, we do not specify the
		// collection/array levels
		if (result instanceof Iterable) {
			return ((Iterable<?>) result).iterator().next();
		} else if (result.getClass() != byte[].class && result.getClass().isArray())
			return Array.getLength(result) > 0 ? Array.get(result, 0) : result;
		else
			return result;
	}

	private List<DBObject> exprs(Reader ldap) throws Exception {
		int c;
		do {
			c = ldap.read();
		} while (Character.isWhitespace(c));

		List<DBObject> list = new ArrayList<DBObject>();
		while (c == '(') {
			list.add(expr(ldap, c));

			// read ( for another or ) for close
			c = ldap.read();
		}
		return list;
	}

	BasicDBObject filter(T t) throws IllegalAccessException {
		BasicDBObject or = new BasicDBObject();
		Object id = _id.get(t);
		if (id != null)
			or.append("_id", id);
		else {
			for (Field unique : this.unique.values()) {
				Object u = unique.get(t);
				if (u != null)
					or.append(unique.getName(), u);
			}
		}
		return or;
	}

	boolean checkField(String field, Object value) throws Exception {
		Field f = type.getField(field);
		return f != null;
	}

	public MongoCursorImpl<T> select(String... keys) {
		return new MongoCursorImpl<T>(this).select(keys);
	}

	public byte[] uniqueId() {
		return new ObjectId().toByteArray();
	}

	GridFS getGridFs() {
		if (gridfs == null) {
			this.gridfs = new GridFS(collection.getDB(), collection.getName());

		}
		return gridfs;
	}

	public void drop() {
		handler.checkTest();
		collection.drop();
	}

	/**
	 * TODO implement optimistic locking
	 */
	public Cursor<T> optimistic(T p) throws Exception {
		return find(p);
	}

	@Override
	public long count() {
		return collection.count();
	}

}
