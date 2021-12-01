package no.paneon.api.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.Logger;
import org.aspectj.weaver.patterns.ThisOrTargetAnnotationPointcut;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.GraphIterator;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.utils.Utils;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;

public class CoreAPIGraph {

    static final Logger LOG = LogManager.getLogger(CoreAPIGraph.class);

    private Map<String, Node> graphNodes;
    private Map<String, EnumNode> enumNodes;
    private Map<String, List<String>> enumMapping;
		
    private static final String INHERITANCE = "coreInheritanceTypes"; 
    private static final String INHERITANCE_PATTERN = "coreInheritanceRegexp"; 

    static final String INCLUDE_INHERITED = "includeInherited"; 
    
    static final String INCLUDE_DISCRIMINATOR_MAPPING = "includeDiscriminatorMapping";
    static final String SET_DISCRIMINATOR_DEFAULT = "setDiscriminatorDefault";
    
    static final String REF = "$ref";
    
	Graph<Node,Edge> completeGraph;
	
	public CoreAPIGraph() {
		this.graphNodes = new HashMap<>();
		this.enumNodes = new HashMap<>();
		this.enumMapping = new HashMap<>();
		
		this.completeGraph = generateGraph();	
		
		updateNodeInheritance();
		
		updateDiscriminators();
		
		markRequiredDiscriminators();
		
		LOG.debug("CoreAPIGraph:: completeGraph={}", completeGraph);
		
//		final Node n = this.getNode("QuoteItem");
//		if(n != null) {
//			LOG.debug("CoreAPIGraph:: {} = {}", n, this.completeGraph.edgesOf(n).stream().filter(e -> this.completeGraph.getEdgeSource(e).equals(n)).collect(Collectors.toSet()));
//		}
//		
//		final Node m = this.getNode("ProductRefOrValue");
//		if(m != null) {
//			LOG.debug("CoreAPIGraph:: {} = {}", m, this.completeGraph.edgesOf(m).stream().filter(e -> this.completeGraph.getEdgeSource(e).equals(m)).collect(toSet()));
//		}
		
	}
	
	
	private void markRequiredDiscriminators() {
		Set<Edge> edges = this.completeGraph.edgeSet().stream().filter(Edge::isDiscriminator).collect(toSet());
		
		for(Edge edge : edges) {
			Node from = this.completeGraph.getEdgeSource(edge);
			Node to   = this.completeGraph.getEdgeTarget(edge);

			Set<Edge> allEdgesBetween = new HashSet<>();
			allEdgesBetween.addAll( this.completeGraph.getAllEdges(from, to).stream().collect(toSet()) );
			allEdgesBetween.addAll( this.completeGraph.getAllEdges(to, from).stream().collect(toSet()) );

			LOG.debug("### markRequiredDiscriminators: from={} to={} all={}", from, to, allEdgesBetween);

			Predicate<Edge> notDiscriminatorEdge = e -> !e.isDiscriminator();
			
			Optional<Edge> nonDiscriminatorExists = allEdgesBetween.stream().filter(notDiscriminatorEdge).findAny();
			
			if(nonDiscriminatorExists.isPresent()) {
				edge.setMarked(true);	
				nonDiscriminatorExists.get().setMarked(true);
			}
				
		}
		
	}



	private void updateDiscriminators() {
		Set<Node> nodes = this.completeGraph.vertexSet();
		
		LOG.debug("updateDiscriminators: nodes={} set-default={}", nodes, Config.getBoolean(SET_DISCRIMINATOR_DEFAULT));

		nodes.forEach(Node::updateDiscriminatorMapping);
		
		if(Config.getBoolean(SET_DISCRIMINATOR_DEFAULT)) {
			this.completeGraph.edgeSet().stream()
					.filter(Edge::isDiscriminator)
					.map(this.completeGraph::getEdgeTarget)
					.forEach(Node::setDiscriminatorDefault);
		}
		
	}

	private void updateNodeInheritance() {
		Set<Node> nodes = this.completeGraph.vertexSet();
		
		LOG.debug("updateNodeInheritance: nodes={} ", nodes);

		nodes.forEach(this::setNodeInheritance);
	}

	private void setNodeInheritance(Node node) {
		Set<Node> inheritsFrom = getOutboundEdges(this.completeGraph, node).stream()
									.filter(Edge::isAllOf)
									.map(this.completeGraph::getEdgeTarget)
									.filter(n -> !n.equals(node))
									.collect(toSet());
		
		Set<String> inheritance = inheritsFrom.stream().map(Node::getInheritance).flatMap(Set::stream).collect(toSet());
		
		inheritance.addAll( inheritsFrom.stream().map(Node::getName).collect(toSet()));
		
		LOG.debug("### setNodeInheritance: node={} inheritance={}", node, inheritance);
		
		node.inheritance = Optional.of(inheritance);
		
	}
	
	private Graph<Node, Edge> convertToUnidirectional(Graph<Node, Edge> subGraph) {
		Graph<Node,Edge> g = GraphTypeBuilder
				.<Node,Edge> undirected().allowingMultipleEdges(false).allowingSelfLoops(false)
				.edgeClass(Edge.class).buildGraph();

		subGraph.vertexSet().forEach(g::addVertex);
		
		subGraph.edgeSet().forEach(edge -> {
			Node source = subGraph.getEdgeSource(edge);
			Node target = subGraph.getEdgeTarget(edge);
			
			if(g.getEdge(source, target)!=null) {
				addGraphEdge(g, source, target);
			}
			
		});
		
		return g;
	}

