package aQute.impl.store.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.osgi.service.log.LogService;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.lib.converter.Converter;


/**
 * This component is driven by a Managed Service Factory. It opens a Mongo DB,
 * gets a DB object and provides access to the stores. This component implements
 * the aQute.service.store service.
 */
@Component(designateFactory=MongoDBImpl.Config.class)
public class MongoDBImpl implements aQute.open.store.api.DB {
	Mongo		mongo;
	DB			db;
	LogService	log;

	public @interface Config {
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
		
		/**
		 * Reads from the slave are ok
		 */
		boolean slaveOk();

		/**
		 * Replicaset
		 */
		String[] replicas();
	};

	Config	config;
	
	/**
	 * Activate method
	 * @throws Exception 
	 */
	@SuppressWarnings("deprecation")
	@Activate
	void activate(Map<String,Object> props) throws Exception {
		
		this.config = Converter.cnv(Config.class, props);
		String[] replicates = config.replicas();
		if ( replicates != null) {
			List<ServerAddress> addresses = new ArrayList<ServerAddress>();
			for ( String replicate : replicates ) try {
				
				//
				// Check if we had a macro that could not be 
				// expanded. This is handy with Kubernetes
				//
				
				if ( replicate.contains("${"))
					continue;
				
				String parts[] = replicate.split(":");
				if ( parts.length == 1)
					addresses.add( new ServerAddress(parts[0]));
				else
					addresses.add( new ServerAddress(parts[0], Integer.parseInt(parts[1])));
			} catch( Exception e) {
				log.log(LogService.LOG_ERROR, e.getMessage() + " : " + Arrays.toString(replicates));
				e.printStackTrace();
			}
			
			mongo = new Mongo(addresses);
			
			if ( config.slaveOk())
				 mongo.setReadPreference(ReadPreference.secondary());
			
		} else {
			// Get the host
			if (config.host() != null && config.host().length() > 1) {
				if (config.port() != 0)
					mongo = new Mongo(config.host(), config.port());
				else
					mongo = new Mongo(config.host());
			} else
				mongo = new Mongo();
		}
		this.db = mongo.getDB(config.db());
		if (config.db().startsWith("test-")) {
			// databases that start with "test-" are always dropped for
			// testing purposes.
			this.db.dropDatabase();
			this.db = mongo.getDB(config.db());
		}

		// Log in if required
		if (config.user() != null && config.user().length() > 1 && config._password() != null) {
			db.authenticate(config.user(), config._password().toCharArray());
		}

	}

	/**
	 * Close the db and unregister the collections
	 */
	@Deactivate
	void deactivate() {
		mongo.close();
	}

	public <T> MongoStoreImpl<T> getStore(Class<T> clazz, String name) throws Exception {
		return new MongoStoreImpl<T>(this, clazz, db.getCollection(name));
	}

	@Override
	public void drop() {
		checkTest();
		for (String name : db.getCollectionNames()) {
			if (!name.startsWith("system"))
				db.getCollection(name).drop();
		}
	}

	@Reference
	public void setLogService(LogService log) {
		this.log = log;
	}

	void checkTest() {
		if (!config.db().startsWith("test-"))
			throw new SecurityException("This is not a testing database (name must start with 'test-'), it is "
					+ config.db());
	}
	@Override
	public String toString() {
		return "MongoDBImpl [db=" + db + ", config=" + config + "]";
	}
}
