package aQute.impl.store.mongo;

import aQute.bnd.annotation.component.Reference;
import aQute.bnd.util.dto.DTO;
import aQute.open.store.api.DB;
import aQute.open.store.api.Store;
import aQute.test.dummy.ds.DummyDS;
import aQute.test.dummy.log.DummyLog;
import junit.framework.TestCase;

public class ExampleTest extends TestCase {

	DB db;

	@Reference
	void setMongo(MongoDBImpl mongo) throws Exception {
		this.db = mongo;
	}

	public void setUp() throws Exception {
		DummyDS ds = new DummyDS();
		ds.add(this);
		ds.add(MongoDBImpl.class).$("db", "test-mongo");
		ds.add(new DummyLog().direct().stacktrace());
		ds.wire();
	}

	public static class Person extends DTO {
		public byte[]	_id;
		public String	name;
		public String	email;
		public int		year;
		public int[] 	points;
	}

	public void testPerson() throws Exception {
		
		Store<Person> people = db.getStore(Person.class, "test");

		Person peter = new Person();
		peter.name = "Peter";
		peter.email = "Peter.Kriens@aQute.biz";
		peter.year = 1958;
		peter = people.insert(peter);

		Person mieke = new Person();
		mieke.name = "Mieke";
		mieke.email = "Mieke.Kriens@aQute.biz";
		mieke.year = 1962;
		mieke = people.insert(mieke);

		Person mischa = new Person();
		mischa.name = "Mischa";
		mischa.email = "Mischa.Kriens@aQute.biz";
		mischa.year = 1988;
		mischa = people.insert(mischa);

		Person thomas = new Person();
		thomas.name = "Thomas";
		thomas.email = "Thomas.Kriens@aQute.biz";
		thomas.year = 1990;
		thomas = people.insert(thomas);

		assertEquals(4, people.count());
		assertEquals(2, people.find("year<1980").count());
		assertEquals(2, people.find("year<%s", 1980).count());
		assertEquals(2, people.find("name=M*").count());

		people.all().stream().forEach(p -> 	System.out.println("Name = " + p.name));
		
		assertEquals(3, people.find(thomas).or(mischa).or(mieke).append("points",1,2,3).update());
		
		assertEquals(3, people.find("points>2").count());
		
		thomas.points= new int[] {0};
		people.update(thomas, "points");
		assertEquals(2, people.find("points>2").count());
	}
}
