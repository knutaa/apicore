package no.paneon.api.graph;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.paneon.api.utils.Out;

public class Discriminator extends Edge {

    static final Logger LOG = LogManager.getLogger(Discriminator.class);

	public Discriminator(Node node, String relation, Node related, String cardinality, boolean required, boolean deprecated) {
		super(node, relation, related, cardinality, required, deprecated);
	}
		
	public Discriminator(Node node, Node related ) {
		super(node, "", related, "", true, false);
		
		LOG.debug("Discriminator: from={} to={}", node, related);

	}

	@Override
	public String toString() {
		return "discriminator: " + node + " --> " + related;
	}
	

}
