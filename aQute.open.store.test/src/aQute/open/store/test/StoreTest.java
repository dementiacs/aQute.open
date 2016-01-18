package aQute.open.store.test;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.osgi.dto.DTO;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import aQute.open.store.api.DB;
import aQute.open.store.api.Store;
import osgi.enroute.configurer.api.RequireConfigurerExtender;

@RequireConfigurerExtender
public class StoreTest {

		BundleContext context = FrameworkUtil.getBundle(StoreTest.class).getBundleContext();
		static public class Person extends DTO {
			public byte[] _id;
			public String name;
		}
		
		@Test
		public void simple() throws Exception {
			assertNotNull(context);
			DB db = getService(DB.class);
			assertNotNull(db);
			
			Store<Person> store = db.getStore(Person.class, "people"	);
			assertNotNull(store);
			store.drop();
			
			Person p = new Person();
			p.name = "Peter";
			p = store.insert(p);
			
			store.all().forEach( System.out::println);
		}

		private <T> T getService(Class<T> clazz) {
			ServiceReference<T> ref = context.getServiceReference(clazz);
			assertNotNull("No such service for " + clazz,ref);
			T service = context.getService(ref);
			assertNotNull("No such service instance for " + clazz,ref);
			return service;
		}
}
