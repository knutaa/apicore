package no.paneon.api.graph.complexity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;

import no.paneon.api.graph.APIGraph;
import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.graph.Property;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class ComplexityAdjustedAPIGraph {

    static final Logger LOG = LogManager.getLogger(ComplexityAdjustedAPIGraph.class);
	
	Map<String, Map<String,Graph<Node,Edge>> > allGraphs;

	CoreAPIGraph graph;
	Set<String> resources;
	
	static final int GRAPH_PRUNE_MIN_SIZE = 10;
	static final String REF_OR_VALUE = "RefOrValue";
	static final String GRAPH_PRUNE_NONINHERIT_LIMIT = "graphPruneNonInheritLimit";
	
	boolean keepTechnicalEdges;
	
	public ComplexityAdjustedAPIGraph(CoreAPIGraph graph, boolean keepTechnicalEdges) {
		this.graph = graph;
		
		this.allGraphs = new HashMap<>();		
		this.resources = new HashSet<>();
		this.keepTechnicalEdges = keepTechnicalEdges;
	}
	
//	@LogMethod(level=LogLevel.DEBUG)
//	private void generateSubGraphs() {
//		for(String resource : resources) {
//			generateSubGraphsForResource(resource);
//		}	
//	}

	@LogMethod(level=LogLevel.DEBUG)
	public void generateSubGraphsForResource(List<String> allResources, String resource) {
	    LOG.debug("### generateSubGraphsForResource: resource=" + resource);

	    Node resourceNode = graph.getNode(resource);
	    
	    if(resourceNode==null) {
	    	Out.printAlways("# ... specified resource '" + resource + "' not found in API - no further processing");
	    	return;
	    }
	    
	    Graph<Node,Edge> resourceGraph = CoreAPIGraph.getSubGraphWithInheritance(allResources, graph.getCompleteGraph(), resourceNode, resourceNode);

		LOG.debug("###### generateSubGraphsForResource: #0 nodes={}", graph.getCompleteGraph().vertexSet() );
		LOG.debug("###### generateSubGraphsForResource: #0 edges={}", graph.getCompleteGraph().edgeSet() );

		LOG.debug("###### generateSubGraphsForResource: #0 node={} edgesForResourceNode={}",  resource, resourceGraph.edgesOf(resourceNode));

		LOG.debug("generateSubGraphsForResource:: #1 resource={} resourceGraph={}",  resource, resourceGraph.vertexSet() );
		
		LOG.debug("generateSubGraphsForResource:: #1 resource={} resourceGraph={}",  resource, resourceGraph.vertexSet() );

	    resourceGraph = CoreAPIGraph.cleanExplicitResources(resourceGraph, allResources, resource);
	    resourceGraph = CoreAPIGraph.cleanDiscriminatorEdges(resourceGraph, resource);

		LOG.debug("generateSubGraphsForResource:: #1 resource={} resourceGraph={}",  resource, resourceGraph.vertexSet() );

		LOG.debug("###### generateSubGraphsForResource: #0 node={} edgesForResourceNode={}",  resource, resourceGraph.edgesOf(resourceNode));

		LOG.debug("generateSubGraphsForResource:: #2 resource={} resourceGraph={}",  resource, resourceGraph.vertexSet() );

		LOG.debug("generateSubGraphsForResource:: resource={} resourceGraph={}",  resource, resourceGraph.edgeSet().stream().map(Object::toString).collect(Collectors.joining("\n")));

	    LOG.debug("### generateSubGraphsForResource: resource={} complete vertices={}", resource, graph.getCompleteGraph().vertexSet());

	    LOG.debug("### generateSubGraphsForResource: resource={} vertices={}", resource, resourceGraph.vertexSet());

	    Map<String,Graph<Node,Edge>> graphMap = createSubGraphsGraphFromComplexity(resourceGraph, resourceNode, allResources);

	    LOG.debug("### generateSubGraphsForResource: resource={} graphMap={}", resource, graphMap.keySet());
	    if(graphMap.containsKey(resource))
	    	LOG.debug("### generateSubGraphsForResource: resource={} graphMap={}", resource, graphMap.get(resource).vertexSet());

	    this.allGraphs = adjustSubGraphs(allResources, resourceNode, resourceGraph, graphMap);
	    	    
	    LOG.debug("### generateSubGraphsForResource: resource={} allGraphs={}", resource, this.allGraphs.keySet());
	    
	    for(String node : this.allGraphs.get(resource).keySet()) {
		    LOG.debug("### generateSubGraphsForResource: resource={} node={} nodes={}", resource, node, this.allGraphs.get(resource).get(node).vertexSet());
	    }

	}
	

	private Map<String, Graph<Node, Edge>> createSubGraphsGraphFromComplexity(Graph<Node,Edge> resourceGraph, Node resourceNode, Collection<String> allResources) {
			
		String resource = resourceNode.getName();
			    
		LOG.debug("createSubGraphsGraphFromComplexity: #0 node={} resourceGraph={}",  resource, resourceGraph.vertexSet());
		LOG.debug("createSubGraphsGraphFromComplexity: #0 node={} resourceGraph={}",  resource, resourceGraph.vertexSet().size());

		LOG.debug("###### createSubGraphsGraphFromComplexity: #0 node={} edgesForResourceNode={}",  resource, resourceGraph.edgesOf(resourceNode));

		// remove the other resources in allResources
		
		Predicate<Node> otherResource = n -> allResources.contains(n.getName()) && !n.getName().contentEquals(resource);
		
		Set<Node> otherResources = resourceGraph.vertexSet().stream()
										.filter(otherResource)
										.collect(toSet());
		
		LOG.debug("##### createSubGraphsGraphFromComplexity: node={} otherResources={} allResources={}",  resource, otherResources, allResources);

	    boolean isSimplified = false; // simplifyGraphForComplexDiscriminators(resourceGraph, resourceNode);
	    
		LOG.debug("#0 createSubGraphsGraphFromComplexity: #00 node={} resourceGraph={}",  resource, resourceGraph.vertexSet());

	    LOG.debug("createSubGraphsGraphFromComplexity: resource={} isSimplified={} resourceGraph={}" , resource, isSimplified, resourceGraph.vertexSet());

	    GraphComplexity analyser = new GraphComplexity(resourceGraph, resourceNode);
	    
		LOG.debug("createSubGraphsGraphFromComplexity: #0a node={} resourceGraph={}",  resource, resourceGraph.vertexSet());

	    Map<Node,Integer> complexity = analyser.computeGraphComplexity();
	    
		LOG.debug("#0 createSubGraphsGraphFromComplexity: complexity={}",  complexity);

	    LOG.debug("createSubGraphsGraphFromComplexity: complexity resourceNode={} keys={}" , resourceNode, complexity.keySet());
	    
	    Map<String,Graph<Node,Edge>> graphMap = new LinkedHashMap<>();
	    
		LOG.debug("## createSubGraphsGraphFromComplexity: node={} complexity={}",  resource, complexity.keySet());

		LOG.debug("createSubGraphsGraphFromComplexity: #1 node={} resourceGraph={}",  resource, resourceGraph.vertexSet().size());
		LOG.debug("createSubGraphsGraphFromComplexity: #1 node={} complexity={}",  resource, complexity);

	    if(complexity.isEmpty()) {    	
	    	graphMap.put(resource, resourceGraph);
	    	
    		LOG.debug("createSubGraphsGraphFromComplexity: ##1 node={} subGraph={}",  resource, resourceGraph.vertexSet());

	    } else {
	    	List<Node> subGraphs = new LinkedList<>();
	    	
	    	subGraphs.addAll(complexity.keySet());
	    	subGraphs.remove(resourceNode);
	    	subGraphs.add(resourceNode);
	    	
			LOG.debug("createSubGraphsGraphFromComplexity: #1 node={} subGraphs={}",  resource, subGraphs);

	    	for(Node node : subGraphs ) {
	    		
	    		LOG.debug("## createSubGraphsGraphFromComplexity: resource={} node={}",  resource, node);

	    		Graph<Node,Edge> subGraph = CoreAPIGraph.getSubGraphWithInheritance(allResources, resourceGraph, node, resourceNode);
	    		
	    		LOG.debug("### createSubGraphsGraphFromComplexity: #2 node={} subGraph resourceGraph={}",  node, resourceGraph.vertexSet());
	    		LOG.debug("### createSubGraphsGraphFromComplexity: #2 node={} subGraph nodes={}",  node, subGraph.vertexSet());
//	    		LOG.debug("createSubGraphsGraphFromComplexity: #2 node={} subGraph edges={}",  node, 
//	    				subGraph.edgeSet().stream().map(Edge::toString).collect(Collectors.joining("\n")));

	    		Set<Node> nodes_in = new HashSet<>(subGraph.vertexSet());
	    		isSimplified = simplifyGraphForComplexDiscriminators(subGraph, node, allResources); // 06-2023
	    		
	    		Set<Node> nodes_out = new HashSet<>(subGraph.vertexSet());
	    	    LOG.debug("createSubGraphsGraphFromComplexity: node={} nodes_in ={}" , node, nodes_in);
	    	    LOG.debug("createSubGraphsGraphFromComplexity: node={} nodes_out={}" , node, nodes_out);

	    	    nodes_in.removeAll(nodes_out);

	    	    LOG.debug("createSubGraphsGraphFromComplexity: node={} isSimplified={} removed={}" , node, isSimplified, nodes_in);

	    	    LOG.debug("createSubGraphsGraphFromComplexity: node={} isSimplified={} subGraph={}" , node, isSimplified, subGraph.vertexSet());

//		    		if(!this.keepTechnicalEdges) { 
//		    			CoreAPIGraph.removeTechnicalAllOfs(subGraph);
//		    			CoreAPIGraph.removeRedundantRelationships(subGraph, resourceNode);
//		    		}
	    		
	    	    
	    	    LOG.debug("createSubGraphsGraphFromComplexity: node={} subGraph={}" , node, subGraph.vertexSet().size());

	    	    if(!GraphComplexity.tooSmallGraph(node, subGraph) || APIModel.isAsyncAPI()) {
	    	    	graphMap.put(node.getName(), subGraph);
	    	    } else {
		    	    LOG.debug("createSubGraphsGraphFromComplexity: node={} tooSmallGraph=" , node );

	    	    }
	    	    	
	    	}
	    	
	    	graphMap.keySet().forEach(key -> {
	    		LOG.debug("### createSubGraphsGraphFromComplexity: resource={} #vertices={}", key, graphMap.get(key).vertexSet().size());
	    		LOG.debug("### createSubGraphsGraphFromComplexity: resource={} key_in_graph={}", key, 
	    				graphMap.get(key).vertexSet() );

	    	});
	    	
	    }
	    
	    return graphMap;
	}

	private boolean simplifyGraphForComplexDiscriminators(Graph<Node,Edge> graph, Node resourceNode, Collection<String> allResources) {
		boolean res = false;
		
		Set<Node> nodes = graph.vertexSet();
		
		Predicate<Node> notResourceNode = n -> !n.equals(resourceNode);
		
		int discriminatorCutOff = Config.getInteger("maxDiscriminators",4);
				
		Set<Node> complexDiscriminators = nodes.stream()
											.filter(n -> outBoundDiscriminators(graph,n)>discriminatorCutOff)
											.filter(notResourceNode)
											.collect(toSet());
		
		LOG.debug("simplifyGraphForComplexDiscriminators: resourceNode={} cutOff={} complexDiscriminators={}", resourceNode, discriminatorCutOff, complexDiscriminators);

	    if(!complexDiscriminators.isEmpty()) LOG.debug("simplifyGraphForComplexDiscriminators: resourceNode={} complexDiscriminators={}", resourceNode, complexDiscriminators);

	    complexDiscriminators.retainAll( graph.vertexSet());
	    
		LOG.debug("simplifyGraphForComplexDiscriminators: complexDiscriminators={} allResources={}", complexDiscriminators, allResources);

	    for(Node n : complexDiscriminators) {
	    	if(!graph.containsVertex(n)) continue;
	    	
	    	Predicate<Node> notSelf = node -> !node.getName().contentEquals(n.getName());
	    	
	    	Set<Node> outbound = graph.outgoingEdgesOf(n).stream()
	    							.filter(Edge::isDiscriminator)
	    							.map(graph::getEdgeTarget)
	    							.filter(notSelf)
	    							.collect(toSet());
	    		    	
	    	LOG.debug("simplifyGraphForComplexDiscriminators: resourceNode={} remove={}", resourceNode, outbound);

	    	Set<Node> retainDirectNeighbours = APIGraph.getOutboundNeighbours(graph, resourceNode);
	    	
	    	outbound.removeAll(retainDirectNeighbours);
	    	
	    	// if(!retainDirectNeighbours.contains(n)) outbound.add(n); // 06-2023 : not necessary to have direct edge from the diagram root node
	    	
	    	if(allResources.contains(n.getName()) && !n.getName().contentEquals(resourceNode.getName())) {
	    		outbound.add(n);
		    	// if(!retainDirectNeighbours.contains(n)) outbound.add(n); // 06-2023 : not necessary to have direct edge from the diagram root node

	    	}
	    
	    	LOG.debug("## simplifyGraphForComplexDiscriminators: resourceNode={} remove={}", resourceNode, outbound);

	    	// check if there are containing nodes and discriminator relationships
	    	Set<Node> retain = new HashSet<>();
	    	outbound.forEach(node -> {
	    		Set<Node> from = APIGraph.getInboundNeighbours(graph, node);
	    		for(Node q : from) {
	    			Set<Edge> edges = graph.outgoingEdgesOf(q);
	    			if(edges.size()>1 && edges.size()<=discriminatorCutOff) retain.add(node);
	    		}
	    	});
	    	
	    	outbound.remove(resourceNode);
	    	
	    	outbound.removeAll(retain);

	    	LOG.debug("simplifyGraphForComplexDiscriminators: final resourceNode={} remove={}", resourceNode, outbound);

	    	graph.removeAllVertices(outbound);

	    	res = res || !outbound.isEmpty();
	    	
	    }
			
	    return res;
	}

	private long outBoundDiscriminators(Graph<Node,Edge> graph, Node n) {
		return graph.outgoingEdgesOf(n).stream().filter(Edge::isDiscriminator).count();
	}

//	private final String NEWLINE = "\n";
//	
//	private String debug(Map<String,Graph<Node,Edge>> graphs) {
//		StringBuilder str = new StringBuilder();
//				
//		if(graphs.isEmpty()) return str.toString();
//		
//	    for(String graph : graphs.keySet() ) {
//	    	Graph<Node,Edge> g = graphs.get(graph);
//	    	
//			str.append(NEWLINE);
//		    str.append( "... subGraph=" + graph + " nodes=" + g.vertexSet() );
//		    
//			str.append(NEWLINE);
//		    str.append( "... subGraph=" + graph + " edges=" + g.edgeSet() );
//
//	    }
//		str.append(NEWLINE);
//				
//		return str.toString();
//	}
	
	private Map<String,Graph<Node,Edge>> addMissingMappedResources(Collection<String> allResources, Graph<Node, Edge> graph, Node pivot, Set<Node> nodes) {
		
	    LOG.debug("addMissingMappedResources: nodes={}", nodes);
	    LOG.debug("addMissingMappedResources: graph_nodes={}", graph.vertexSet());

		Map<String, Graph<Node, Edge>> res = new HashMap<>();
		
		Set<String> mapping = nodes.stream()
								// .map(Node::getAllDiscriminatorMapping)
								.map(Node::getLocalDiscriminators)
								.flatMap(Set::stream)
								.collect(toSet());
			    				
		mapping.remove(pivot.getName());
		
	    LOG.debug("addMissingMappedResources: pivot={} mapping={}", pivot, mapping);
		
		mapping.removeAll(nodes.stream().map(Node::getName).collect(toSet()) );
		
	    LOG.debug("addMissingMappedResources: mapping={}", mapping);

		Set<String> refs = mapping.stream()
				.filter(s -> s.endsWith("Ref") )
				// .map(s -> s.replaceFirst("Ref$",""))
				.collect(toSet());
		
		final Set<String> refsCore = nodes.stream()
				.map(Node::getName)
				.filter(refs::contains)
				.collect(toSet());
		
		mapping = mapping.stream()
				.filter(s -> !refsCore.contains(s))
				.collect(toSet());
		
	    LOG.debug("addMissingMappedResources: nodes={}", nodes);
		LOG.debug("addMissingMappedResources: refsCore={}", refsCore);

	    LOG.debug("addMissingMappedResources: mapping={}", mapping);
	    LOG.debug("addMissingMappedResources: nodes={}",   nodes);
	    LOG.debug("addMissingMappedResources: graph={}",   graph.edgeSet());

	    LOG.debug("addMissingMappedResources: mapping={}", mapping);

	    LOG.debug("addMissingMappedResources: graph={}", graph.vertexSet());

	    for(String candidate : mapping) {
	    	Map<String, Graph<Node, Edge>> special = specialDisciminatorCase(allResources,graph,candidate);
	    	
	    	res.putAll(special);
	    		
	    	if(special.isEmpty()) {
	    		res.putAll( addRegularNode(allResources, graph,candidate) );
	    	}
	    	
	    }
		
	    LOG.debug("addMissingMappedResources: res={}", res.keySet());

	    return res;
	}

	private  Map<String, Graph<Node, Edge>>  specialDisciminatorCase(Collection<String> allResources, Graph<Node, Edge> graph, String candidate) {
		Map<String, Graph<Node, Edge>> res = new HashMap<>();
		
    	Optional<Node> optNode = CoreAPIGraph.getNodeByName(graph, candidate);
    	
	    LOG.debug("specialDisciminatorCase: candidate={} optNode={}", candidate, optNode);

    	if(optNode.isPresent()) {
    		Node node = optNode.get();
    		
    	    LOG.debug("specialDisciminatorCase: node={}", node);

    		Set<Node> fromNodes = graph.incomingEdgesOf(node).stream().map(graph::getEdgeSource).distinct().collect(toSet());
    		
    		fromNodes = fromNodes.stream().filter(n -> graph.outgoingEdgesOf(n).stream().noneMatch(Edge::isAllOf)).collect(toSet());
    		
    	    LOG.debug("specialDisciminatorCase: node={} fromNodes={}", node, fromNodes);

    		if(fromNodes.size()==1) {
    			Node discriminatorNode = fromNodes.iterator().next();	    
    			
    			if( discriminatorNode.getExternalDiscriminatorMapping().size() > 2 ) {
    		    	Graph<Node, Edge> g = CoreAPIGraph.getSubGraphWithInheritance(allResources, graph, discriminatorNode, discriminatorNode);
    		    	
    		    	Set<Edge> discriminatorEdges = g.outgoingEdgesOf(discriminatorNode).stream()
    		    										.filter(Edge::isDiscriminator)
    		    										.collect(toSet());
    		    	
    		    	discriminatorEdges = discriminatorEdges.stream()
    		    							.filter( e -> g.getEdge( g.getEdgeTarget(e), discriminatorNode) !=null)
    		    							.collect(toSet());
    		    	
    	    	    LOG.debug("specialDisciminatorCase: discriminatorNode={} discriminatorEdges={}", discriminatorNode, discriminatorEdges);

    	    	    
    	    	    // TBD - cleanup in case of pair of allOf and discriminator between nodes
    		    	// g.removeAllEdges(discriminatorEdges);
    		    	
    				res.put(discriminatorNode.getName(), g);
    			}
    		}    
    		
    	    LOG.debug("## specialDisciminatorCase: candidate={} node={} res={}", candidate, node, res.keySet());

    	}
    	
		return res;
	}

	private Map<String, Graph<Node, Edge>> addRegularNode(Collection<String> allResources, Graph<Node, Edge> graph, String candidate) {
		Map<String, Graph<Node, Edge>> res = new HashMap<>();
		
    	Optional<Node> optNode = CoreAPIGraph.getNodeByName(graph, candidate);
    	
    	if(optNode.isPresent()) {
    		Node node = optNode.get();
		    LOG.debug("addRegularNode: node={} edges={}", node, graph.outgoingEdgesOf(node));

	    	Graph<Node, Edge> g = CoreAPIGraph.getSubGraphWithInheritance(allResources, graph, node, node);
	    	
    		if(!this.keepTechnicalEdges) { 
    			CoreAPIGraph.removeTechnicalAllOfs(g);
    			CoreAPIGraph.removeRedundantRelationships(g, node);
    		}
    		
	    	if(!g.edgeSet().isEmpty()) {
	    		res.put(candidate, g);
	    		
	    		LOG.debug("addRegularNode: candidate={} graph={}", candidate, g.vertexSet());

	    		LOG.debug("addRegularNode: candidate={} graph={}", candidate, res.get(candidate));
	    	}
    	}		
    	
    	return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void pruneSubGraphsFromContainingGraphs(String resource, Map<String, Graph<Node, Edge>> graphMap) {
		
		boolean hasRemoved=true;
		
		while(hasRemoved) {
			hasRemoved=false;
		    List<String> remainingGraphs = getGraphsBySize(graphMap);
		    	    
			while(!remainingGraphs.isEmpty()) {		
				String node = remainingGraphs.remove(0);
								
				LOG.debug("#10 pruneSubGraphsFromContainingGraphs: node={} remainingGraphs={}",  node, remainingGraphs);
	
				for(String containing : remainingGraphs) {	
					boolean removed = removeContainedSubgraph(resource, node, containing, graphMap.get(node), graphMap);

					LOG.debug("#12 pruneSubGraphsFromContainingGraphs: resource={} node={} containing={} removed={}",  resource, node, containing, removed);

					hasRemoved = hasRemoved || removed;
					
				}
					
			}
		}
				
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean removeContainedSubgraph(String resource, String rootOfGraphToRemove, String rootOfGraphToPrune, Graph<Node, Edge> subGraphToRemove, Map<String,Graph<Node,Edge>> graphMap) {
		boolean res = false;
		Graph<Node,Edge> originalGraph = graphMap.get(rootOfGraphToPrune);

		LOG.debug("#13I removeContainedSubgraph: rootOfGraphToPrune={} rootOfGraphToRemove={}",  rootOfGraphToPrune, rootOfGraphToRemove);

		Optional<Node> optSubResource = getNodeByName(originalGraph,rootOfGraphToPrune);
		Optional<Node> optRootToPrune = getNodeByName(originalGraph,rootOfGraphToRemove);

		LOG.debug("#13 removeContainedSubgraph: optSubResource={} optRootToPrune={}",  optSubResource, optRootToPrune);

		if(!optSubResource.isPresent() || !optRootToPrune.isPresent()) {
			LOG.debug("#13A removeContainedSubgraph: optSubResource={} optRootToPrune={}",  optSubResource, optRootToPrune);
			return res;
		}
		
		if(rootOfGraphToRemove.contentEquals(resource)) {
			LOG.debug("#13B removeContainedSubgraph: rootOfGraphToRemove={} resource={}",  rootOfGraphToRemove, resource);
			return res;
		}
		
		if(!optRootToPrune.isPresent()) {
			LOG.debug("#13C removeContainedSubgraph: optRootToPrune={}",  optRootToPrune);
			return res;
		}

//		Set<Edge> outgoingFromRoot = CoreAPIGraph.getNonInheritedEdges(originalGraph,optSubResource.get());
//		boolean isTooSimple = outgoingFromRoot.size()<=Config.getInteger(GRAPH_PRUNE_NONINHERIT_LIMIT, 4);
//		
//		LOG.debug("#13D removeContainedSubgraph: rootOfGraphToPrune={} outgoingFromRoot.size()={} isTooSimple={}",  
//				rootOfGraphToPrune, outgoingFromRoot.size(), isTooSimple);
//
//		if(isTooSimple) return res;
		
		Node subResource = optSubResource.get();
		
		Optional<Node> optResource = getNodeByName(originalGraph,rootOfGraphToRemove); // was resource
		
		LOG.debug("removeContainedSubgraph: rootOfGraphToPrune={} subResource={}",  rootOfGraphToPrune, subResource);

		if(!optResource.isPresent()) return res;

		Node resourceNode = optResource.get();

		Graph<Node,Edge> graphToPrune = originalGraph; // CoreAPIGraph.getSubGraphWithInheritance(originalGraph, subResource, resourceNode);

		LOG.debug("removeContainedSubgraph: subResource={} graphToPrune={}",  subResource, graphToPrune.vertexSet());

		// if(graphToPrune.vertexSet().size()<GRAPH_PRUNE_MIN_SIZE) return;

		LOG.debug("removeContainedSubgraph: subResource={} remove edges={}",  subResource, graphToPrune.outgoingEdgesOf(optRootToPrune.get()));

		Set<Edge> edgesToRemove = graphToPrune.outgoingEdgesOf(optRootToPrune.get()).stream().collect(toSet());
		
		graphToPrune.removeAllEdges(edgesToRemove);
		
		LOG.debug("removeContainedSubgraph: subResource={} nodes={}",  subResource, graphToPrune.vertexSet());
		
		Set<Node> nodesWithEdges = edgesToRemove.stream().map(Edge::getRelated).collect(toSet());
		
		nodesWithEdges = nodesWithEdges.stream().map(graphToPrune::incomingEdgesOf).flatMap(Set::stream).map(Edge::getNode).collect(toSet());
		
		LOG.debug("removeContainedSubgraph: subResource={} nodes with edges={}",  subResource, nodesWithEdges);

		boolean hasRemoved=true;
		while(hasRemoved) {

			Predicate<Node> notDiscriminatorFromResourceNode = n -> graphToPrune.getEdge(subResource,n)==null || graphToPrune.getEdge(subResource,n).isDiscriminator();
			
			hasRemoved=false;
			final Graph<Node,Edge> g = graphToPrune;
			Set<Node> nodesWithNoIncomingEdges = graphToPrune.vertexSet().stream()
				.filter(n -> g.incomingEdgesOf(n).isEmpty())
				.filter(n -> !n.equals(subResource) && !n.equals(resourceNode))
				.filter(n -> ! subResource.getDiscriminatorMapping().contains(n.getName()))
				.collect(toSet());
			
			LOG.debug("#11 removeContainedSubgraph: subResource={} subResource.getDiscriminatorMapping={}",  subResource, subResource.getDiscriminatorMapping() );
			LOG.debug("#11 removeContainedSubgraph: subResource={} rootOfGraphToPrune={} nodesWithNoIncomingEdges={}",  subResource, rootOfGraphToPrune, nodesWithNoIncomingEdges );
			
			hasRemoved = graphToPrune.removeAllVertices(nodesWithNoIncomingEdges);
			res = res || hasRemoved;

		}
		
		boolean removed=true;
		while(removed) {
			removed = false;
			Set<Node> noInbound = graphToPrune.vertexSet().stream()
										.filter(n -> graphToPrune.incomingEdgesOf(n).isEmpty())
										.filter(n -> !n.equals(subResource))
										.collect(toSet());
	
			Set<Node> onlyDiscriminator = graphToPrune.vertexSet().stream()
										.filter(n -> graphToPrune.incomingEdgesOf(n).stream().allMatch(Edge::isDiscriminator))
										.filter(n -> !n.equals(subResource))
										.collect(toSet());
			
			LOG.debug("removeContainedSubgraph:: node={} NO inbund discriminator={}", rootOfGraphToPrune, noInbound);
			LOG.debug("removeContainedSubgraph:: node={} only inbund discriminator={}", rootOfGraphToPrune, onlyDiscriminator);
	
//			graphToPrune.removeAllVertices(noInbound);
//			graphToPrune.removeAllVertices(onlyDiscriminator);
			
//			removed = !(noInbound.isEmpty() && onlyDiscriminator.isEmpty());
			
			res = res || removed;
 
			
		}
		
		// graphToPrune = addSimpleInheritance(subResource, graphToPrune, originalGraph, rootOfGraphToPrune.contentEquals(resource));
		
		// graphToPrune = revertToOriginalIfTooSmall(subResource, graphToPrune, originalGraph, rootOfGraphToPrune.contentEquals(resource));
							
		graphMap.put(rootOfGraphToPrune, graphToPrune);
		
		LOG.debug("#14 removeContainedSubgraph: rootOfGraphToPrune={} rootOfGraphToRemove={} pruned graph={} ", 
				rootOfGraphToPrune, rootOfGraphToRemove, graphToPrune.vertexSet());

		return hasRemoved;
		
	}


//	@LogMethod(level=LogLevel.DEBUG)
//	private void removeContainedSubgraph_old(Collection<String> allResources, String resource, String root, Graph<Node, Edge> subGraphToRemove, Map<String,Graph<Node,Edge>> graphMap) {
//		Graph<Node,Edge> originalGraph = graphMap.get(root);
//
//		Optional<Node> optSubResource = getNodeByName(originalGraph,root);
//
//		LOG.debug("removeContainedSubgraph: subResource={}",  optSubResource);
//
//		if(!optSubResource.isPresent()) return;
//		
//		Node subResource = optSubResource.get();
//		
//		Optional<Node> optResource = getNodeByName(originalGraph,resource);
//		if(!optResource.isPresent()) return;
//
//		Node resourceNode = optResource.get();
//
//		Graph<Node,Edge> graphToPrune = CoreAPIGraph.getSubGraphWithInheritance(allResources,originalGraph, subResource, resourceNode);
//
//		LOG.debug("removeContainedSubgraph: subResource={} graphToPrune={}",  subResource, graphToPrune.vertexSet());
//
//		if(graphToPrune.vertexSet().size()<GRAPH_PRUNE_MIN_SIZE) return;
//						
//		Set<Node> nodesToPrune = subGraphToRemove.vertexSet().stream().filter(graphToPrune::containsVertex).collect(toSet());
//				    	
//		LOG.debug("removeContainedSubgraph: #0 subResource={} nodesToPrune={}",  subResource, nodesToPrune);
//				
//		Set<Edge> edgesToPrune = nodesToPrune.stream()
//									.map(graphToPrune::outgoingEdgesOf)
//									.flatMap(Set::stream)
//									.collect(toSet());
//
//		LOG.debug("removeContainedSubgraph: #1 subResource={} nodesToPrune={}",  subResource, nodesToPrune);
//		LOG.debug("removeContainedSubgraph: #1 subResource={} edgesToPrune={}",  subResource, edgesToPrune);
//
//		graphToPrune.removeAllEdges(edgesToPrune);
//					
//		removeOrphans(graphToPrune, subResource);
//				 
//		Set<Node> remainingNodes = graphToPrune.vertexSet();
//		
//		LOG.debug("removeContainedSubgraph: #2 subResource={} remainingNodes={}",  subResource, remainingNodes);
//
//		final Graph<Node,Edge> g = graphToPrune;
//		Set<Edge> reinstateEdges = edgesToPrune.stream()
//			.filter(edge -> edge.isMarked)
//			.filter(edge -> remainingNodes.contains(edge.node))
//			.filter(edge -> remainingNodes.contains(edge.related))
//			.filter(edge -> isCompleteCandidate(g, edge.node))
//			.filter(edge -> isCompleteCandidate(g, edge.related))
//			.filter(Edge::isInheritance)
//			.filter(Edge::isOneOf)
//			.collect(toSet());
//		
//		LOG.debug("removeContainedSubgraph: subResource={} reinstateEdges={}",  subResource, reinstateEdges);
//		
//		for(Edge edge : reinstateEdges) {
//			Node source = originalGraph.getEdgeSource(edge);
//			Node target = originalGraph.getEdgeTarget(edge);
//			if(!isRefOrValue(source)) {
//				graphToPrune.addEdge(source, target, edge);
//				LOG.debug("removeContainedSubgraph: addEdge source={} target={} edge={}", source, target, edge);
//			}
//		}
//		
//		graphToPrune = addSimpleInheritance(subResource, graphToPrune, originalGraph, root.contentEquals(resource));
//		
//		graphToPrune = revertToOriginalIfTooSmall(subResource, graphToPrune, originalGraph, root.contentEquals(resource));
//							
//		graphMap.put(root, graphToPrune);
//		
//		LOG.debug("removeContainedSubgraph: final subResource={} graphMap={}", subResource, graphMap.keySet());
//
//		
//	}

	private Graph<Node, Edge> addSimpleInheritance(Node resource, Graph<Node, Edge> activeGraph, Graph<Node, Edge> originalGraph, boolean contentEquals) {
		
		Predicate<Node> isInheritanceOnly = n -> originalGraph.outgoingEdgesOf(n).stream().allMatch(Edge::isInheritance);
		
		Predicate<Node> notCoreInheritance = n -> n.getInheritance().size()==0 && !n.isEnumNode();
		
		Set<Node> simpleInheritance = activeGraph.vertexSet().stream().filter(isInheritanceOnly).filter(notCoreInheritance).collect(toSet());
		
		LOG.debug("addSimpleInheritance: resource={} simpleInheritance={}", resource, simpleInheritance);
		
		for(Node n : simpleInheritance) {
			for(Edge edge : originalGraph.outgoingEdgesOf(n)) {
				Node source = originalGraph.getEdgeSource(edge);
				Node target = originalGraph.getEdgeTarget(edge);
				activeGraph.addVertex(target);
				activeGraph.addEdge(source, target, edge);
				LOG.debug("addSimpleInheritance: addEdge source={} target={} edge={}", source, target, edge);
			}
		}

		return activeGraph;
	}

	private boolean isRefOrValue(Node node) {
		return node.getName().endsWith("RefOrValue");
	}

	private Graph<Node, Edge> revertToOriginalIfTooSmall(Node subResource, Graph<Node, Edge> prunedGraph, Graph<Node, Edge> originalGraph, boolean isResourceGraph) {
	    int preComplexity = getComplexity(originalGraph, subResource);	
	    int postComplexity = getComplexity(prunedGraph, subResource);

	    double fraction = (preComplexity-postComplexity)/(1.0*preComplexity);
	    
	    boolean isAboveLimit = GraphComplexity.isAboveLowerLimit(postComplexity);
	    
		if( !isAboveLimit && 
			( fraction<0.05 || prunedGraph.vertexSet().size()<6 || (prunedGraph.vertexSet().size()<6 && isResourceGraph))) {			

			LOG.debug("revertToOriginalIfTooSmall: subResource={} isAboveLowerLimit={} fraction={} preComplexity={} postComplexity={}",  
					subResource, isAboveLimit, fraction, preComplexity, postComplexity);
			
			LOG.debug("revertToOriginalIfTooSmall: prunedGraph={}", prunedGraph);
			
			prunedGraph = originalGraph;
		}

		return prunedGraph;
	
	}

	private int getComplexity(Graph<Node, Edge> graph, Node root) {
	    GraphComplexity analyser = new GraphComplexity(graph, root);	    
	    analyser.computeGraphComplexity();
	    return analyser.getComplexity();	

	}

	@LogMethod(level=LogLevel.DEBUG)
	private Optional<Node> getNodeByName(Graph<Node, Edge> graph, String node) {
		return CoreAPIGraph.getNodeByName(graph, node);	
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void removeSubGraphsCoveredByContainingGraph(Collection<String> allResources, String resource, Map<String, Graph<Node, Edge>> graphMap) {
		Set<String> unusedSubGraphs = new HashSet<>();
	    List<String> remainingGraphs = getGraphsBySize(graphMap);
	    
	    remainingGraphs.remove(resource); // 2022-12-09: remove resource to avoid all subgraphs covered by the resource graph
	    
		LOG.debug("removeSubGraphsCoveredByContainingGraph: remainingGraph={} resource={}",  remainingGraphs, resource);

		while(!remainingGraphs.isEmpty()) {		
			String node = remainingGraphs.remove(0);
			
			LOG.debug("removeSubGraphsCoveredByContainingGraph: remainingGraph={} node={}",  remainingGraphs, node);

			if(node.contentEquals(resource)) continue;
			
			Set<Node> nodes = graphMap.get(node).vertexSet();
			
			LOG.debug("removeSubGraphsCoveredByContainingGraph: remainingGraph={} node={} vertexSet={}",  remainingGraphs, node, nodes);
			
			Predicate<Edge> isNotInheritanceFromAllResources = e -> e.isDiscriminator() && !allResources.contains(e.getRelated().getName());
			
			remainingGraphs.stream()
				.map(graphMap::get)
				.forEach(superior -> {
					boolean covered = graphMap.get(node).vertexSet().stream()
							.filter(n -> !allResources.contains(n.getName()))
							.allMatch(n -> superior.vertexSet().contains(n));
					
					covered = covered && graphMap.get(node).edgeSet().stream()
							.filter(isNotInheritanceFromAllResources)
							.allMatch(n -> superior.edgeSet().contains(n));
					
					Set<Node> nodesNotCovered = graphMap.get(node).vertexSet().stream().filter(n -> !allResources.contains(n.getName())).filter(n -> !superior.vertexSet().contains(n)).collect(toSet());
			
					LOG.debug("removeSubGraphsCoveredByContainingGraph: resource={} node={} nodesNotCovered={}", resource, node, nodesNotCovered);

					Set<Edge> edgesNotCovered = graphMap.get(node).edgeSet().stream()
							.filter(isNotInheritanceFromAllResources)
							.filter(e -> !superior.edgeSet().contains(e)).collect(toSet());

					LOG.debug("removeSubGraphsCoveredByContainingGraph: resource={} node={} edgesNotCovered={}", resource, node, edgesNotCovered);

					if(covered) {
						unusedSubGraphs.add(node);
						LOG.debug("removeSubGraphsCoveredByContainingGraph: resource={} node={} unusedSubGraphs={}", resource, node, unusedSubGraphs);					
					}
				});
		}
				
		LOG.debug("removeSubGraphsCoveredByContainingGraph: subgraphs to remove={}", unusedSubGraphs);
		
		unusedSubGraphs.forEach(graphMap::remove);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void removeSubGraphsCoveredByContainingGraph_old(Map<String, Graph<Node, Edge>> graphMap) {
		Set<String> unusedSubGraphs = new HashSet<>();
	    List<String> remainingGraphs = getGraphsBySize(graphMap);
	    
		while(!remainingGraphs.isEmpty()) {		
			String node = remainingGraphs.remove(0);
			
			Set<Node> nodes = graphMap.get(node).vertexSet();
			
			LOG.debug("removeSubGraphsCoveredByContainingGraph: remainingGraph={} node={} vertexSet={}",  remainingGraphs, node, nodes);
			
			remainingGraphs.stream()
				.map(graphMap::get)
				.forEach(superior -> {
					boolean covered = graphMap.get(node).vertexSet().stream().allMatch(n -> superior.vertexSet().contains(n));
					
					if(covered) {
						unusedSubGraphs.add(node);
						LOG.debug("removeSubGraphsCoveredByContainingGraph: node={} unusedSubGraphs={}", node, unusedSubGraphs);

					}
				});
		}
				
		LOG.debug("removeSubGraphsCoveredByContainingGraph: subgraphs to remove={}", unusedSubGraphs);
		
		unusedSubGraphs.forEach(graphMap::remove);
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private List<String> getGraphsBySize(Map<String, Graph<Node, Edge>> graphMap) {
	   return graphMap.entrySet().stream()
				.sorted((e1, e2) -> compareGraphSize(e1.getValue(), e2.getValue()))		
				.map(Map.Entry::getKey)
				.collect(toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void removeOrphans(Graph<Node, Edge> graphToPrune, Node subResource) {
		boolean removed=true;
		while(removed) {
			Set<Node> orphans = graphToPrune.vertexSet().stream()
									.filter(node -> graphToPrune.containsVertex(node) && graphToPrune.incomingEdgesOf(node).isEmpty())
									.collect(toSet());
			
			orphans.remove(subResource);
			
			graphToPrune.removeAllVertices(orphans);
			
			removed=!orphans.isEmpty();
		}
	}

	private int compareGraphSize(Graph<Node, Edge> graph1, Graph<Node, Edge> graph2) {
		Integer sizeGraph1 = Integer.valueOf(graph1.vertexSet().size());
		Integer sizeGraph2 = Integer.valueOf(graph2.vertexSet().size());
		return sizeGraph1.compareTo(sizeGraph2);

	}


	@LogMethod(level=LogLevel.DEBUG)
	private boolean isSimpleType(Node node) {
		String type = node.getName();
		return Utils.isSimpleType(type) && !APIModel.isEnumType(type);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void complexityAdjustedGraph(Graph<Node,Edge> graph, Node pivot) {

		Set<Node> simpleTypes = graph.vertexSet().stream()
									.filter(this::isSimpleType)
									.collect(toSet());
		
       	graph.removeAllVertices(simpleTypes);
      	
	    GraphComplexity analyser = new GraphComplexity(graph, pivot);
	    	    
	    analyser.computeGraphComplexity();

	    Set<Node> baseTypes = analyser.getBaseTypes();
	    Set<Node> nonComplexTypes = analyser.getNonComplexTypes();

    	Set<Node> invalidBaseTypes = baseTypes.stream()
										.filter(analyser::noComplexityContribution)
										.filter(baseNode -> getOutboundNeighbours(baseNode).size()<3)
										.collect(toSet());
    	
    	Set<Node> invalidNonComplexTypes = invalidBaseTypes.stream()
											.map(this::getNodesOfSubGraph)
											.flatMap(Set::stream)
											.collect(toSet());
    	
    	baseTypes.removeAll(invalidBaseTypes);
    	nonComplexTypes.removeAll(invalidNonComplexTypes);
      	      	
      	Set<Node> nodesWithoutOutboundEdges = Utils.union( baseTypes, nonComplexTypes );
      	for(Node node : nodesWithoutOutboundEdges ) {
      		if(!node.equals(pivot) && baseTypes.contains(node)) {
      			Set<Edge> edgesToRemove = graph.outgoingEdgesOf(node);
      			
      			LOG.debug("complexityAdjustedGraph:: remove edges={}",  edgesToRemove.stream().map(Object::toString).collect(Collectors.joining("\n")));
      			
      			graph.removeAllEdges(edgesToRemove);
      		}
      	}
      	      	
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean isPivot(Node node, Node pivot) {
		return node.equals(pivot);	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private boolean isIndependent(Graph<Node,Edge> graph, Node node) {
		return graph.outgoingEdgesOf(node).isEmpty() && graph.incomingEdgesOf(node).isEmpty();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getOutboundNeighbours(Node node) {
		return CoreAPIGraph.getOutboundNeighbours(graph.getCompleteGraph(), node);	
	}

	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getNodesOfSubGraph(Node node) {
		return CoreAPIGraph.getNodesOfSubGraph(graph.getCompleteGraph(), node);	
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isComplete(Graph<Node,Edge> graph, Node node) {
		for(Property property : node.getProperties() ) {
			if(!property.isSimpleType() || property.isEnum()) {
				String type = property.getType();
				Optional<Node> typeNode = CoreAPIGraph.getNodeByName(graph, type);
				
				if(!typeNode.isPresent()) return false;
				if(graph.getAllEdges(node, typeNode.get()).isEmpty()) return false;
				
			}
		}
		return true;
 	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isCompleteCandidate(Graph<Node,Edge> graph, Node node) {
		for(Property property : node.getProperties() ) {
			if(!property.isSimpleType()) {
				String type = property.getType();
				Optional<Node> typeNode = CoreAPIGraph.getNodeByName(graph, type);
				
				if(!typeNode.isPresent()) return false;
				
			}
		}
		return true;
 	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Map<String,Graph<Node,Edge>> getGraphsForResource(String resource) {
		if(allGraphs.containsKey(resource)) {
			return allGraphs.get(resource);
		} else {
			return new HashMap<>(); 
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<String> getSubGraphLabels(String resource) {
		List<String> res = new LinkedList<>();
		
		LOG.debug("getSubGraphLabels:: resource={} allGraphs={}",  resource, allGraphs.keySet());
		LOG.debug("getSubGraphLabels:: resource={} graph={}",  resource, allGraphs.get(resource));

		if(allGraphs.containsKey(resource)) res.addAll( allGraphs.get(resource).keySet() );
		
		LOG.debug("getSubGraphLabels:: resource={} allGraphs={}",  resource, allGraphs);

		LOG.debug("getSubGraphLabels:: resource={} res={}",  resource, res);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Optional<Graph<Node, Edge>> getSubGraphOptional(String resource, String pivot) {
		Optional<Graph<Node,Edge>> res = Optional.empty();
		
		if(allGraphs.containsKey(resource) && allGraphs.get(resource).containsKey(pivot)) {
			res = Optional.of( allGraphs.get(resource).get(pivot) );
		}
			
		LOG.debug("getSubGraph:: resource={} pivot={} res={}",  resource, pivot, res);
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Graph<Node, Edge> getSubGraphV2(String resource, String pivot) {
		Graph<Node,Edge> res = null;
		
		if(allGraphs.containsKey(resource) && allGraphs.get(resource).containsKey(pivot)) {
			res = allGraphs.get(resource).get(pivot);
		}
			
		LOG.debug("getSubGraph:: resource={} pivot={} res={}",  resource, pivot, res);
		
		return res;
	}

	public void generateSubGraphsFromConfig(List<String> allResources, String resource, List<String> subGraphsForResource) {
	    LOG.debug("### generateSubGraphsFromConfig: resource=" + resource);

	    Node resourceNode = graph.getNode(resource);
	    
	    if(resourceNode==null) {
	    	Out.printAlways("... specified resource '" + resource + "' not found in API - no further processing");
	    	return;
	    }
	    
	    Graph<Node,Edge> resourceGraph = CoreAPIGraph.getSubGraphWithInheritance(allResources, graph.getCompleteGraph(), resourceNode, resourceNode);

		LOG.debug("generateSubGraphsFromConfig:: resource={} resourceGraph={}",  resource, resourceGraph.edgeSet());
	    
	    Map<String,Graph<Node,Edge>> graphMap = createGraphsForSubResource(allResources, resourceGraph, resourceNode, subGraphsForResource);

		LOG.debug("generateSubGraphsFromConfig:: resource={} graphMap={} subGraphsForResource={}",  resource, graphMap.keySet(), subGraphsForResource);

	    this.allGraphs = adjustSubGraphs(allResources, resourceNode, resourceGraph, graphMap);
		
	}
	
	
    private Map<String, Map<String,Graph<Node,Edge>>> adjustSubGraphs(Collection<String> allResources, Node resourceNode, Graph<Node, Edge> resourceGraph, Map<String, Graph<Node, Edge>> graphMap) {
    	
	    LOG.debug("##1 adjustSubGraphs: resource={} vertexSet={}" , resourceNode, resourceGraph.vertexSet() );
	    LOG.debug("##1 adjustSubGraphs: resource={} edges={}" , resourceNode, resourceGraph.edgeSet() );

	    LOG.debug("## adjustSubGraphs: resource={} graphMap={}" , resourceNode.getName(), graphMap.keySet() );

    	Map<String, Map<String,Graph<Node,Edge>>> res = new HashMap<>();
    	
	    String resource = resourceNode.getName();
	    
	    Map<String,Graph<Node,Edge>> mappingGraphs = addMissingMappedResources(allResources, graph.getCompleteGraph(), resourceNode, resourceGraph.vertexSet());

	    LOG.debug("## adjustSubGraphs: resource={} mappingGraphs={}" , resource, mappingGraphs.keySet() );

		removeSubGraphsCoveredByContainingGraph(allResources, resource, mappingGraphs);

	    LOG.debug("## adjustSubGraphs: resource={} mappingGraphs={}" , resource, mappingGraphs.keySet() );

	    for(String key : mappingGraphs.keySet() ) {
	    	graphMap.put(key,mappingGraphs.get(key));
	    }
	    	    	    
	    res.put(resource, graphMap);
	    
	    for(String key : graphMap.keySet()) { LOG.debug(" before prune ... {} :: {}", key, graphMap.get(key).vertexSet()); }

	    pruneSubGraphsFromContainingGraphs(resource, graphMap);

	    for(String key : graphMap.keySet()) { LOG.debug(" before remove ... {} :: {}", key, graphMap.get(key).vertexSet()); }

	    removeSubGraphsCoveredByContainingGraph(allResources, resource, graphMap);
	    
	    for(String key : graphMap.keySet()) { LOG.debug(" after remove ... {} :: {}", key, graphMap.get(key).edgeSet().stream().map(Object::toString).collect(Collectors.joining("\n...")) ); }
	    
	    removeDisjointSubgraphs(allResources, resource, graphMap);

	    addRemovedRelationshipsWhenDiscriminator(graph.getCompleteGraph(), graphMap);

	    removeSubGraphNotReferenced(resource, graphMap);
	    
	    res.put(resource, graphMap);
    
	    LOG.debug("adjustSubGraphs: #2 resource={} final graphMap={}", resource, graphMap.keySet());
	    
	    for(String key : graphMap.keySet()) { LOG.debug(" ... {} :: \n...{}", key, graphMap.get(key).vertexSet().stream().map(Node::getName).collect(Collectors.joining("\n...")) ); }
	    
	    return res;
	    
	}

	private void removeDisjointSubgraphs(Collection<String> allResources, String resource, Map<String, Graph<Node, Edge>> graphMap) {
		for(String map : graphMap.keySet()) {
			
			Graph<Node, Edge> g = graphMap.get(map);
			Set<Node> reachable = CoreAPIGraph.getReachableNew(g, map);
			Set<Node> nonReachable = new HashSet<>( g.vertexSet() );
			nonReachable.removeAll(reachable);
		   
			LOG.debug("removeDisjointSubgraphs: resource={} map={} reachable={} nonReachable={}", resource, map, reachable, nonReachable);
			LOG.debug("removeDisjointSubgraphs: resource={} map={} nonReachable={}", resource, map, nonReachable);
			
			g.removeAllVertices(nonReachable);

		}
	}

	private void removeSubGraphNotReferenced(String resource, Map<String, Graph<Node, Edge>> graphMap) {
		
		Set<String> notSeen = new HashSet<>();
		
		for(String node : graphMap.keySet()) {
			
			Predicate<String> notNode = n -> !n.contentEquals(node);
			Set<String> otherGraphs = graphMap.keySet().stream().filter(notNode).collect(toSet());

			Set<String> seen = otherGraphs.stream()
								.map(graphMap::get)
								.map(Graph<Node, Edge>::vertexSet)
								.flatMap(Set::stream)
								.map(Node::getName)
								.collect(toSet());
			
			if(!seen.contains(node)) notSeen.add(node);
			
		}

		notSeen.remove(resource);
		
		if(!notSeen.isEmpty()) LOG.debug("removeSubGraphNotReferenced: resource={} notSeen={}", resource, notSeen);
		
		if(!Config.getBoolean("doNotRemoverSubGraphNotReferenced")) {
			// notSeen.forEach(graphMap::remove);
		}

	}

	private void addRemovedRelationshipsWhenDiscriminator(Graph<Node, Edge> completeGraph, Map<String, Graph<Node, Edge>> graphMap) {
		for(String key : graphMap.keySet()) {
			Graph<Node, Edge> g = graphMap.get(key);
			Set<Edge> discriminatorEdges = g.edgeSet().stream().filter(Edge::isDiscriminator).collect(toSet());
			
			LOG.debug("addRemovedRelationshipsWhenDiscriminator: node={} discriminatorEdges={}", key, discriminatorEdges);
			
			for(Edge edge : discriminatorEdges) {
				Node nodeA=g.getEdgeSource(edge);
				Node nodeB=g.getEdgeTarget(edge);
			
				Set<Edge> edges = completeGraph.getAllEdges(nodeB, nodeA).stream().filter(e -> !g.edgeSet().contains(e)).collect(toSet());
			
				if(edges.isEmpty()) continue;
				
				LOG.debug("addRemovedRelationshipsWhenDiscriminator: key={} nodeA={} nodeB={} edges={}", key, nodeA, nodeB, edges);

				edges.forEach(e -> g.addEdge(nodeB, nodeA, e));
				
			}
			
		}
		
	}

	private Map<String,Graph<Node,Edge>> createGraphsForSubResource(Collection<String> allResources, Graph<Node, Edge> resourceGraph, Node resourceNode, List<String> subGraphsForResource) {
    
	    boolean isSimplified = simplifyGraphForComplexDiscriminators(resourceGraph, resourceNode, allResources);
	        
	    LOG.debug("createGraphsForSubResource: resource={} isSimplified={}" , resourceNode, isSimplified);

	    Map<String,Graph<Node,Edge>> graphMap = new HashMap<>();
	    
	    if(!subGraphsForResource.contains(resourceNode.getName())) subGraphsForResource.add(0, resourceNode.getName());
	    
	    for(String subResource : subGraphsForResource ) {
	    		
	    	Optional<Node> optNode = CoreAPIGraph.getNodeByName(resourceGraph,  subResource);
	    	
	    	if(optNode.isPresent()) {
	    		Node node = optNode.get();
	    		
	    		Graph<Node,Edge> subGraph = CoreAPIGraph.getSubGraphWithInheritance(allResources, resourceGraph, node, resourceNode);
	    			    		
	    		isSimplified = simplifyGraphForComplexDiscriminators(subGraph, node, allResources);
	    	     
	    	    LOG.debug("createGraphsForSubResource: resource={} isSimplified={}" , node, isSimplified);

	    		graphMap.put(node.getName(), subGraph);
	    	
	    	} else {
	    		Out.debug("... sub-resource {} not found", subResource);
	    	}
			
		}
	    
	    return graphMap;
    }
    
	
}

