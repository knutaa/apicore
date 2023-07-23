package no.paneon.api.graph.complexity;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Node;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.apache.logging.log4j.LogManager;

public class GraphComplexity {

    static final Logger LOG = LogManager.getLogger(GraphComplexity.class);

	Graph<Node,Edge> graph;
	Node resource;
	
	Map<Node,Integer> nodeComplexity;
	
	public GraphComplexity(Graph<Node,Edge> graph, Node resource) {
		this.graph = graph;	
		this.resource = resource;
		this.nodeComplexity = new TreeMap<>();

	}
	
	static final String REF_OR_VALUE = "RefOrValue";

	static final int PATH_LENGTH_THRESHOLD = 0;
	static final int MIN_COMPLEXITY = 100;
	static final int MIN_COMPLEXITY_HIGH = 200;

	static final int MAX_DIAGRAM_COMPLEXITY = 2000;
	static final int LOW_DIAGRAM_COMPLEXITY = 1000;

	@LogMethod(level=LogLevel.DEBUG)
	public Map<Node, Integer> computeGraphComplexity() {

		LOG.debug("computeGraphComplexity: nodes={}", this.graph.vertexSet());

		int minimum = Config.getInteger("minimum_complexity");
		if(minimum==0) minimum=Integer.MAX_VALUE;
				
		removeSimpleTypeNodes(this.resource);
		
		PathAlgorithms pathAlgs = new PathAlgorithms(this.graph, this.resource);
		
		Map<Node,Integer> shortestPath = pathAlgs.computeShortestPath();		
		Map<Node,Integer> longestPath = pathAlgs.computeLongestPath();
					
		LOG.debug("computeGraphComplexity:: shortestPath={}", shortestPath.keySet());

		Set<Node> nodes = graph.vertexSet();
		
		Set<String> mappings = nodes.stream().map(Node::getExternalDiscriminatorMapping).flatMap(Set::stream).collect(toSet());
		
		LOG.debug("computeGraphComplexity:: nodes={}", nodes);
		LOG.debug("computeGraphComplexity:: mappings={}", mappings);

		for(Node node : graph.vertexSet().stream().sorted().collect(toList())) {

			LOG.debug("computeGraphComplexity:: node={}", node );

			boolean filter = !shortestPath.containsKey(node);		
			LOG.debug("computeGraphComplexity:: node={} filter={}", node, filter );

			filter = filter || !longestPath.containsKey(node);
			LOG.debug("computeGraphComplexity:: node={} filter={}", node, filter );

			filter = filter || (!node.equals(resource) && isSimplePrefixGraph(graph,node));
			LOG.debug("computeGraphComplexity:: node={} filter={}", node, filter );
				
			if(filter) continue;
			
//			if(!shortestPath.containsKey(node) || 
//			   !longestPath.containsKey(node) ||
//			   (!node.equals(resource) && isSimplePrefixGraph(graph,node))) continue;

			LOG.debug("## computeGraphComplexity:: node={} isSimplePrefixGraph={}", node, isSimplePrefixGraph(graph,node));

			int complexityContribution = computeComplexityContribution(this.graph, node, shortestPath.get(node), longestPath.get(node));
			
			if(complexityContribution>minimum+MIN_COMPLEXITY || this.resource.equals(node)) nodeComplexity.put(node, complexityContribution);
					
			LOG.debug("computeGraphComplexity:: node={} complexityContribution={}", node, complexityContribution); // 06-2023

		}
				
		nodeComplexity = nodeComplexity.entrySet().stream()
							.sorted(Map.Entry.comparingByValue())
							.collect(Collectors.toMap(
								    Map.Entry::getKey, 
								    Map.Entry::getValue, 
								    (oldValue, newValue) -> oldValue, LinkedHashMap::new));
				
		LOG.debug("computeGraphComplexity: node={} nodeComplexity={}", this.resource, nodeComplexity);
		
//		if(nodeComplexity.containsKey(resource)) {
//			Integer comp = nodeComplexity.get(resource);
//			nodeComplexity.remove(resource);
//			nodeComplexity.put(resource, comp);
//		}
		
		return nodeComplexity;
	}
	