	public CoreAPIGraph(CoreAPIGraph core) {
		this.graphNodes = core.graphNodes;
		this.enumNodes = core.enumNodes;
		this.enumMapping = core.enumMapping;
		
		this.completeGraph = core.completeGraph;
		
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	private Graph<Node,Edge> generateGraph() {

		Graph<Node,Edge> g = GraphTypeBuilder
								.<Node,Edge> directed().allowingMultipleEdges(true).allowingSelfLoops(true)
								.edgeClass(Edge.class).buildGraph();

		addNodesAndEnums(g);						
		addProperties(g);

		LOG.debug("generateGraph: g=" + g);
		
		Predicate<Node> isNotInline = n -> !n.getInline().isBlank();
		
		Set<Node> inlineNodes = g.vertexSet().stream().filter(isNotInline).collect(toSet());
		
		LOG.debug("generateGraph: inline nodes=" + inlineNodes);

		Set<Edge> outgoingFromInline = inlineNodes.stream().map(g::outgoingEdgesOf).flatMap(Set::stream).collect(toSet());
		
		g.removeAllEdges(outgoingFromInline);
		
		return g;
			
	}
	 
	@LogMethod(level=LogLevel.DEBUG)
	private void addNodesAndEnums(Graph<Node, Edge> g) {
		APIModel.getAllDefinitions().forEach(node -> getOrAddNode(g,node));
		
		LOG.debug("addNodesAndEnums: nodes={}", g.vertexSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Node getOrAddNode(Graph<Node, Edge> g, String definition) {		
		Node node;
		String coreDefinition = APIModel.removePrefix(definition);
		
		Optional<Node> candidate = getNodeByName(g,definition);
				
		if(candidate.isEmpty()) {
			node = APIModel.isEnumType(definition) ? new EnumNode(coreDefinition) : new Node(coreDefinition);
			g.addVertex(node);
			graphNodes.put(coreDefinition, node);
			
			if(node instanceof EnumNode) {
				addEnumNode((EnumNode) node);
			} else {
				addProperties(g, node.getName());
			}
			
//			if(node.getLocalDiscriminators().size()>1) {
//				LOG.debug("getOrAddNode:: non empty local discriminator node={}", node);
//				DiscriminatorNode discriminator = new DiscriminatorNode(node.getName() + "_discriminator");
//				g.addVertex(discriminator);
//				g.addEdge(node, discriminator, new Discriminator(node,discriminator));
//
//				node.getLocalDiscriminators().stream()
//						.filter(label -> !label.contentEquals(node.getName()))
//						.forEach(label -> {
//							Node to = getOrAddNode(g, label);
//							g.addEdge(discriminator, to, new Discriminator(discriminator, to));
//						});
//			}
			
			if(node.getLocalDiscriminators().size()>1) {
				LOG.debug("getOrAddNode:: non empty local discriminator node={}", node);
				node.getLocalDiscriminators().stream()
						.filter(label -> !label.contentEquals(node.getName()))
						.forEach(label -> {
							Node to = getOrAddNode(g, label);
							g.addEdge(node, to, new Discriminator(node, to));
						});
			}
			
			LOG.debug("addNode:: adding node={}", definition);
			
		} else {
			node = candidate.get();
		}
		
		return node;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addProperties(Graph<Node, Edge> g) {
		for(String definition : APIModel.getAllDefinitions() ) {
			addProperties(g,definition);
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addProperties(Graph<Node, Edge> g, String definition) {
	
		String coreDefinition = APIModel.removePrefix(definition);

		Node node = getOrAddNode(g,coreDefinition);

		LOG.debug("addProperties: coreDefinition={} node={}", coreDefinition, node);

		JSONObject properties = APIModel.getPropertyObjectForResource(definition);
		addProperties(g, node, definition, properties);			
		
		LOG.debug("addProperties: g={}", g);

		JSONArray allOfs = APIModel.getAllOfForResource(definition);
		
		allOfs.forEach(allOf -> {
			if(allOf instanceof JSONObject) {
				JSONObject allOfObject = (JSONObject) allOf;
				
				LOG.debug("addProperties:: node={} allOfObject={}", node, allOfObject);

				if(allOfObject.has(REF)) {
					processAllOfReference(g, allOfObject, node);					
				} else {
					JSONObject obj = APIModel.getPropertyObjectBySchemaObject(allOfObject);
					LOG.debug("addProperties:: allOf: resource={} obj={}", definition, obj);

					if(obj!=null) {
						String type = APIModel.getTypeName(obj);

						// if(type==null || type.isEmpty()) type=definition;
						
						LOG.debug("addProperties:: allOf: resource={} type={} obj={}", definition, type, obj);
						
						addProperties(g, node, type, obj);		
							
					} else {
						
						Out.printAlways("addProperties:: NOT PROCESSED: resource={} allOfObject={}", definition, allOfObject.toString(2));

					}
				}				
			}
		});
			
		addEdgesForInheritedEnums(g, node);
			
	}	


//	private void addInheritedDiscriminator(Graph<Node, Edge> g, Node node) {
//		boolean includeDiscriminatorMapping = Config.getBoolean(INCLUDE_DISCRIMINATOR_MAPPING);
//		if(includeDiscriminatorMapping) {
//			
//			Set<String> inherited = node.getInheritedDiscriminatorMapping();
//	
//			LOG.debug("addInheritedDiscriminator:: node={} inherited={}", node, inherited);	
//			LOG.debug("addInheritedDiscriminator:: node={} local={}", node, node.getLocalDiscriminators() );	
//
//			if(inherited.contains(node.getName())) {
//	
//				if(node.getLocalDiscriminators().isEmpty()) {
//					node.clearInheritedDiscriminatorMapping();	
//					Set<String> local = new HashSet<>();
//					local.add(node.getName());
//					node.setInheritedDiscriminatorMapping(local);
//				}
//				
//			}
//			
//			for(String discriminator : node.getExternalDiscriminatorMapping()) {		
//				
//				LOG.debug("addInheritedDiscriminator:: node={} discriminator={}", node, discriminator);	
//
//				Node to = getOrAddNode(g, discriminator);
//					
//				if(to.equals(node)) continue;
//				
//				boolean allOfReverse = g.getAllEdges(node, to).stream().anyMatch(Edge::isAllOf);
//				
//				LOG.debug("addInheritedDiscriminator:: node={} to={} allOfReverse={}", node, to, allOfReverse);	
//				
//				if(allOfReverse) {
//					continue;
//				}
//				
////					Set<Edge> edges = g.getAllEdges(node, to).stream().collect(toSet());
////					edges.addAll( g.getAllEdges(to,  node).stream().collect(toSet()) );
////					
////					LOG.debug("addInheritedDiscriminator:: node={} to={} edges={}", node, to, edges);	
//
//								
//				Predicate<Edge> isEdgeWithToNode = e -> g.getEdgeTarget(e).equals(to);
//				
//				boolean discriminatorExists = g.edgesOf(node).stream().filter(Edge::isDiscriminator).anyMatch(isEdgeWithToNode);
//				
//				if(!discriminatorExists) {
//					Edge edge = new Discriminator(node, to);
//			
//					addGraphEdge(g, node, to, edge);		
//		
//					LOG.debug("addInheritedDiscriminator:: adding edge={}", edge);	
//				}
//				
//				boolean allOf = g.getAllEdges(to, node).stream().anyMatch(Edge::isAllOf);
//
//				if(allOf) {
//					// node.clearInheritedDiscriminatorMapping();
//				}
//				
//				
//			}
//			
//			Set<String> inheritedDiscriminators = node.getExternalDiscriminatorMapping(); // getInheritedDiscriminator(g,node); 
//			
//			if(inheritedDiscriminators.contains(node.getName()) && !node.getName().contains("RefOrValue")) {
//				inheritedDiscriminators.clear();
//				inheritedDiscriminators.add(node.getName());
//			}
//			
//			LOG.debug("addInheritedDiscriminator:: node={} inheritedDiscriminators={}", node, inheritedDiscriminators);	
//
//			// node.setInheritedDiscriminatorMapping(inheritedDiscriminators);
//			
//			for(String discriminator : inheritedDiscriminators) {					
//				Node to = getOrAddNode(g, discriminator);
//	
//				if(to.equals(node)) continue;
//				
//				Predicate<Edge> isEdgeWithToNode = e -> g.getEdgeTarget(e).equals(to);
//				
//				boolean discriminatorExists = g.edgesOf(node).stream().filter(Edge::isDiscriminator).anyMatch(isEdgeWithToNode);
//				
//				if(!discriminatorExists) {
//					Edge edge = new Discriminator(node, to);
//					addGraphEdge(g, node, to, edge);		
//					LOG.debug("addInheritedDiscriminator:: adding discriminator edge={}", edge);	
//				}
//				
////				boolean hasDiscriminatorEdge = g.getAllEdges(node,to).stream().anyMatch(Edge::isDiscriminator);
////				if(!hasDiscriminatorEdge) {
////					Edge edge = new Discriminator(node, to);
////					addGraphEdge(g, node, to, edge);		
////					LOG.debug("addProperties:: adding inherited discriminator discriminator={} edge={}", discriminator, edge);	
////				}
//			}
//			
//			
//		}		
//	}

	private void addEdgesForInheritedEnums(Graph<Node, Edge> g, Node node) {
		if(!node.getEnums().isEmpty()) LOG.debug("addProperties: node {} enums {}",  node, node.getEnums());

		Set<Node> presentEnumNodes = g.edgesOf(node).stream().filter(Edge::isEnumEdge).map(g::getEdgeTarget).collect(toSet());
		
		if(!presentEnumNodes.isEmpty()) LOG.debug("addProperties: node {} enumEdges {}",  node, presentEnumNodes);

		Predicate<Edge> notPresentEnumNode = e -> g.getEdgeTarget(e)!=null && !presentEnumNodes.contains(g.getEdgeTarget(e));
		
		Set<Edge> inheritedEnumEdgesNotPresent = node.getInheritance().stream()
								.map(this::getNode)
								.filter(n -> n!=null)
								.map(g::edgesOf).flatMap(Set::stream)
								.filter( Edge::isEnumEdge )
								.filter( notPresentEnumNode )
								.collect(toSet());
							
		if(!inheritedEnumEdgesNotPresent.isEmpty()) LOG.debug("addProperties: node {} inheritedEnumEdgesNotPresent {}",  node, inheritedEnumEdgesNotPresent);

		for(Edge enumEdgeToAdd : inheritedEnumEdgesNotPresent) {
						
			Edge edge = new EdgeEnum(node, enumEdgeToAdd.getRelationship(), enumEdgeToAdd.getRelated(), enumEdgeToAdd.cardinality, enumEdgeToAdd.required );
			
			addGraphEdge(g, node, enumEdgeToAdd.getRelated(), edge);		
			LOG.debug("addProperties:: adding inherited enum {} edge={}", enumEdgeToAdd, edge);	
			
		}		
	}

	private Set<String> getInheritedDiscriminator(Graph<Node, Edge> g, Node node) {
		Set<String> res = new HashSet<>();
		
//		Set<Node> allOfNeighbours = g.outgoingEdgesOf(node).stream().filter(Edge::isAllOf).map(g::getEdgeTarget).collect(toSet());
//		
//		Set<String> inherited = allOfNeighbours.stream().map(Node::getInheritedDiscriminatorMapping).flatMap(Set::stream).collect(toSet());
//		
//		res.addAll(inherited);
//		
//		inherited = allOfNeighbours.stream().map(Node::getDiscriminatorMapping).flatMap(Set::stream).collect(toSet());
//
//		res.addAll(inherited );
		
//		LOG.debug("getInheritedDiscriminator:: node={} inheritsFrom={}", node, node.getInheritance());	
//		
//		Set<String> superClasses = node.getInheritance();
//
//		for(String superClass : superClasses) {
//			Node n = getOrAddNode(g,superClass);
//			res.addAll( n.getDiscriminatorMapping() );
//			res.addAll( getInheritedDiscriminator(g, n) );
//		}
//				
//		LOG.debug("getInheritedDiscriminator:: node={} #1 res={}", node, res);	
//
//		Set<String> coreInheritanceTypes = Config.get("coreInheritanceTypes").stream().collect(toSet());
//
//		res = res.stream().filter(n -> !coreInheritanceTypes.contains(n)).collect(toSet());
//
//		for(String regexp : Config.get("coreInheritanceRegexp")) {
//			res = res.stream().filter(n -> !n.matches(regexp)).collect(toSet());
//		}
//		
//		res = res.stream().filter(n -> !coreInheritanceTypes.contains(n)).collect(toSet());
//
//		LOG.debug("getInheritedDiscriminator:: node={} #2 res={}", node, res);	
//
//		return res;
		
		res = node.getAllDiscriminatorMapping();
		
		LOG.debug("getInheritedDiscriminator:: node={} res={}", node, res);	
		
		return res;
		
		
	}

	private void processAllOfReference(Graph<Node, Edge> g, JSONObject allOfObject, Node node) {
		String type = APIModel.getTypeByReference(allOfObject.optString(REF));
		
		boolean flattenInheritance = isBasicInheritanceType(type) || isPatternInheritance(type);
		
		LOG.debug("processAllOfReference:: type={} node={}", type, node);	

		flattenInheritance = flattenInheritance && !APIModel.isEnumType(type);
		
		LOG.debug("processAllOfReference:: type={} node={} flattenInheritance={}", type, node, flattenInheritance);	

		if(flattenInheritance) {
			
			node.addInheritance(type);
			
			boolean includeInherited = Config.getBoolean(INCLUDE_INHERITED); 
			if(includeInherited) {
				JSONObject obj = APIModel.getDefinitionBySchemaObject(allOfObject);							
				
				Set<Property> propertiesBefore = new HashSet<>(node.getProperties());
				
				node.addAllOfObject(obj, Property.VISIBLE_INHERITED);
				
				addEnumsToGraph(g, node, propertiesBefore);				
			}
			
		} else {			
			Node to = getOrAddNode(g, type); 
			
			Predicate<Edge> isAllOfWithToNode = e -> g.getEdgeTarget(e).equals(to);
			
			boolean hasAllOfEdge = g.edgesOf(node).stream().anyMatch(isAllOfWithToNode);
			
			if(!hasAllOfEdge) {
				Edge edge = new AllOf(node, to);
				addGraphEdge(g, node, to, edge);		
				LOG.debug("processAllOfReference:: adding edge={}", edge);	
			}
			
		}
		
	}

	private void addEnumsToGraph(Graph<Node, Edge> g, Node node, Set<Property> propertiesBefore) {
		Set<Property> propertiesAdded = new HashSet<>(node.getProperties());
		propertiesAdded.removeAll(propertiesBefore);
		Set<Property> enumsAdded = propertiesAdded.stream().filter(Property::isEnum).collect(toSet()); 

		if(!enumsAdded.isEmpty()) {
			LOG.debug("adding enums for node={} enums={}",  node, enumsAdded); 
			enumsAdded.forEach(property -> {
				Node to = graphNodes.get(property.getType());
				Edge edge = new EdgeEnum(node, property.name, to, property.cardinality, property.required);
				addGraphEdge(g, node, to, edge);		
			});
		}		
	}

	private static boolean isBasicInheritanceType(String type) {
		final List<String> inheritance = Config.get(INHERITANCE);
		return inheritance.contains(type);
	}

	private static boolean isPatternInheritance(String type) {
		final List<String> patterns = Config.get(INHERITANCE_PATTERN);
		return patterns.stream().anyMatch(pattern -> type.matches(pattern));
	}
	
	public static boolean isPatternInheritance(Node type) {
		final List<String> patterns = Config.get(INHERITANCE_PATTERN);
		return patterns.stream().anyMatch(pattern -> type.getName().matches(pattern));
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addProperties(Graph<Node, Edge> graph, Node from, String typeName, JSONObject properties) {
		
		LOG.debug("addProperties: typeName={} properties={}", typeName, properties.keySet());

		// Set<String> existingProperties = from.getProperties().stream().map(Property::getName).collect(toSet());
		
		// if("QuoteItem".contentEquals(typeName)) LOG.debug("addProperties: from={} existingProperties={}", from, existingProperties);

		// Set<String> newProperties = properties.keySet(); // .stream().filter(p -> !existingProperties.contains(p)).collect(toSet());
		
		for(String propertyName : properties.keySet()) {
						
			JSONObject property = properties.getJSONObject(propertyName);
			String type = APIModel.getTypeName(property);

			String coreType = APIModel.removePrefix(type);

			LOG.debug("addProperties: from={} propertyName={} type={} coreType={} property={}", from, propertyName, type, coreType, property);
			LOG.debug("addProperties: from={} propertyName={} type={} isSimpleType={}", from, propertyName, type, APIModel.isSimpleType(type));

			if(!APIModel.isSimpleType(type) || APIModel.isEnumType(type)) {
				
				Node to = getOrAddNode(graph, coreType);

				Set<Edge> existingEdgesToType = graph.edgesOf(from);
				
				LOG.debug("addProperties: from={} type={} to={} existingEdgesToType={}", from, coreType, to, existingEdgesToType);

				existingEdgesToType = existingEdgesToType.stream().filter(e -> e.getRelated().getName().contentEquals(to.getName())).collect(toSet());

				LOG.debug("addProperties: from={} type={} to={} existingEdgesToType={}", from, coreType, to, existingEdgesToType);

				boolean existingEdgesToProperty = existingEdgesToType.stream().anyMatch( e -> e.getRelationship().contentEquals(propertyName));
								
				LOG.debug("addProperties: from={} type={} to={} existingEdgesToProperty={}", from, coreType, to, existingEdgesToProperty);

				if(existingEdgesToProperty) continue;

				boolean isRequired = APIModel.isRequired(typeName, propertyName);
				String cardinality = APIModel.getCardinality(property, isRequired);

				Edge edge = APIModel.isEnumType(type) ? 
								new EdgeEnum(from, propertyName, to, cardinality, isRequired) :
								new Edge(from, propertyName, to, cardinality, isRequired);

				LOG.debug("addProperties: edge={}", edge);
			
				addGraphEdge(graph, from, to, edge);

			} 
		}
		
		Set<Edge> edgesOfFromNode = graph.edgesOf(from);
				
		LOG.debug("addProperties: #1 from={} edgesOfFromNode={}", from, edgesOfFromNode.stream().filter(e -> graph.getEdgeSource(e).equals(from)).map(graph::getEdgeTarget).collect(toList()));

	}
	
	private void addGraphEdge(Graph<Node, Edge> graph, Node from, Node to) {
		
		LOG.debug("addEdge:: from={} to={}", from, to);
		
		graph.addEdge(from, to);
		
	}
	
	private void addGraphEdge(Graph<Node, Edge> graph, Node from, Node to, Edge edge) {
		
		LOG.debug("addEdge:: from={} to={} edge={}", from, to, edge);
		
		graph.addEdge(from, to, edge);		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Edge> getOutboundEdges(Graph<Node,Edge> graph, Node node) {
		Set<Edge> res = new HashSet<>();
		if(graph.vertexSet().contains(node)) {
			res.addAll(  graph.outgoingEdgesOf(node) );
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getOutboundNeighbours(Graph<Node,Edge> graph, Node node) {
		Set<Node> res = new HashSet<>();
		if(graph.vertexSet().contains(node)) {
			res.addAll(  graph.outgoingEdgesOf(node).stream().map(graph::getEdgeTarget).collect(toSet()) );
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getInboundNeighbours(Graph<Node,Edge> graph, Node node) {
		Set<Node> res = new HashSet<>();
		if(graph.vertexSet().contains(node)) {
			res.addAll( graph.incomingEdgesOf(node).stream().map(graph::getEdgeSource).collect(toSet()) );
			// res.addAll( graph.vertexSet().stream().filter(n -> graph.containsEdge(node, n)).collect(toSet()) ); // if not explicit incoming edge
		}
		
		LOG.debug("getInboundNeighbours: node={} res={}", node, res);
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getNeighbours(Graph<Node,Edge> graph, Node node) {
		Set<Node> res = getOutboundNeighbours(graph,node);		
		res.addAll( getInboundNeighbours(graph,node) );
				
		LOG.debug("getNeighbours: node={} res={} graph={}", node, res, graph.vertexSet());

		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<EnumNode> getEnumsForNode(Graph<Node,Edge> graph, Node node) {
		return getOutboundNeighbours(graph, node).stream()
				.filter(n -> n instanceof EnumNode)
				.map(n -> (EnumNode) n)
				.collect(toSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getNodeNames(Graph<Node,Edge> graph) {
		return graph.vertexSet().stream().map(Node::getName).collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getNodesOfSubGraph(Graph<Node,Edge> graph, Node node) {
		Set<Node> res = new HashSet<>();

//		Set<Node> allDiscriminators = graph.vertexSet().stream()
//											.map(graph::edgesOf).flatMap(Set::stream)
//											.filter(Edge::isDiscriminator)
//											.map(graph::getEdgeTarget).collect(toSet());
//		
//		LOG.debug("getNodesOfSubGraph:: node={} allDiscriminators={}", node, allDiscriminators);
		
		if(node==null) {
			Out.printAlways("node argument equal to null - not expected");
			return res;
		}	
		
		GraphIterator<Node, Edge> it = new BreadthFirstIterator<>(graph, node);

		while(it.hasNext() ) {
			Node cand = it.next();
			if(cand!=null) res.add(cand);
		}

		LOG.debug("getNodesOfSubGraph:: node={} #00 res={}", node, res);

		Set<Node> superClassOf = graph.incomingEdgesOf(node).stream().filter(edge -> edge instanceof AllOf).map(graph::getEdgeSource).collect(toSet());
		
		superClassOf.removeAll(res);
		
		superClassOf.forEach(superior -> res.addAll( getNodesOfSubGraph(graph, superior)));
		
		LOG.debug("getNodesOfSubGraph:: node={} #0 res={}", node, res);
		
		LOG.debug("getNodesOfSubGraph:: node={} enums={}", node, res.stream().filter(Node::isEnumNode).collect(toSet()));
		LOG.debug("getNodesOfSubGraph:: node={} allEnums={}", node, graph.vertexSet().stream().filter(Node::isEnumNode).collect(toSet()));

		Set<Node> discriminators = res.stream().map(graph::edgesOf).flatMap(Set::stream).filter(Edge::isDiscriminator).map(graph::getEdgeTarget).collect(toSet());

		LOG.debug("getNodesOfSubGraph:: node={} #00 discriminators={}", node, discriminators);

		// 2021-11-29 TBD discriminators.removeAll(res);
		
		LOG.debug("getNodesOfSubGraph:: node={} #0 discriminators={}", node, discriminators);

		LOG.debug("getNodesOfSubGraph:: node={} #1 discriminators={}", node, discriminators);
		
		LOG.debug("getNodesOfSubGraph:: node={} #1 res={}", node, res);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Graph<Node,Edge> getSubGraphWithInheritance(Graph<Node,Edge> origGraph, Node node, Node resource) {
						
		LOG.debug("getSubGraphWithInheritance: #000 node={} origGraph isDiscriminator=\n{}",  node, origGraph.edgeSet().stream().filter(Edge::isDiscriminator).map(Object::toString).collect(Collectors.joining("\n")));

		Set<Node> nodes = getNodesOfSubGraph(origGraph, node);
		
		Graph<Node,Edge> graph = new AsSubgraph<Node, Edge>(origGraph);

		LOG.debug("getSubGraphWithInheritance: #111 node={} graph isDiscriminator=\n{}",  node, graph.edgeSet().stream().filter(Edge::isDiscriminator).map(Object::toString).collect(Collectors.joining("\n")));

		LOG.debug("getSubGraphWithInheritance:: node={} nodes={} ", node, nodes);

		LOG.debug("getSubGraphWithInheritance:: node={} resource={} vertexSet={}", node, resource, graph.vertexSet());
		LOG.debug("getSubGraphWithInheritance:: node={} resource={} edgeSet={}", node, resource, graph.edgeSet());
		
		Set<Node> simpleTypes = graph.vertexSet().stream()
				.filter(Node::isSimpleType)
				.collect(toSet());
		
		LOG.debug("getSubGraph:: node={} resource={} simpleTypes={}", node, resource, simpleTypes);

		simpleTypes.remove(node);
		simpleTypes.remove(resource);
		
		nodes.removeAll(simpleTypes);

		LOG.debug("getSubGraphWithInheritance:: after remove simple nodes={}", nodes);

		Set<Node> excludeNodes = new HashSet<>();
		
		if(!node.equals(resource)) {
			Set<Node> inheritedBy = nodes.stream()
					.filter(n -> graph.getAllEdges(n, node)!=null)
					.filter(n -> graph.getAllEdges(n, node).stream().anyMatch(Edge::isInheritance))
					.filter(n -> CoreAPIGraph.isLeafNodeOrOnlyEnums(graph, n))
					.collect(toSet());

			LOG.debug("getSubGraphWithInheritance:: node={} resource={} nodes={}", node, resource, nodes);
			LOG.debug("getSubGraphWithInheritance:: node={} resource={} inheritedBy={}", node, resource, inheritedBy);
			
			if(!Config.getBoolean(INCLUDE_INHERITED)) {
				inheritedBy.clear();
			} else {
				excludeNodes.addAll(inheritedBy);
			}
			
			LOG.debug("getSubGraphWithInheritance:: node={} resource={} inheritedBy={} nodes={}", node, resource, inheritedBy, nodes);

		}
	
		LOG.debug("getSubGraphWithInheritance:: node={} #1 subGraph={}", node, graph.edgeSet().stream().filter(Edge::isDiscriminator).collect(toSet()));

		Predicate<Node> notEnumNode = n -> !n.isEnumNode();
	 	
		boolean remove=true;
		while(remove) {
			Set<Node> noInbound = graph.vertexSet().stream()
										.filter(n -> graph.incomingEdgesOf(n).isEmpty())
										.filter(n -> !n.equals(node))
										.filter(notEnumNode)
										.collect(toSet());
	
			LOG.debug("getSubGraphWithInheritance:: node={} NO inbund discriminator={}", node, noInbound);

			graph.removeAllVertices(noInbound);

//			Set<Node> onlyDiscriminator = graph.vertexSet().stream()
//										.filter(n -> graph.incomingEdgesOf(n).stream().allMatch(Edge::isDiscriminator))
//										.filter(n -> !n.equals(node))
//										.collect(toSet());
//			
//			onlyDiscriminator = onlyDiscriminator.stream()
//										.filter(n -> graph.outgoingEdgesOf(n).stream().noneMatch(Edge::isAllOf))
//										.filter(notEnumNode)
//										.collect(toSet());
//			
//			LOG.debug("getSubGraphWithInheritance:: node={} only inbound discriminator={}", node, onlyDiscriminator);
//	
//			graph.removeAllVertices(onlyDiscriminator);
			
			remove = !noInbound.isEmpty() ; // && !onlyDiscriminator.isEmpty();
			
		}

		if(node.equals(resource)) {
			Set<Node> oneOfs = nodes.stream()
					.filter(n -> graph.getAllEdges(n, node)!=null)
					.filter(n -> graph.getAllEdges(n, node).stream().anyMatch(Edge::isOneOf))
					.collect(toSet());

			LOG.debug("getSubGraphWithInheritance:: node={} oneOfs={}", node, oneOfs);

			excludeNodes.addAll(oneOfs);
			
			Set<Node> discriminators = nodes.stream()
					.filter(n -> graph.getAllEdges(n, node)!=null)
					.filter(n -> graph.getAllEdges(n, node).stream().anyMatch(Edge::isDiscriminator))
					.filter(n -> graph.getAllEdges(node, n).isEmpty())
					.collect(toSet());

			
			LOG.debug("getSubGraphWithInheritance:: #1 node={} discriminators={}", node, discriminators);
			
			discriminators = discriminators.stream()
								.filter(n -> graph.getAllEdges(n, node)!=null)
								.filter(n -> graph.getAllEdges(n, node).stream().anyMatch(Edge::isNotDiscriminator))
								.collect(toSet());
			
			// TBD excludeNodes.addAll(discriminators);

		}
		
		
		if(node.equals(resource)) {
			// removeIrrelevantDiscriminatorRelationships(graph,node);
		}

		excludeNodes = excludeNodes.stream().filter(notEnumNode).collect(toSet());
		
		LOG.debug("getSubGraphWithInheritance:: node={} excludeNodes={}", node, excludeNodes);

		nodes.removeAll(excludeNodes);

		LOG.debug("getSubGraphWithInheritance:: node={} nodes={}", node, nodes);

		Graph<Node,Edge> subGraph = new AsSubgraph<>(graph, nodes);
		
		LOG.debug("getSubGraphWithInheritance:: node={} subGraph edges={}", node, subGraph.edgeSet().stream().map(Object::toString).collect(Collectors.joining("\n")));

		Set<Node> excludedNodes = new HashSet<>();
		excludedNodes.add(node);
		
		// removeOutboundFromDiscriminatorMappingNodes(subGraph,excludedNodes);

		LOG.debug("getSubGraphWithInheritance:: node={} subGraph edges={}", node, subGraph.edgeSet());
		LOG.debug("getSubGraphWithInheritance:: after remove outbound edges={}", subGraph.edgeSet().stream().map(Object::toString).collect(Collectors.joining("\n")));

		Predicate<Node> noNeighbours = n -> CoreAPIGraph.getNeighbours(subGraph, n).isEmpty();		
		Predicate<Node> noInboundNeighbours = n -> CoreAPIGraph.getInboundNeighbours(subGraph, n).isEmpty();
		
		Set<Node> orphans = selectGraphNodes(subGraph,noNeighbours,node);		
		orphans.forEach(subGraph::removeVertex);
		
		LOG.debug("getSubGraphWithInheritance:: node={} orphans={}", node, orphans);
		LOG.debug("getSubGraphWithInheritance:: node={} subGraph={}", node, subGraph);
		
		LOG.debug("## getSubGraphWithInheritance:: node={} subGraph={}", node, 
				subGraph.vertexSet().stream().filter(n -> n.getName().contains("ProductRef")).collect(toSet()));

		remove=true;
		while(remove) {
			remove = false;
			
			Set<Node> reachable = CoreAPIGraph.getSubGraphNodes(graph, node); // TBD : or subGraph ?
			
			LOG.debug("getSubGraphWithInheritance:: node={} reachable={} ", node, reachable);

			Set<Node> allNodes = subGraph.vertexSet();
			Set<Node> unReachable = new HashSet<>(allNodes);
			unReachable.removeAll(reachable);
			
			unReachable.removeAll(excludeNodes);
			
			unReachable.remove(node);
			
			LOG.debug("getSubGraphWithInheritance:: node={} reachable={} ", node, reachable);
			LOG.debug("getSubGraphWithInheritance:: node={} unReachabl={} ", node, unReachable);
			
			if(!reachable.isEmpty() && !unReachable.isEmpty()) {
				remove=true;
				
				LOG.debug("getSubGraphWithInheritance:: node={} unReachable={} ", node, unReachable);

				unReachable.forEach(subGraph::removeVertex); 				
			}
			
		}
		
		
		LOG.debug("## getSubGraphWithInheritance:: node={} final subGraph={}", node, subGraph.vertexSet());

		return subGraph;
	}
	
	
	public static Set<Node> getReachable(Graph<Node, Edge> graph, String node) {
		Optional<Node> optNode = CoreAPIGraph.getNodeByName(graph, node);
		if(optNode.isPresent()) {
			return getReachable(graph, optNode.get(), new HashSet<>() );
		} else {
			return new HashSet<>();
		}
	}
	
	public static Set<Node> getReachable(Graph<Node, Edge> graph, Node node) {
		return getReachable(graph, node, new HashSet<>() );
	}

	private static Set<Node> getReachable(Graph<Node, Edge> graph, Node node, Set<Node> seen) {
		Set<Node> res = new HashSet<>();
		
		res.add(node);
		
		if(!seen.contains(node)) {		
			
			Set<String> mapped = node.getAllDiscriminatorMapping();
			Set<Node> mappedNodes = graph.vertexSet().stream().filter(n -> mapped.contains(n.getName())).collect(toSet());
			
			Set<Node> neighbours = CoreAPIGraph.getOutboundNeighbours(graph, node);
			
			neighbours.addAll(mappedNodes);
			neighbours.removeAll(seen);
			
			
			LOG.debug("getReachable:: node={} neighbours={}", node, neighbours);

			res.addAll(neighbours);
			
			seen.add(node);
			
			res.addAll( neighbours.stream().map( n -> getReachable(graph, n, seen)).flatMap(Set::stream).collect(toSet()) );
			
		}
		
		return res;
	}
	
	private static void removeIrrelevantDiscriminatorRelationships(Graph<Node, Edge> graph, Node node) {
		Set<String> discriminators = new HashSet<>(node.getAllDiscriminatorMapping());
		
		if(!discriminators.isEmpty()) {
			discriminators.removeAll( node.getInheritedDiscriminatorMapping() );
			
			if(discriminators.isEmpty()) {
				Set<Node> inheritsFrom = getAllSuperClasses(graph,node); 
			
				LOG.debug("## getSubGraphWithInheritance:: node={} inheritsFrom={}", node, inheritsFrom);
				
				discriminators = inheritsFrom.stream().map(Node::getDiscriminatorMapping).flatMap(Set::stream).collect(toSet());
				
				LOG.debug("### getSubGraphWithInheritance:: node={} discriminators={}", node, discriminators);
				
				discriminators.remove( node.getName());

				Set<Node> removeNodes = discriminators.stream()
											.map(n -> getNodeByName(graph,n) )
											.filter(Optional::isPresent)
											.map(Optional::get)
											.filter( n -> !inheritsFrom.contains(n))
											.collect(toSet());
				
				LOG.debug("### getSubGraphWithInheritance:: node={} remove nodes={}", node, removeNodes);

				graph.removeAllVertices(removeNodes);
				
				
			}
		}		
	}

	private static Set<Node> getAllSuperClasses(Graph<Node, Edge> graph, Node node) {
		return getAllSuperClasses(graph, node, new HashSet<>());
	}

	private static Set<Node> getAllSuperClasses(Graph<Node, Edge> graph, Node node,  Set<Node> seen ) {
		Set<Node> res = new HashSet<>();
		
		if(seen.contains(node)) {
			return res;
		} else {
			seen.add(node);
			
			Set<Node> superior = graph.outgoingEdgesOf(node).stream().filter(Edge::isAllOf).map(graph::getEdgeTarget).collect(toSet());
			
			res.addAll( superior);
			seen.addAll(superior);
			
			res.addAll( superior.stream().map( n -> getAllSuperClasses(graph,n,seen)).flatMap(Set::stream).collect(toSet()) );
			
		}
		

		return res;
	}
	
//	@LogMethod(level=LogLevel.DEBUG)
//	private static void removeOutboundFromDiscriminatorMappingNodes(Graph<Node, Edge> graph, Set<Node> excludedNodes) {
//				
//		LOG.debug("removeOutboundFromDiscriminatorMappingNodes: excludedNodes={}", excludedNodes);
//
//		Set<Edge> edgesToRemove = new HashSet<>();
//
//		for(Node node : graph.vertexSet()) {
//			Set<String> mapping = node.getExternalDiscriminatorMapping();
//			if(!mapping.isEmpty()) {
//				LOG.debug("removeOutboundFromDiscriminatorMappingNodes: node={} mapping={}", node.getName(), mapping);
//								
//				Set<Node> mappingNodes = mapping.stream()
//											.map(n -> CoreAPIGraph.getNodeByName(graph,n))
//											.filter(Optional::isPresent)
//											.map(Optional::get)
//											.collect(toSet());
//			
//				// mappingNodes.removeAll(currentGraphNodes);
//				
//				mappingNodes.removeAll(excludedNodes);
//				
//				LOG.debug("removeOutboundFromDiscriminatorMappingNodes: node={} mappingNodes={}", node, mappingNodes);
//
//				for(Node mappingNode : mappingNodes) {
//					
//					LOG.debug("removeOutboundFromDiscriminatorMappingNodes: node={} mappingNode={}", node, mappingNode);
//
//					Set<Edge> edges = graph.outgoingEdgesOf(mappingNode);
//					
//					LOG.debug("removeOutboundFromDiscriminatorMappingNodes: mappingNode={} #1 edges={}", mappingNode, edges);
//
//					edges = edges.stream()
//							.filter(edge -> !graph.getEdgeTarget(edge).equals(node))
//							.collect(toSet());
//					
//					boolean atLeastOneLargeSubGraph = edges.stream().anyMatch(edge -> !isSmallSubGraph(graph,mappingNode,edge));
//					
//					if(atLeastOneLargeSubGraph) {
//						LOG.debug("removeOutboundFromDiscriminatorMappingNodes: mappingNode={} edges={}", mappingNode, edges);
//						edgesToRemove.addAll( edges );
//					}
//					
//				}
//				
//			}
//		}
//		
//		LOG.debug("removeOutboundFromDiscriminatorMappingNodes: nodes={}", graph.vertexSet());
//		LOG.debug("removeOutboundFromDiscriminatorMappingNodes: edgesToRemove={}", edgesToRemove);
//		LOG.debug("");
//
//		graph.removeAllEdges(edgesToRemove);
//
//	}

	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isSmallSubGraph(Graph<Node, Edge> graph, Node node, Edge edge) {
		Node target = graph.getEdgeTarget(edge);
		
		Set<Node> subGraph = CoreAPIGraph.getNodesOfSubGraph(graph, target);
		
		subGraph.remove(node);
		
		LOG.debug("isSimpleSubGraph: node={} edge={} subGraph={}", node, edge, subGraph);
		
		return subGraph.size()<=3;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> selectGraphNodes(Graph<Node, Edge> g, Predicate<Node> condition, Node exclude) {
		
		LOG.debug("selectGraphNodes: g={} condition={} exclude={}", g.vertexSet(), condition, exclude);

		Predicate<Node> notEqual = n -> !n.equals(exclude);
		return g.vertexSet().stream().filter(condition).filter(notEqual).collect(toSet());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> selectGraphNodes(Graph<Node, Edge> g, Predicate<Node> condition) {
		return g.vertexSet().stream().filter(condition).collect(toSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Map<Node,Graph<Node,Edge>> getSubGraphsOfNeighbours(Graph<Node,Edge> graph, Node node) {	
		Map<Node,Graph<Node,Edge>> res = new HashMap<>();
		
		Set<Node> neighbours = getOutboundNeighbours(graph,node);
			
		for(Node neighbour : neighbours) {
			Set<Node> sub = getNodesOfSubGraph(graph, neighbour);
			Graph<Node,Edge> subGraph = new AsSubgraph<>(graph, sub);
			res.put(node,  subGraph);
		}
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Node addNode(String node) {
		return graphNodes.get(node);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<String> getNodes() {
		return getNodeNames(completeGraph);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addEnumNode(EnumNode enumNode) {
		this.enumNodes.put(enumNode.getName(), enumNode);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Node getNode(String node) {
		return this.graphNodes.get(node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Optional<Node> getNodeByName(Graph<Node,Edge> graph, String name) {
		return graph.vertexSet().stream().filter(gn -> gn.getName().contentEquals(name)).findFirst();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Map<String, EnumNode> getEnumNodes() {
		return this.enumNodes;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public EnumNode getEnumNode(String name) {
		return this.enumNodes.get(name);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public List<Node> getNodesByNames(Collection<String> nodes) {
		nodes.retainAll( getNodes() );
		return graphNodes.values().stream().filter(node -> nodes.contains( node.getName())).collect(toList());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getSubGraphNodes(Graph<Node,Edge> graph, Node node) {
		return getSubGraphByParent(graph, node,node);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getSubGraphByParent(Graph<Node,Edge> graph, Node parent, Node node) {
		Set<Node> seen = new HashSet<>();
		seen.add(parent);
		Set<Node>  res = getSubGraphHelper(graph, node, seen);
		
		res.remove(parent);
				
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static void removeRedundantRelationships(Graph<Node,Edge> graph, Node parent) {
				
		Set<Node> nodes = new HashSet<>();
		nodes.addAll( graph.vertexSet() );
				
		if(!nodes.contains(parent)) return;
		
		nodes = graph.incomingEdgesOf(parent).stream().filter(Edge::isDiscriminator).map(graph::getEdgeSource).collect(toSet());
		
		nodes.remove(parent);
		
		LOG.debug("removeRedundantRelationships: resource={} nodes={}", parent, nodes);

		boolean removed=false;
		for(Node node : nodes) {
			Set<Edge> irrelevantDiscriminators = graph.edgesOf(node).stream().filter(Edge::isDiscriminator).collect(toSet());
			graph.removeAllEdges(irrelevantDiscriminators);
			removed = removed || !irrelevantDiscriminators.isEmpty();
		}
		
		Predicate<Node> noInboundEdges  = n -> graph.incomingEdgesOf(n).isEmpty();
		Predicate<Node> notResourceNode = n -> !n.equals(parent);
		
		while(removed) {
			nodes = graph.vertexSet().stream().filter(noInboundEdges).filter(notResourceNode).collect(toSet());
			graph.removeAllVertices(nodes);	

			LOG.debug("removeRedundantRelationships: resource={} REMOVE nodes={}", parent, nodes);

			removed = !nodes.isEmpty();
		}
	
		LOG.debug("removeRedundantRelationships: resource={} DONE nodes={}", parent, graph.vertexSet());
		LOG.debug("removeRedundantRelationships: resource={} DONE edges={}", parent, graph.edgeSet());

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static void removeTechnicalAllOfs(Graph<Node,Edge> graph) {
		Set<Edge> edges = graph.edgeSet().stream().filter(Edge::isDiscriminator).collect(toSet());
		
		if(true) return;
		
		LOG.debug("removeTechnicalAllOfs: #1 edges={}", edges);
		
		for(Edge edge : edges) {
			Node target = graph.getEdgeTarget(edge);
			Node source = graph.getEdgeSource(edge);
			
			if(target.getName().endsWith("RefOrValue") || source.getName().endsWith("RefOrValue")) {

				LOG.debug("removeTechnicalAllOfs: target={} source={}", target.getName(), source.getName());

				Set<Edge> allOfEdges = graph.getAllEdges(target, source).stream().filter(Edge::isAllOf).collect(toSet());
				
				LOG.debug("removeTechnicalAllOfs: target={} source={} allOfs={}", target.getName(), source.getName(), allOfEdges);

				graph.removeAllEdges(allOfEdges);	
				
				Predicate<Node> isOrphan = n -> graph.incomingEdgesOf(n).isEmpty();
				
				Set<Node> orphans = graph.vertexSet().stream().filter(isOrphan).collect(toSet());
				
				LOG.debug("removeTechnicalAllOfs: target={} source={} orphans={}", target.getName(), source.getName(), orphans);
				
			}
		}
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Set<Node> getSubGraphHelper(Graph<Node,Edge> graph, Node node, Set<Node> seen) {
		Set<Node> neighbours = getOutboundNeighbours(graph, node);
		
		LOG.debug("getSubGraphHelper: node={} graph={}",node, graph.vertexSet());

//		if(node.getName().equals("Product")) LOG.debug("getSubGraphHelper: {} neighbours={}",  node, neighbours);		
//		if(node.getName().equals("Product")) LOG.debug("getSubGraphHelper: {} graph={}",  node, graph.vertexSet());

		Set<Node> res = new HashSet<>();
		
		res.addAll(neighbours);

		seen.add(node);
		
		for(Node n : neighbours) {
			if(!seen.contains(n)) {
				Set<Node> sub = getSubGraphHelper(graph, n,seen);
				res.addAll(sub);
			}
		}
		
//		if(node.getName().equals("Product")) LOG.debug("getSubGraphHelper: {} res={}",  node, res);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<Node> getReverseSubGraph(Graph<Node,Edge> graph, Node node) {
		Set<Node> seen = new HashSet<>();
		seen.add(node);
		return getReverseSubGraphHelper(graph, node, seen);	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static Set<Node> getReverseSubGraphHelper(Graph<Node,Edge> graph, Node node, Set<Node> seen) {
		Set<Node> neighbours = getInboundNeighbours(graph, node);
		
		Set<Node> res = new HashSet<>();
		
		res.addAll(neighbours);
		seen.add(node);
		
		for(Node n : neighbours) {
			if(!seen.contains(n)) {
				Set<Node> sub = getReverseSubGraphHelper(graph, n,seen);
				res.addAll(sub);
			}
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public Graph<Node, Edge> getCompleteGraph() {
		return this.completeGraph;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Graph<Node, Edge> copyGraph(Graph<Node, Edge> graph) {
		Graph<Node, Edge> revGraph = new EdgeReversedGraph<>(graph);		
		return new EdgeReversedGraph<>(revGraph);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isLeafNode(Graph<Node, Edge> graph, Node node) {
		return graph.outgoingEdgesOf(node).isEmpty();
	}

	public static boolean isLeafNodeOrOnlyEnums(Graph<Node, Edge> graph, Node n) {
		return isLeafNode(graph,n) ||
			   getOutboundNeighbours(graph,n).stream().allMatch(Node::isEnumNode);
		
	}

	public static void simplifyAllOfDiscriminatorPairs(Graph<Node, Edge> completeGraph, Graph<Node, Edge> graph, Node resource) {
		Set<Node> neighbours = getNeighbours(graph, resource);
		
		for(Node node : neighbours) {
			Set<Edge> hasAllOf = graph.getAllEdges(node, resource).stream().filter(Edge::isAllOf).collect(toSet());
			Set<Edge> hasDiscriminator = graph.getAllEdges(resource, node).stream().filter(Edge::isDiscriminator).collect(toSet());
			
			if(!hasAllOf.isEmpty() && !hasDiscriminator.isEmpty()) {
				
				LOG.debug("simplifyAllOfDiscriminatorPairs: hasAllOf={}",  hasAllOf);
				graph.removeAllEdges(hasDiscriminator);
				
				// replaceAllOfWithReverseAllOfs(completeGraph, graph, hasAllOf);
				
			}
		}
	}

//	private static void replaceAllOfWithReverseAllOfs(Graph<Node, Edge> completeGraph, Graph<Node, Edge> graph, Set<Edge> allOfEdges) {
//		for(Edge edge : allOfEdges) {
//			Edge reverse = new AllOfReverse(edge);
//			
//			completeGraph.addEdge(graph.getEdgeTarget(edge), graph.getEdgeSource(edge), reverse);
//			
//			graph.addEdge(graph.getEdgeTarget(edge), graph.getEdgeSource(edge), reverse);
//		}
//		graph.removeAllEdges(allOfEdges);
//	}	

}

