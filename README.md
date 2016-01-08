This is an OSGi enRoute workspace for the aQute open source bundles.

You can add this repository to an OSGi enRoute workspace by adding the following content to your cnf/ext directory:

	-plugin.enroute.aqute = \
	  aQute.bnd.deployer.repository.FixedIndexedRepo; \
	    name        =       aQute; \
	    locations   =       https://raw.githubusercontent.com/pkriens/aQute.open/master/cnf/release/index.xml

