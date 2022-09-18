package no.paneon.api.graph;

import java.util.Optional;
import java.util.Set;

import org.jgrapht.Graph;

import no.paneon.api.utils.Out;

public class APISubGraph extends APIGraph {

	public APISubGraph(CoreAPIGraph core, Graph<Node,Edge> graph, String parent, String node, boolean keepTechnicalEdges) {
		super(core, graph, node, keepTechnicalEdges);
		
		LOG.debug("APISubGraph; node={}", node);
		
		Optional<Node> n = CoreAPIGraph.getNodeByName(graph, node);
		Optional<Node> parentNode = CoreAPIGraph.getNodeByName(graph, node);

		if(n.isPresent() && parentNode.isPresent()) {
			Node source=parentNode.get();
			Edge e1 = graph.getEdge(source, n.get());
			Edge e2 = graph.getEdge(n.get(), source);

			if(e2 instanceof Discriminator || e1 instanceof AllOf) {
				
				LOG.debug("APISubGraph; node={} e1={} e2={}", node, e1, e2);

				graph.removeEdge(e1);
				graph.removeEdge(e2);
				graph.removeVertex(source);

				CoreAPIGraph.removeUnreachable(graph,n.get());
			}
			
//			Set<Node> inbounds = CoreAPIGraph.getInboundNeighbours(graph,n.get());
//			
//			LOG.debug("APISubGraph; node={} inbounds={}", node, inbounds);
//
//			inbounds.forEach(source -> {
//				Edge e1 = graph.getEdge(source, n.get());
//				Edge e2 = graph.getEdge(n.get(), source);
//
//				if(e2 instanceof Discriminator || e1 instanceof AllOf) {
//					
//					LOG.debug("APISubGraph; node={} e1={} e2={}", node, e1, e2);
//
//					graph.removeEdge(e1);
//					graph.removeEdge(e2);
//					graph.removeVertex(source);
//
//					CoreAPIGraph.removeUnreachable(graph,n.get());
//				}
//			});
		}
		
		LOG.debug("APISubGraph; node={} edges={}", node, graph.edgeSet());

	}
	
}
