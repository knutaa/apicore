package no.paneon.api.graph;

import org.jgrapht.Graph;

public class APISubGraph extends APIGraph {

	public APISubGraph(CoreAPIGraph core, Graph<Node,Edge> graph, String node, boolean keepTechnicalEdges) {
		super(core, graph, node, keepTechnicalEdges);
	}
	
}
