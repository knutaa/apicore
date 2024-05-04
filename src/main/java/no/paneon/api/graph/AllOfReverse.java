package no.paneon.api.graph;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class AllOfReverse extends Edge {
	
    static final Logger LOG = LogManager.getLogger(AllOfReverse.class);
	
	public AllOfReverse(Edge edge) {
		super(edge.related, "allOf", edge.node, "", true, edge.isDeprecated());
	}

	@Override
	public String toString() {
		return "allOfReverse: " + related + " --> " + node;
	}
	
}
