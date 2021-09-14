package no.paneon.api.graph;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Discriminator extends Edge {

    static final Logger LOG = LogManager.getLogger(Discriminator.class);

	public Discriminator(Node node, String relation, Node related, String cardinality, boolean required) {
		super(node, relation, related, cardinality, required);
	}
		
	public Discriminator(Node node, Node related ) {
		super(node, "", related, "", true);
	}

	@Override
	public String toString() {
		return "discriminator: " + node + " --> " + related;
	}
	

}
