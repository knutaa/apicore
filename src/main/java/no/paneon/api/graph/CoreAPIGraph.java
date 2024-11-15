package no.paneon.api.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.GraphIterator;
import org.json.JSONArray;
import org.json.JSONObject;

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
    private static final String CUSTOM_INHERITANCE = "customInheritanceTypes"; 

    static final String INCLUDE_INHERITED = "includeInherited"; 
    
    static final String INCLUDE_DISCRIMINATOR_MAPPING = "includeDiscriminatorMapping";
    static final String SET_DISCRIMINATOR_DEFAULT = "setDiscriminatorDefault";
    static final String DESCRIPTION = "description";
    
	static final String DEPRECATED = "deprecated";

    static final String REF = "$ref";
    static final String ITEMS = "items";
	static final String ENUM = "enum";

	Graph<Node,Edge> completeGraph;
	
	List<String> allResources = new LinkedList<>();
	
	public CoreAPIGraph() {
		this(new LinkedList<>());
	}
	
	public CoreAPIGraph(List<String> allResources) {
		
//		if(!Config.getBoolean("keepMVOFVOResources")) {
//			Predicate<String> MVO_or_FVO = s -> s.endsWith("_FVO") || s.endsWith("_MVO");
//			allResources = allResources.stream().filter(MVO_or_FVO.negate()).toList();
//		}
		
		allResources = APIModel.filterMVOFVO(allResources);

		
		this.allResources = allResources;
		this.graphNodes = new HashMap<>();
		this.enumNodes = new HashMap<>();
		this.enumMapping = new HashMap<>();
		
		LOG.debug("CoreAPIGraph:: #1");

		this.completeGraph = generateGraph();	
		
		LOG.debug("CoreAPIGraph:: edges={}", completeGraph.edgeSet());

//		completeGraph.vertexSet().stream()
//       	.filter(n -> n.getName().contentEquals("ProductRefOrValue"))
//       	.map(n -> completeGraph.edgesOf(n))
//       	.forEach(e -> LOG.debug("init: ProductRefOrValue edge={}", e));

//		completeGraph.vertexSet().stream()
//       	.filter(n -> n.getName().contentEquals("PermissionSpecification"))
//       	.map(n -> completeGraph.outgoingEdgesOf(n))
//       	.forEach(e -> LOG.debug("init: edge={}", e));
		
		addOrphanEnums();
		
		if(Config.getBoolean("removeInheritedAllOfEdges")) {
			completeGraph.vertexSet().forEach(node -> {
				node.getInheritance().forEach(inherits -> {
					Optional<Node> inheritsNode = CoreAPIGraph.getNodeByName(completeGraph, inherits);
					if(inheritsNode.isPresent()) {
						completeGraph.outgoingEdgesOf(node).stream()
							.filter(Edge::isAllOf)
							.map(completeGraph::removeEdge);
					}
				});
				node.clearInheritance();
			});
		}

		LOG.debug("CoreAPIGraph:: final edges={}", completeGraph.edgeSet().stream().map(Edge::toString).collect(Collectors.joining("\n")) );

		LOG.debug("CoreAPIGraph:: edges={}", completeGraph.edgeSet().stream().map(Edge::toString).collect(Collectors.joining("\n")) );

		updateNodeInheritance();
		
		updateNodePropertiesFromFVO();

		updateDiscriminators();
		
		markRequiredDiscriminators();
		
		updateCardinalityFromFactoryObjects();
		
		LOG.debug("CoreAPIGraph:: completeGraph={}", completeGraph);
		
		LOG.debug("CoreAPIGraph:: #2 edges={}", completeGraph.edgeSet());
		
		LOG.debug("CoreAPIGraph:: final edges={}", completeGraph.edgeSet().stream().map(Edge::toString).collect(Collectors.joining("\n")) );

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addOrphanEnums() {
  	    List<String> orphans = Config.getOrphanEnums();
  	    LOG.debug("## addOrphanEnums(): orphans={}", orphans);
  	    if(Config.getIncludeOrphanEnums()) {
  	    	JSONObject orphanEnums = Config.getConfig("orphan-enums-by-resource");
  	    	
  	    	orphanEnums.keySet().stream().forEach(resource -> {
  	    		
  	    		if(orphanEnums.optJSONArray(resource)==null) return;
  	    		
  	    		Node node = this.getNode(resource);
  	    		List<String> enums = orphanEnums.getJSONArray(resource).toList().stream().map(Object::toString).collect(toList());
  	    		
  	    		if(enums.isEmpty()) {
  	    			enums = this.enumNodes.entrySet().stream().filter(v -> {
  	    				 return this.completeGraph.incomingEdgesOf(v.getValue()).isEmpty();
  	    			}).map(m->m.getValue()).map(EnumNode::getName).collect(toList());
  	    		}
  	    		
  	  	  	    LOG.debug("## addOrphanEnums: resource={} orphanEnums={}", resource, enums);

  	    		enums.forEach(e -> {
  	    			Node orphanNode = this.getNode(e);
  	    			if(orphanNode!=null) {
  	    		 	    Edge edge = new EdgeEnum(node,"", orphanNode,"",false,false);
  	  	    	  	    this.completeGraph.addEdge(node, orphanNode, edge);
  	  	    	 
  	    			}

  	    		});
  	    	});
  	    }
				
	}


	private void updateCardinalityFromFactoryObjects() {
		Set<Node> nodes = this.completeGraph.vertexSet().stream().filter(n-> !n.getName().endsWith("_FVO")).collect(toSet());
		nodes.forEach(node -> {
			Optional<Node> fvoNode = this.completeGraph.vertexSet().stream()
										.filter(fvo -> fvo.getName().contentEquals(node.getName()+"_FVO"))
										.findFirst();
			
			if(fvoNode.isPresent()) {
				Set<Edge> edges = this.completeGraph.edgesOf(fvoNode.get());
				edges.forEach(edge -> {
					String cardinality = edge.cardinality;
					Node target = this.completeGraph.getEdgeTarget(edge);
					if(target.getName().endsWith("_FVO")) {
						String baseTarget = target.getName().replace("_FVO","");
						Optional<Node> baseTargetNode = CoreAPIGraph.getNodeByName(completeGraph, baseTarget);
						if(baseTargetNode.isPresent()) target = baseTargetNode.get();
					}
					Edge baseEdge = this.completeGraph.getEdge(node, target);
					
					LOG.debug("updateCardinalityFromFactoryObjects: node={} target={} baseEdge={} cardinaliry={}",  node, target, baseEdge, cardinality);

					if(baseEdge!=null) {
						baseEdge.cardinality=cardinality;
						Property p = node.getPropertyByName(baseEdge.relation);
						if(p!=null && !p.getCardinality().contentEquals((cardinality))) {
							LOG.debug("updateCardinalityFromFactoryObjects: update property cardinality node={} edge_cardinality={} property_cardinalit={}", node.getName(), cardinality, p.getCardinality());
							p.setCardinality(cardinality);
						}
					}
					
				});
			}
		});
	}


	private void updateNodePropertiesFromFVO() {
		Set<Node> nodes = this.completeGraph.vertexSet().stream().filter(n-> !n.getName().endsWith("_FVO")).collect(toSet());
		nodes.forEach(Node::updatePropertiesFromFVO);		
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
		
		node.addInheritance(inheritance);

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

		LOG.debug("generateGraph: g=" + g);

		addNodesAndEnums(g);
		
		if(APIModel.isAsyncAPI()) {
			Set<String> currentNodes = g.vertexSet().stream().map(Node::getName).collect(toSet());
			Set<String> addedAsync = APIModel.getAddedAsyncTypes();
			addedAsync.removeAll(currentNodes);
			
			addedAsync.forEach(node -> this.getOrAddNode(g,node));
		}
		
		LOG.debug("generateGraph: g=\n...{}", g.edgeSet().stream().map(Edge::toString).collect(Collectors.joining("\n... ")));

		LOG.debug("CoreAPIGraph:: nodes={}", g.vertexSet().stream().map(Node::toString).collect(Collectors.joining("\n")) );

		addProperties(g);

		LOG.debug("generateGraph: g=" + g);
		
		LOG.debug("CoreAPIGraph:: final edges={}", g.edgeSet().stream().map(Edge::toString).collect(Collectors.joining("\n")) );

		LOG.debug("CoreAPIGraph:: inline nodes={}", g.vertexSet().stream().filter(n->!n.getInline().isEmpty()).map(Node::getName).collect(Collectors.joining("\n")) );

		Predicate<Node> hasInlineDescription = n -> !n.getInline().isEmpty() && !n.getInline().contentEquals("object"); // was !n
		
		Set<Node> inlineNodes = g.vertexSet().stream()
									.filter(hasInlineDescription)
									.filter(n -> !this.allResources.contains(n.getName()))
									.collect(toSet());
				
		LOG.debug("generateGraph: inline nodes={} allResources={}", inlineNodes, this.allResources);

		Set<Edge> outgoingFromInline = inlineNodes.stream().map(g::outgoingEdgesOf).flatMap(Set::stream).collect(toSet());
		
		LOG.debug("generateGraph: outgoingFromInline=\n...{}", outgoingFromInline.stream().map(Edge::toString).collect(Collectors.joining("\n... ")));

		g.removeAllEdges(outgoingFromInline);
		
		LOG.debug("generateGraph: edges=" + g.edgeSet().stream().map(Object::toString).collect(Collectors.joining("\n")));

		LOG.debug("generateGraph: g=\n...{}", g.edgeSet().stream().map(Edge::toString).collect(Collectors.joining("\n... ")));

		return g;
			
	}
	 
	@LogMethod(level=LogLevel.DEBUG)
	private void addNodesAndEnums(Graph<Node, Edge> g) {
		
		LOG.debug("addNodesAndEnums: g.nodes={}", g.vertexSet());

		boolean run = true;
		while(run) {
			run = false;
			
			APIModel.getAllDefinitions().forEach(node -> getOrAddNode(g,node));
			
			if(APIModel.getAllDefinitions().size()!=g.vertexSet().size()) {
	
				final Set<String> graphNodes = g.vertexSet().stream().map(Node::getName).collect(Collectors.toSet());
				Set<String> newNodes = APIModel.getAllDefinitions().stream().filter(n -> !graphNodes.contains(n)).collect(Collectors.toSet());
						
				LOG.debug("addNodesAndEnums: NEW graphNodes={} definitions={}", graphNodes, APIModel.getAllDefinitions());
				LOG.debug("addNodesAndEnums: NEW definitions={} graph={}", APIModel.getAllDefinitions().size(), g.vertexSet().size());
	
				LOG.debug("addNodesAndEnums: newNodes={}", newNodes);

				run = !newNodes.isEmpty();
			}
		}
		
		LOG.debug("addNodesAndEnums: nodes={}", g.vertexSet());
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private Node getOrAddNode(Graph<Node, Edge> g, String definition) {		
		Node node;
		String coreDefinition = APIModel.removePrefix(definition);
		
		Optional<Node> candidate = getNodeByName(g,definition);
				
		LOG.debug("getOrAddNode::candidate={} coreDefinition={}", candidate, coreDefinition);

		if(candidate.isPresent()) return candidate.get();
		
		LOG.debug("getOrAddNode::isEnumType={}", APIModel.isEnumType(definition));

		node = APIModel.isEnumType(definition) ? new EnumNode(coreDefinition) : new Node(coreDefinition);
		
		LOG.debug("getOrAddNode::node={}", node);

		g.addVertex(node);
		graphNodes.put(coreDefinition, node);

		if(APIModel.isAsyncAPI()) {
			if(!node.additional_edges.isEmpty()) {
				LOG.debug("CoreAPIGraph:: node={} additional_edges={}", node, node.additional_edges);
				node.additional_edges.forEach(propName -> {
					Property prop = node.getPropertyByName(propName);
					String propType = prop.getType();
					JSONObject propDef = APIModel.getDefinition(propType);
					
					boolean earlierCreatedNode = g.vertexSet().stream().map(Node::getName).anyMatch(n -> n.contentEquals(propType));
					
					LOG.debug("CoreAPIGraph:: node={} to={} earlierCreatedNode={}", node, propType, earlierCreatedNode);

					Node to = getOrAddNode(g, propType);
					to.setDynamic(!earlierCreatedNode);
					
					boolean isRequired = APIModel.isRequired(node.getName(), propName);
					String cardinality = APIModel.getCardinality(propDef, isRequired);
					boolean isDeprecated = APIModel.isDeprecated(node.getName(), propName);

					g.addEdge(node, to, new Edge(node, propName, to, cardinality, isRequired, isDeprecated) );
					LOG.debug("CoreAPIGraph:: add EDGE node={} to={}", node, to);
				});
			}
		}
				
		if(!APIModel.getAllDefinitions().contains(node.getName())) {
			LOG.debug("... ISSUE missing node={} in API model ({}) ({})", node, coreDefinition, APIModel.getSource());
		}
		
		LOG.debug("getOrAddNode:: adding new node={}", node);

		if(node instanceof EnumNode) {
			addEnumNode((EnumNode) node);
		} else {
			addProperties(g, node.getName());
		}
		
		if(node.getLocalDiscriminators().size()>1) {
			LOG.debug("getOrAddNode:: non empty local discriminator node={} localDiscriminator={}", node, node.getLocalDiscriminators());
			node.getLocalDiscriminators().stream()
					.filter(label -> !label.contentEquals(node.getName()))
					.forEach(label -> {
						String discriminatorReference = APIModel.getDiscriminatorReference(node.getName(),label);
						
						Node to = getOrAddNode(g, discriminatorReference);
						g.addEdge(node, to, new Discriminator(node, to));
						LOG.debug("getOrAddNode:: add discriminator node={} to={}", node, to);
					});
		}
		
		LOG.debug("addNode:: adding node={}", definition);
				
		if(node.getName().contentEquals("String")) LOG.debug("getOrAddNode:: node={} edges={}", node, g.edgesOf(node).stream().map(Edge::toString).collect(Collectors.joining("\n")) );

		return node;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addProperties(Graph<Node, Edge> g) {
		for(String definition : APIModel.getAllDefinitions() ) {
			LOG.debug("addProperties:: definition={}", definition);

			addProperties(g,definition);
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addProperties(Graph<Node, Edge> g, String definition) {
	
		LOG.debug("addProperties: definition={}", definition);

		String coreDefinition = APIModel.removePrefix(definition);

		Node node = getOrAddNode(g,coreDefinition);
		
		// JSONObject properties = APIModel.getPropertyObjectForResource(definition);
		JSONObject properties = APIModel.getPropertyObjectForResource(definition);

		LOG.debug("addProperties: node={} properties={}", node, properties);

		addProperties(g, node, definition, properties);			
		
		LOG.debug("addProperties: node={} properties={}", node, node.getProperties());

		JSONArray allOfs = APIModel.getAllOfForResource(definition);
		
		LOG.debug("addProperties: node={} allOfs={}", node, allOfs);

		allOfs.forEach(allOf -> {
			if(allOf instanceof JSONObject) {
				JSONObject allOfObject = (JSONObject) allOf;
				
				LOG.debug("addProperties:: node={} allOfObject={}", node, allOfObject);

				if(allOfObject.has(REF)) {
					processAllOfReference(g, allOfObject, node);					
				} else {
					JSONObject obj = APIModel.getPropertyObjectBySchemaObject(allOfObject);
					LOG.debug("##2 addProperties:: allOf: resource={} obj={}", definition, obj);

					if(obj.isEmpty()) return;
					
					if(obj!=null ) {
//						String type = APIModel.getTypeName(obj);
//
//						// if(type==null || type.isEmpty()) type=definition;
//						
//						if(type.isEmpty()) return;
//						
//						LOG.debug("addProperties:: allOf: resource={} type={} obj={}", definition, type, obj);
//						
//						addProperties(g, node, type, obj);		

						String type = definition;

						LOG.debug("#1 addProperties:: allOf: resource={} type={} obj={}", definition, type, obj);
						
						addProperties(g, node, type, obj);	
						
						
					} else {
						
						Out.printAlways("addProperties:: NOT PROCESSED: resource={} allOfObject={}", definition, allOfObject.toString(2));

					}
				}				
			}
		});
			
		LOG.debug("addProperties:: node={} getProperties={}", node, node.getPropertyNames());

		addEdgesForInheritedEnums(g, node);
		
		Set<Edge> edges = g.outgoingEdgesOf(node);
		LOG.debug("addProperties:: node={} edges={}", node, edges);
		Set<Property> referencedProperties = new HashSet<>();
		edges.stream().filter(Edge::isRegularEdgeCore).forEach(e -> {
			String description = "";
			
			Property p = new Property(e.getRelationship(), 
									e.getRelated().getName(), 
									e.cardinality, 
									e.required, description, 
									Property.VISIBLE_INHERITED );
			
			p.setDeprected(e.getDeprecated());
			
			referencedProperties.add(p);
			
			LOG.debug("addProperties:: node={} referenced property={}", node, p);

		});
		
		LOG.debug("addProperties:: node={} referencedProperties={}", node, referencedProperties);

		node.addProperties(referencedProperties);
		
		
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
						
			Edge edge = new EdgeEnum(node, enumEdgeToAdd.getRelationship(), 
								enumEdgeToAdd.getRelated(), 
								enumEdgeToAdd.cardinality, 
								enumEdgeToAdd.required,
								enumEdgeToAdd.deprecated );
			
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
		
		boolean flattenInheritance = isBasicInheritanceType(type) || isPatternInheritance(type) ;
		boolean customFlattenInheritance = isCustomInheritanceType(type);

//		LOG.debug("processAllOfReference:: type={} node={} isBasicInheritanceType={} isPatternInheritance={}", 
//				type, node, isBasicInheritanceType(type), isPatternInheritance(type));	

		LOG.debug("processAllOfReference:: type={} node={}", type, node);	

		LOG.debug("processAllOfReference:: type={} node={} flattenInheritance={}", type, node, flattenInheritance);	

		flattenInheritance = flattenInheritance && !APIModel.isEnumType(type);
		boolean includeInherited = Config.getBoolean(INCLUDE_INHERITED); 
		
		LOG.debug("processAllOfReference:: type={} node={} flattenInheritance={} includeInherited={}", type, node, flattenInheritance, includeInherited);	

		if(customFlattenInheritance) {
			
			LOG.debug("processAllOfReference:: customFlattenInheritance type={} node={}", type, node);	

			if(node.getInheritance().contains(type)) {
				LOG.debug("processAllOfReference:: customFlattenInheritance node={} already processed inheritance={}", node, type);
				return;
			}
			
			node.addInheritance(type);
			node.addCustomFlatten(type);		
			
			JSONObject obj = APIModel.getDefinitionBySchemaObject(allOfObject);							
			
			Set<Property> propertiesBefore = new HashSet<>(node.getProperties());
			
			node.addAllOfObject(obj, Property.VISIBLE_INHERITED);
			
			LOG.debug("processAllOfReference:: node={} properties={}", node, node.getPropertyNames());	

			addEnumsToGraph(g, node, propertiesBefore);			
							
			Node inheritsFromNode = getOrAddNode(g, type); 
			
			Set<Edge> outboundEdges = getOutboundEdges(g, inheritsFromNode).stream()
											.filter(Predicate.not(Edge::isAllOf))
											.filter(Predicate.not(Edge::isEnumEdge))	
											.filter(Predicate.not(Edge::isDiscriminator))
											.collect(Collectors.toSet());
			
			LOG.debug("processAllOfReference:: customFlattenInheritance node={} inheritsFromNode={} inheritEdges={}", node, type, outboundEdges);	

			for(Edge edge : outboundEdges) {
				Node to = edge.getRelated();
				
				if(!to.equals(node) && !edge.isDiscriminator()) {
					Edge newEdge = new Edge(edge,node);
					
					LOG.debug("processAllOfReference:: customFlattenInheritance newEdge={} to={}", newEdge, to);	
	
					g.addEdge(node, to, newEdge);
				}
				
			}
			
//			boolean hasAllOfEdge = g.edgesOf(node).stream().anyMatch(isAllOfWithToNode);
//			
//			LOG.debug("processAllOfReference:: node={} to={} hasAllOfEdge={}", node, to, hasAllOfEdge);	
//
//			if(!hasAllOfEdge) {
//				Edge edge = new AllOf(node, to);
//				addGraphEdge(g, node, to, edge);		
//				LOG.debug("processAllOfReference:: adding edge={}", edge);	
//				
//				LOG.debug("processAllOfReference:: node={} edges={}", node, g.outgoingEdgesOf(node));	
//
//			}			
//			
		} else if(flattenInheritance) {
			
			LOG.debug("processAllOfReference:: flattenInheritance type={} node={}", type, node);	

			node.addInheritance(type);
			
			if(includeInherited) {
				JSONObject obj = APIModel.getDefinitionBySchemaObject(allOfObject);							
				
				Set<Property> propertiesBefore = new HashSet<>(node.getProperties());
				
				node.addAllOfObject(obj, Property.VISIBLE_INHERITED);
				
				LOG.debug("processAllOfReference:: node={} properties={}", node, node.getPropertyNames());	

				addEnumsToGraph(g, node, propertiesBefore);			
			
			}
			
		} else {		
			
			Node to = getOrAddNode(g, type); 
			
			Predicate<Edge> isAllOfWithToNode = e -> e.isAllOf() && g.getEdgeTarget(e).equals(to);
			
			boolean hasAllOfEdge = g.edgesOf(node).stream().anyMatch(isAllOfWithToNode);
			
			LOG.debug("processAllOfReference:: node={} to={} hasAllOfEdge={}", node, to, hasAllOfEdge);	

			if(!hasAllOfEdge) {
				Edge edge = new AllOf(node, to);
				addGraphEdge(g, node, to, edge);		
				LOG.debug("processAllOfReference:: adding edge={}", edge);	
				
				LOG.debug("processAllOfReference:: node={} edges={}", node, g.outgoingEdgesOf(node));	

			}
			
			node.addInheritance(to.getName());
			
		}
		
		LOG.debug("processAllOfReference:: node={} properties={}", node, node.getPropertyNames());	
		LOG.debug("processAllOfReference:: node={} inheritnace={}", node, node.getInheritance());	

		
	}

	private void addEnumsToGraph(Graph<Node, Edge> g, Node node, Set<Property> propertiesBefore) {
		Set<Property> propertiesAdded = new HashSet<>(node.getProperties());
		propertiesAdded.removeAll(propertiesBefore);
		Set<Property> enumsAdded = propertiesAdded.stream().filter(Property::isEnum).collect(toSet()); 

		if(!enumsAdded.isEmpty()) {
			LOG.debug("adding enums for node={} enums={}",  node, enumsAdded); 
			enumsAdded.forEach(property -> {
				Node to = getOrAddNode(g, property.type); // graphNodes.get(property.getType());
				if(to!=null) {
					Edge edge = new EdgeEnum(node, property.name, to, property.cardinality, property.required, property.isDeprected);
					addGraphEdge(g, node, to, edge);	
				} else {
					Out.printAlways("ERROR: Missing enum definition for resource {} property {}", node, property);
				}
			});
		}		
	}

	private static boolean isCustomInheritanceType(String type) {
		final List<String> inheritance = Config.get(CUSTOM_INHERITANCE);
		
		boolean res = inheritance.contains(type);
		
		if(res) LOG.debug("isCustomInheritanceType: type={} inheritance={}", type, inheritance);

		return res;
	}
	
	private static boolean isBasicInheritanceType(String type) {
		final List<String> inheritance = Config.get(INHERITANCE);
		
		boolean res = inheritance.contains(type);
		
		if(res) LOG.debug("isBasicInheritanceType: type={} inheritance={}", type, inheritance);

		return res;
	}

	private static boolean isPatternInheritance(String type) {
		final List<String> patterns = Config.get(INHERITANCE_PATTERN);
		
		boolean res = patterns.stream().anyMatch(pattern -> type.matches(pattern));
		
		if(res) LOG.debug("isPatternInheritance: type={} patterns={}", type, patterns);

		return res;
	}
	
	public static boolean isPatternInheritance(Node type) {
		final List<String> patterns = Config.get(INHERITANCE_PATTERN);
		return patterns.stream().anyMatch(pattern -> type.getName().matches(pattern));
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addProperties(Graph<Node, Edge> graph, Node from, String typeName, JSONObject properties) {
		
		if(properties==null) properties = new JSONObject();
		
		LOG.debug("addProperties: typeName={} deprecated={}", typeName, properties.optBoolean(DEPRECATED));

		// Set<String> existingProperties = from.getProperties().stream().map(Property::getName).collect(toSet());
		
		// if("QuoteItem".contentEquals(typeName)) LOG.debug("addProperties: from={} existingProperties={}", from, existingProperties);

		// Set<String> newProperties = properties.keySet(); // .stream().filter(p -> !existingProperties.contains(p)).collect(toSet());		
		
		for(String propertyName : properties.keySet()) {
						
			JSONObject property = properties.optJSONObject(propertyName);
		
			LOG.debug("addPropertyDetails: property={} deprecated={}" , propertyName, property.optBoolean(DEPRECATED) );

			LOG.debug("addProperties: typeName={} property={}", typeName, property);

			if(property==null || property.isEmpty()) continue;
			
			LOG.debug("## addProperties: propertyName={} properties={}", propertyName, property);

//			if(property==null) {
//				if(!APIModel.isAsyncAPI() || propertyName.contentEquals("name")) {
//					String className = Utils.getLastPart(properties.get(propertyName).getClass().toString(), ".");
//					Out.printOnce("... ERROR: expecting property '{}' of '{}' to be a JSON object, found {}", propertyName, from.getName(), className);
//					continue; 
//				}
//			}
			
			
			boolean isDeprecated = property.optBoolean(DEPRECATED);
			
			LOG.debug("addPropertyDetails: property={} deprecated={}" , propertyName, isDeprecated );

			if(isDeprecated) {
				LOG.debug("addPropertyDetails: property={} deprecated={}" , propertyName, isDeprecated );
			}
			
			String type = APIModel.getTypeName(property, propertyName);
			
			LOG.debug("addProperties: typeName={} type={}", typeName, type);

			if(type.isEmpty() && !APIModel.isAsyncAPI()) continue;
			
			if(type.isEmpty() && APIModel.isAsyncAPI()) {
				type=APIModel.createAsyncType(typeName,property);
				Node to = getOrAddNode(graph, type);
				
				boolean isRequired = true;
				String cardinality = "1..1";

				Edge edge = new Edge(from, propertyName, to, cardinality, isRequired, isDeprecated);
				
				LOG.debug("addProperties: EDGE from={} propertyName={} type={} edge={}", from, propertyName, type, edge);

				property=APIModel.getPropertyObjectForResource(typeName); 
			}

			String coreType = APIModel.removePrefix(type);

			if(coreType.contains("_")) {
				LOG.debug("addProperties: from={} propertyName={} type={} coreType={} property={}", from, propertyName, type, coreType, property);
				LOG.debug("addProperties: from={} propertyName={} type={} isSimpleType={}", from, propertyName, type, APIModel.isSimpleType(type));
			}
			
			boolean isArrayType=false;

			
			//
			// NEW handling of array types
			// 
			if(!Config.getBoolean("old_style_array_types")) {
				
				// if(!APIModel.isArrayType(type)) continue;
				
				LOG.debug("addProperties: type={} property={} ", type, property );

				// property = APIModel.getDefinition(type);

				// type = APIModel.getTypeName(property, propertyName);
				
				coreType = APIModel.removePrefix(type);
				
//				Node to = getOrAddNode(graph, coreType);
//				
//				LOG.debug("addProperties: NEW style type={} coreType={} property={}", type, coreType, property );
//
//				Set<Edge> existingEdgesToType = graph.edgesOf(from);
//				
//				LOG.debug("addProperties: from={} type={} to={} existingEdgesToType={}", from, coreType, to, existingEdgesToType);
//
//				existingEdgesToType = existingEdgesToType.stream().filter(e -> e.getRelated().getName().contentEquals(to.getName())).collect(toSet());
//
//				LOG.debug("addProperties: from={} type={} to={} existingEdgesToType={}", from, coreType, to, existingEdgesToType);
//
//				boolean existingEdgesToProperty = existingEdgesToType.stream().anyMatch( e -> e.getRelationship().contentEquals(propertyName));
//								
//				LOG.debug("addProperties: from={} type={} to={} existingEdgesToProperty={}", from, coreType, to, existingEdgesToProperty);
//
//				if(existingEdgesToProperty) continue;
//
//				boolean isRequired = APIModel.isRequired(typeName, propertyName);
//				String cardinality = APIModel.getCardinality(property, isRequired);
//
//				Edge edge = APIModel.isEnumType(type) ? 
//								new EdgeEnum(from, propertyName, to, cardinality, isRequired) :
//								new Edge(from, propertyName, to, cardinality, isRequired);
//			
//				LOG.debug("addProperties: NEW edge={} isRequired={} typeName={} propertyName={}", edge, isRequired, typeName, propertyName);
//
//				addGraphEdge(graph, from, to, edge);
		
				
			} else {
			
				// 
				// continue with the old version
				//
				
				if(property.has(REF) && APIModel.isArrayType(type)) {
					LOG.debug("addProperties: isArrayType from={} propertyName={} type={} coreType={} property={}", from, propertyName, type, coreType, property);
					
					property = APIModel.getDefinition(type);
	
					type = APIModel.getTypeName(property, propertyName);
					coreType = APIModel.removePrefix(type);
	
					LOG.debug("addProperties: isArrayType #2 type={} coreType={} property={} isSimpleType={}", type, coreType, property, APIModel.isSimpleType(type));
					isArrayType=true;
					
				} 
			
			}
			
			
			if(type.isEmpty()) continue;
			
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
				isDeprecated = APIModel.isDeprecated(typeName, propertyName);

				Edge edge = APIModel.isEnumType(type) ? 
								new EdgeEnum(from, propertyName, to, cardinality, isRequired, isDeprecated) :
								new Edge(from, propertyName, to, cardinality, isRequired, isDeprecated);
			
				LOG.debug("addProperties: edge={} isRequired={} isDeprecated={}", edge, isRequired, isDeprecated);

				addGraphEdge(graph, from, to, edge);

			} else {
				
				boolean isRequired = APIModel.isRequired(typeName, propertyName);
				String cardinality = APIModel.getCardinality(property, isRequired);

				String propType = APIModel.typeOfProperty(property, propertyName);		

				LOG.debug("###### addProperties: property={} typeOfProperty={}", property, propType);

				if(APIModel.isAddedType(propType)) {
					Node to = getOrAddNode(graph, propType);
					Edge edge = new Edge(from, propertyName, to, cardinality, isRequired, isDeprecated);
					LOG.debug("############# addProperties: edge={} isRequired={}", edge, isRequired);

				}
				
				LOG.debug("addProperties: typeName={} propertyName={} propType={} isRequired={}", typeName, propertyName, propType, isRequired);

				Property propDetails = new Property(propertyName, 
													propType, 
													cardinality, 
													isRequired, 
													property.optString(DESCRIPTION), 
													Property.VISIBLE_INHERITED );

				LOG.debug("addProperties: typeName={} property={}", propType, property);

				List<String> enumValues = Config.getList(property, ENUM);
				propDetails.addEnumValues(enumValues);
				
				if(property.has(ENUM)) {
					LOG.debug("addProperties: ENUM typeName={} values={}", propType, enumValues);

				}
				
				if(isDeprecated) {
					LOG.debug("addPropertyDetails: property={} deprecated={}" , propertyName, isDeprecated );
					propDetails.setDeprected(isDeprecated);
				}
				
				from.addProperty(propDetails);
				
			}
		}
		
		Set<Edge> edgesOfFromNode = graph.edgesOf(from);
				
		LOG.debug("addProperties: #1 from={} edgesOfFromNode={}", from, edgesOfFromNode.stream().filter(e -> graph.getEdgeSource(e).equals(from)).map(graph::getEdgeTarget).toList());

		LOG.debug("addProperties: typeName={} node.properties={}", typeName, from.properties);

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
	public static Set<Edge> getInboundEdges(Graph<Node,Edge> graph, Node node) {
		Set<Edge> res = new HashSet<>();
		if(graph.vertexSet().contains(node)) {
			res.addAll( graph.incomingEdgesOf(node) );
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
			Out.printAlways("... node argument equal to null - not expected");
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
	public static Graph<Node,Edge> getSubGraphWithInheritance(Collection<String> allResources, Graph<Node,Edge> origGraph, Node node, Node resource) {
						
		LOG.debug("getSubGraphWithInheritance: #000 node={} origGraph isDiscriminator=\n{}",  node, origGraph.edgeSet().stream().filter(Edge::isDiscriminator).map(Object::toString).collect(Collectors.joining("\n")));

		LOG.debug("getSubGraphWithInheritance:: node={} resource={} complete edgeSet={}", node, resource, origGraph.edgeSet());
		LOG.debug("getSubGraphWithInheritance:: node={} resource={}", node, resource);

		Set<Node> nodes = getNodesOfSubGraph(origGraph, node);
		
		Graph<Node,Edge> graph = new AsSubgraph<Node, Edge>(origGraph);

		LOG.debug("getSubGraphWithInheritance: #111 node={} graph isDiscriminator=\n{}",  node, graph.edgeSet().stream().filter(Edge::isDiscriminator).map(Object::toString).collect(Collectors.joining("\n")));

		LOG.debug("getSubGraphWithInheritance:: node={} nodes={} ", node, nodes);

		LOG.debug("getSubGraphWithInheritance:: node={} resource={} vertexSet={}", node, resource, graph.vertexSet());
		LOG.debug("getSubGraphWithInheritance:: node={} resource={} edgeSet={}", node, resource, graph.edgeSet());

		Predicate<Edge> notBothDirections = e -> {
			return graph.getAllEdges(e.related, e.node).isEmpty();
		};
		
		Set<Edge> inheritedFromNode = graph.incomingEdgesOf(node).stream()
											.filter(Edge::isAllOf)
											//.filter(Edge::isInheritance)
											// .filter(e -> !e.isDiscriminator())
											//.filter(e -> notBothDirections.test(e))
											.collect(toSet());
		
		Set<Edge> inheritedByNode = graph.outgoingEdgesOf(node).stream()
											.filter(Edge::isInheritance)
											.collect(toSet());
		
		Set<Node> inheritance = inheritedByNode.stream().map(Edge::getRelated).collect(Collectors.toSet());
		
		LOG.debug("getSubGraphWithInheritance:: node={} resource={} inheritanceFromNode={}", node, resource, inheritedFromNode);

		Set<Node> subclassedBy = inheritedFromNode.stream()							
									.map(Edge::getNode)
									.filter(n -> allResources.contains((n.getName())) ) // && !inheritance.contains(n))
									.collect(Collectors.toSet());
		
		LOG.debug("getSubGraphWithInheritance:: node={} resource={} subclassedBy={}", node, resource, subclassedBy);

		// graph.removeAllVertices(subclassedBy); // 06-2023

		Set<Node> simpleTypes = graph.vertexSet().stream()
									.filter(Node::isSimpleType)
									.collect(toSet());
		
		if(!simpleTypes.isEmpty()) LOG.debug("getSubGraph:: node={} resource={} simpleTypes={}", node, resource, simpleTypes);

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
	
		//
		// remove indirect inheritance
		//
		if(node.equals(resource)) {
			Set<Node> inherits = APIGraph.getOutboundEdges(graph, resource)
					                .stream()
									.filter(Edge::isAllOf)
									.map(Edge::getRelated)
									.filter(n -> allResources.contains(n.getName()))
									.collect(toSet());
			
			Set<Node> indirectInheritance = inherits.stream()
											.map(n -> APIGraph.getOutboundEdges(graph, n))
											.flatMap(Set::stream)
											.filter(Edge::isAllOf)
											.map(Edge::getRelated)
											.filter(n -> allResources.contains(n.getName()))
											.collect(toSet());
			
			LOG.debug("getSubGraphWithInheritance:: resource={} inherits={}", resource, inherits);
			LOG.debug("getSubGraphWithInheritance:: resource={} indirectInheritance={}", resource, indirectInheritance);
			
			Set<Edge> inboundAllOfEdges = indirectInheritance.stream()
													.map(n -> APIGraph.getInboundEdges(graph, n))
													.flatMap(Set::stream)
													.filter(Edge::isAllOf)
													.filter(e -> !e.getSourceName().contentEquals(resource.getName()))
													.collect(toSet());
			
			LOG.debug("getSubGraphWithInheritance:: resource={} inboundAllOfEdges={}", resource, inboundAllOfEdges);

			// 634: 2023-06-17
			if(!Config.getBoolean("keepIndirectInheritance")) {
				graph.removeAllEdges(inboundAllOfEdges);
			}
			
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
	
			LOG.debug("getSubGraphWithInheritance:: #2 node={} NO inbound discriminator={}", node, noInbound);

			noInbound.removeAll(subclassedBy);
			
			LOG.debug("getSubGraphWithInheritance:: #1 node={} NO inbound discriminator={}", node, noInbound);

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
			excludeNodes.removeAll(subclassedBy); // 06-2023

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

		excludeNodes = excludeNodes.stream()
							.filter(notEnumNode)
							.collect(toSet());
		
		LOG.debug("getSubGraphWithInheritance:: node={} excludeNodes={}", node, excludeNodes);

		excludeNodes.removeAll(subclassedBy); // 06-2023

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

		LOG.debug("## getSubGraphWithInheritance:: node={} before remove subGraph={}", node, subGraph.vertexSet());

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
			
			unReachable.removeAll(subclassedBy);

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
			
			Set<Node> neighbours =  graph.outgoingEdgesOf(node).stream().map(Edge::getRelated).collect(toSet()); // CoreAPIGraph.getOutboundNeighbours(graph, node);
			
			neighbours.addAll(mappedNodes);
			neighbours.removeAll(seen);
			
			// Predicate<Node> detailsInOtherGraph = n -> graph.outgoingEdgesOf(n).isEmpty();
			// neighbours = neighbours.stream().filter(detailsInOtherGraph).collect(toSet());
			
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


	public static void removeDuplicatedInheritedRelationships(Graph<Node, Edge> graph, Node resourceNode) {
		Set<String> allNodes = graph.vertexSet().stream().map(Node::getName).collect(toSet());
		for(Node node : graph.vertexSet()) {
			Set<String> inheritsFrom = node.getDeepInheritance();
			inheritsFrom = inheritsFrom.stream().filter(allNodes::contains).collect(toSet());
			
			if(!inheritsFrom.isEmpty()) {
				
				for(String neighbour : inheritsFrom) {
					Optional<Node> neighbourNode = CoreAPIGraph.getNodeByName(graph, neighbour);
					
					if(neighbourNode.isEmpty()) continue;
					
					LOG.debug("node={} inheritsFrom={}", node, inheritsFrom);
					
					Set<Edge> edgesFromNode = graph.outgoingEdgesOf(node);
					
					Set<Edge> edgesFromNeighbour = graph.outgoingEdgesOf(neighbourNode.get());
					
					LOG.debug("node={} edges={}", node, edgesFromNode);
					LOG.debug("neighbour={} edges={}", neighbour, edgesFromNeighbour);
					
					Predicate<Edge> isMatch =  e -> edgesFromNeighbour.stream().anyMatch(e::isEdgeMatch);
					
					Set<Edge> redundantEdges = edgesFromNode.stream().filter(isMatch).collect(toSet());
					
					if(!redundantEdges.isEmpty()) {
						LOG.debug("node={} redundantEdges={} ", node, redundantEdges);
						graph.removeAllEdges(redundantEdges);
					}
						
				}

			}	
			
		}
		
	}


	public static void removeUnreachable(Graph<Node, Edge> graph, Node node) {
		Set<Node> reachables = CoreAPIGraph.getReachable(graph, node);
		Set<Node> remove = graph.vertexSet().stream().filter(n -> !reachables.contains(n)).collect(toSet());
		
		LOG.debug("removeUnreachable: node={} reachables={} remove={}", node, reachables, remove);
		
		graph.removeAllVertices(remove);
	}

	public static Set<Edge> getNonInheritedEdges(Graph<Node, Edge> graph, Node node) {
		Set<Edge> res = new HashSet<>();

		Set<Edge> inheritedEdges = getInheritedEdges(graph,node);
		graph.edgesOf(node).forEach(edge -> {
			boolean inherited = inheritedEdges.stream().anyMatch(e -> e.related==edge.related);
			if(!inherited) res.add(edge);
		});
		
		LOG.debug("getNonInheritedEdges: node={} res={}", node, res);

		return res;
	}

	private static Set<Edge> getInheritedEdges(Graph<Node, Edge> graph, Node node) {
		Set<Edge> res = new HashSet<>();
		
		Set<Edge> outboundAllOfs = graph.outgoingEdgesOf(node).stream().filter(Edge::isAllOf).collect(toSet());
		
		outboundAllOfs.forEach(edge -> {
			res.addAll( getInheritedEdges(graph, edge.related));
			res.addAll( graph.edgesOf(edge.related) );
		});
		
		return res;
	}

	public static Graph<Node, Edge> cleanExplicitResources(Graph<Node, Edge> graph, List<String> allResources, String resource) {
		LOG.debug("cleanExplicitResources:: node={} allResources={}", resource, allResources);
		
		Predicate<Edge> isResourceNode = e -> allResources.contains(e.getRelated().getName()); 
		Predicate<Edge> isNotResource  = e -> !e.getRelated().getName().contentEquals(resource) && !e.getNode().getName().contentEquals(resource);

		Set<Edge> discriminatorEdges = graph.edgeSet().stream()
										.filter(Edge::isDiscriminator)
										.filter(isResourceNode)
										.filter(isNotResource)
										.collect(Collectors.toSet());
				
		LOG.debug("cleanExplicitResources:: ALL disriminatorEdges={}", 
				graph.edgeSet().stream()
				.filter(Edge::isDiscriminator).collect(Collectors.toSet()));

//		discriminatorEdges = graph.edgeSet().stream()
//				.filter(Edge::isDiscriminator)
//				.filter(isResourceNode)
//				.filter(isNotResource)
//				.collect(Collectors.toSet());

		LOG.debug("cleanExplicitResources:: node={} disriminatorEdges={}", resource, discriminatorEdges);

		graph.removeAllEdges(discriminatorEdges);

		return graph;
	}

	public static Graph<Node, Edge> cleanDiscriminatorEdges(Graph<Node, Edge> graph, String resource) {
		LOG.debug("cleanDiscriminatorEdges:: node={}", resource);
		
		if(resource==null) return graph;
		
		Predicate<Edge> isResourceNode = e -> e.getRelated().getName().contentEquals(resource);
		
		Predicate<Edge> isNotResourceNode = isResourceNode.negate();

		Set<Node> nodesWithDiscriminatorEdgesToResource = graph.edgeSet().stream()
															.filter(Edge::isDiscriminator)
															.filter(isResourceNode)
															.map(Edge::getNode)
															.collect(Collectors.toSet());
		
		Set<Edge> discriminatorEdges = new HashSet<>();
		
		for(Node node : nodesWithDiscriminatorEdgesToResource) {
			Set<Edge> edges = graph.edgesOf(node).stream()
					.filter(Edge::isDiscriminator)
					// .filter(isNotResourceNode)
					.collect(Collectors.toSet());
			
			LOG.debug("cleanDiscriminatorEdges:: resource={} node={} edges={}", resource, node, graph.edgesOf(node));

			discriminatorEdges.addAll( edges );
		}
		
		LOG.debug("cleanDiscriminatorEdges:: resource={} nodes={} disriminatorEdges={}", resource, nodesWithDiscriminatorEdgesToResource, discriminatorEdges);

		graph.removeAllEdges(discriminatorEdges);

		return graph;
	}

	public static Set<Node> getReachableNew(Graph<Node, Edge> g, String from) {
		Set<Node> seen = new HashSet<>();
		Optional<Node> node = CoreAPIGraph.getNodeByName(g, from);
		
		if(node.isPresent())
			return getReachableNewHelper(g, node.get(), seen);
		else
			return seen;
		
	}

	private static Set<Node> getReachableNewHelper(Graph<Node, Edge> g, Node from, Set<Node> seen) {
		Set<Node> res = new HashSet<>();
		if(seen.contains(from)) return res;
		
		res.add(from);
		seen.add(from);
		
		g.outgoingEdgesOf(from).stream()
			.map(Edge::getRelated)
			.forEach( n -> {
				res.addAll( getReachableNewHelper(g, n, seen));
			});
			
		return res;
	}

	public List<String> getSubclasses(String resource) {
		return getInboundEdges(this.getCompleteGraph(), this.getNode(resource)).stream()
				.filter(Edge::isAllOf)
				.map(Edge::getNode)
				.map(Node::getName)
				.collect(Collectors.toList());
	}

	
}

