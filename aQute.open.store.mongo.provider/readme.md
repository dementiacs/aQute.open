# AQUTE OPEN STORE MONGO PROVIDER

${Bundle-Description}

To run the JUnit tests, you should have a Mongod running on localhost on the default port.

## Configuration

Configuration is defined in aQute.impl.store.mongo.MongoDBImpl.Config

		/**
		 * The host name or null. If null, the mongo db should be on localhost
		 * and on the default port.
		 * 
		 * @return the host name
		 */
		String host();

		/**
		 * The port number, only used if not 0 and a host is set
		 * 
		 * @return the port number
		 */
		int port();

		/**
		 * The name of the db
		 * 
		 * @return the name of the db
		 */
		String db();

		/**
		 * The name of the db user. If set, a password must also be set.
		 */
		String user();

		/**
		 * The to be used password
		 */
		String _password();

## Example

	/*
	  Define your objects as DTOs
	  with a _id field (byte[]).
	*/
	public class Person extends DTO {
		public byte[] _id;
		public String name;
		public String email;
		public int year;
	}
	
	
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
	assertEquals(2, people.find("year<%s", 1980).count());
	assertEquals(2, people.find("name=M*").count());

	people.all().stream().forEach(p -> 	System.out.println("Name = " + p.name));
	
	assertEquals(3, people.find(thomas).or(mischa).or(mieke).append("points",1,2,3).update());
	
	assertEquals(3, people.find("points>2").count());
	
	thomas.points= new int[] {0};
	people.update(thomas, "points");
	assertEquals(2, people.find("points>2").count());
	
	