	private boolean isSimplePrefixGraph(Graph<Node, Edge> graph, Node node) {
		
		Set<Node> inbound = GraphAlgorithms.getInboundNeighbours(graph, node);
		Set<Node> outbound = GraphAlgorithms.getOutboundNeighbours(graph, node);

		Set<Node> outboundNonLeaf = outbound.stream()
										.filter(n -> !CoreAPIGraph.isLeafNode(graph,n))
										.collect(toSet());
		
		LOG.debug("isSimplePrefixGraph: node={} outboundNonLeaf={}", node, outboundNonLeaf);

		// outbound.removeAll(inbound); // TBD 05-2023
		
		Predicate<Node> isEnumNode = Node::isEnumNode;
		
		outbound = outbound.stream().filter(isEnumNode.negate()).collect(toSet());
				
		int discriminators = graph.outgoingEdgesOf(node).stream().filter(Edge::isDiscriminator).collect(Collectors.toSet()).size();
		
		boolean res = (outbound.size()<3) 
				|| (outboundNonLeaf.size()==1);

		res = res && discriminators==0;

		LOG.debug("isSimplePrefixGraph: node={} outbound.size={} outboundNonLeaf.size={} discriminators={}", node, outbound.size(), outboundNonLeaf.size(), discriminators);

		LOG.debug("isSimplePrefixGraph: node={} res={}", node, res);

		if(!Config.getBoolean("preVienna")&& node.getName().endsWith("RefOrValue")) res=false;
		
		boolean singleInheritanceOnly = graph.outgoingEdgesOf(node).stream().allMatch(Edge::isAllOf);
		if(!Config.getBoolean("preVienna")&& singleInheritanceOnly) res=true;
		
		return res;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public int getComplexity() {
		return nodeComplexity.values().stream().mapToInt(Integer::intValue).sum();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isAboveLowerLimit() {
		return getComplexity() > LOW_DIAGRAM_COMPLEXITY;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isAboveUpperLimit() {
		return getComplexity() > MAX_DIAGRAM_COMPLEXITY;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isAboveLowerLimit(int complexity) {
		return complexity > LOW_DIAGRAM_COMPLEXITY;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isAboveUpperLimit(int complexity) {
		return complexity > MAX_DIAGRAM_COMPLEXITY;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void removeSimpleTypeNodes(Node resource) {		
		Set<Node> simpleNodes = this.graph.vertexSet().stream()
									.filter(Node::isSimpleType)
									.collect(toSet());
		
		simpleNodes.remove(resource);
		
		LOG.debug("removeSimpleTypeNodes:: simpleNodes={}", simpleNodes);
		
		simpleNodes.forEach(this.graph::removeVertex);
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private int computeComplexityContribution(Graph<Node,Edge> graph, Node node, int shortest, int longest) {
		
		int complexityContribution = 0;
		
		if(shortest>PATH_LENGTH_THRESHOLD || node.equals(this.resource)) {
			
			LOG.debug("computeComplexityContribution: node={} edges={}",  node, graph.edgesOf(node));

			Set<Node> subGraph = CoreAPIGraph.getNodesOfSubGraph(graph, node);
			
			LOG.debug("computeComplexityContribution: node={} graph={} subGraph={}",  node, graph.vertexSet(), subGraph);

			Set<Node> inbound = CoreAPIGraph.getInboundNeighbours(graph, node);
			Set<Node> outbound = CoreAPIGraph.getOutboundNeighbours(graph, node);

			Set<Node> allInboundOutbound = Utils.union( inbound, outbound );					
			Set<Node> differenceInboundOutbound = Utils.difference( outbound, inbound );
			
			int discriminators = graph.outgoingEdgesOf(node).stream().filter(Edge::isDiscriminator).collect(Collectors.toSet()).size();
			
			LOG.debug("computeComplexityContribution: node={} discriminators={}",  node, discriminators);

			int degree = discriminators + graph.degreeOf(node);
			int pathComplexity = 1 + longest * shortest;
			int subGraphContribution = (subGraph.size()<4) ? 1 : subGraph.size()+1;
			int allEdgesContribution = allInboundOutbound.size() + discriminators;
			int differenceContribution = (degree<3) ? 1 : discriminators + differenceInboundOutbound.size();
						
			complexityContribution = pathComplexity * subGraphContribution * allEdgesContribution * differenceContribution;
					
			LOG.debug("computeComplexityContribution: node={} contrib={}",  node, complexityContribution);
			
		}
		
		complexityContribution = node.equals(this.resource) && complexityContribution==0 ? MAX_DIAGRAM_COMPLEXITY : complexityContribution;

		if(tooSmallGraph(node,graph)) complexityContribution=0;
		
		
		return complexityContribution;

	}	

	public static boolean tooSmallGraph(Node pivot, Graph<Node, Edge> graph) {
		boolean res=false;
		List<String> allResources = APIModel.getResources();
		if(!allResources.contains(pivot.getName())) {
			int minimumGraphSize = Config.getInteger("minimumGraphSize");
			res = graph.vertexSet().size()<=minimumGraphSize;
			if(!res) {
				int discriminatorLimit = Config.getInteger("minimumOnlyDiscriminators", 2);
				Set<Edge> edges = graph.outgoingEdgesOf(pivot);
				res = edges.size()<=discriminatorLimit && edges.stream().allMatch(Edge::isDiscriminator);
			}
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getNonComplexTypes() {

		Set<Node> res = getCandidateSimpleTypes();
		
		res = res.stream()
				.filter(node -> !nodeComplexity.containsKey(node) || nodeComplexity.get(node).intValue()<=MIN_COMPLEXITY)
				.collect(toSet());
						
		return res;

	}
	

	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getCandidateSimpleTypes() {

		Set<Node> res = new HashSet<>();
		
		Deque<Node> candidates = nodeComplexity.entrySet().stream()
									.sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
									.filter(item -> item.getValue() > MIN_COMPLEXITY)
									.sorted(Map.Entry.comparingByValue())
									.map(Map.Entry::getKey)
									.filter(item -> !item.equals(resource))
									.collect(Collectors.toCollection(ArrayDeque::new));

		int totalComplexity = computeTotalComplexity();
		
		LOG.debug("getCandidateSimpleTypes: candidates={} totalComplexity={}", candidates, totalComplexity);
		
		boolean done = (totalComplexity <= MAX_DIAGRAM_COMPLEXITY);
		
		while(!done && !candidates.isEmpty()) {

			Set<Node> processed = new HashSet<>();

			Node candidate = candidates.removeLast();
			
			Set<Node> subGraph = CoreAPIGraph.getNodesOfSubGraph(graph, candidate);

			LOG.debug("getCandidateSimpleTypes: processing candidate={} subGraph={}", candidate, subGraph );

			for(Node node : subGraph) {
				
				Set<Node> inboundsubGraph = CoreAPIGraph.getReverseSubGraph(graph, node);
				
				inboundsubGraph.removeAll(subGraph);
				inboundsubGraph.remove(candidate);
				inboundsubGraph.remove(this.resource);

				LOG.debug("getCandidateSimpleTypes: node={} inboundsubGraph={}", node, inboundsubGraph );

				int contribution = nodeComplexity.containsKey(node) ? nodeComplexity.get(node) : 0;

				if(!inboundsubGraph.isEmpty() && !processed.contains(node)) {
										
					totalComplexity = totalComplexity - contribution;
					
					res.add(node);
					
					done = (totalComplexity <= MAX_DIAGRAM_COMPLEXITY);

					LOG.debug("getCandidateSimpleTypes: node={} contribution={} processed={}",  node, contribution, processed);

				} 

				processed.add(node);

			}
					
		}
						
		return res;

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Node> getBaseTypes() {
		Set<Node> res = getCandidateSimpleTypes();
				
		res = res.stream()
				.filter(this::isAboveMinimumComplexity)
				.collect(toSet());

		if(res.size()==1) res.clear();
		
		if(Config.getSimplifyRefOrValue()) {
			Set<Node> refOrValue = getRefOrValueResources();
			refOrValue.removeAll(res);
			res.addAll( refOrValue); 
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private boolean isAboveMinimumComplexity(Node node) {
		int minimum = Config.getInteger("minimum_complexity");
		if(minimum==0) minimum=Integer.MAX_VALUE;
		// if(minimum==0) minimum=Integer.MAX_VALUE;

		return nodeComplexity.containsKey(node) && nodeComplexity.get(node).intValue()>minimum;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Set<Node> getRefOrValueResources() {
		Set<Node> res = new HashSet<>();
				
		if(Config.getSimplifyRefOrValue()) {

			for(Node node : graph.vertexSet()) {
				String refOrValueName = node.getName() + REF_OR_VALUE;
				Optional<Node> refOrValue = CoreAPIGraph.getNodeByName(graph,refOrValueName);
				if(refOrValue.isPresent()) res.add( refOrValue.get() );
			}
		
		}
		
		return res;
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public int computeTotalComplexity() {
		return nodeComplexity.entrySet().stream().mapToInt(Map.Entry::getValue).sum();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Map<Node, Integer> getNodeComplexity() {
		return nodeComplexity;
	}

	static final String NEWLINE = "\n";

	@LogMethod(level=LogLevel.DEBUG)
	public void getComments(StringBuilder res) {
		res.append(NEWLINE);
		res.append("' Overall complexity: " + computeTotalComplexity());
		res.append(NEWLINE);

		getNodeComplexity().entrySet().stream()
			.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
			.forEach(entry -> {
				res.append("' class " + entry.getKey() + " complexity: " + entry.getValue());
				res.append(NEWLINE);			
			});
		res.append(NEWLINE);	
	}

	@LogMethod(level=LogLevel.DEBUG)
	public int getContribution(String node) {
		Optional<Node> optNode = CoreAPIGraph.getNodeByName(graph,node);
		if(optNode.isPresent())
			return CoreAPIGraph.getSubGraphNodes(graph, optNode.get()).size()-1;
		else
			return 0;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean noComplexityContribution(Node node) {
		return nodeComplexity.containsKey(node) && 	nodeComplexity.get(node).intValue() == 0;
	}
	
}

