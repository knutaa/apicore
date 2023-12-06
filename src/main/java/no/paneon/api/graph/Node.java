package no.paneon.api.graph;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toList;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class Node implements Comparable<Object>  {

    static final Logger LOG = LogManager.getLogger(Node.class);

    static Map<String,Node> nodeMap = new HashMap<>();
    
	List<Property> properties;
		
	Map<Place,List<Node>> placements;
	
	String description = "";

	List<OtherProperty> otherProperties;

	String resource = "ANON";
	
	List<String> enums; 
	
	Optional<Set<String>> inheritance;
	Set<String> actualInheritance;

	// Set<String> discriminatorMapping;
	Set<String> localDiscriminatorMapping;

	Optional<Set<String>> externalDiscriminatorMapping;

	String inline = "";
	
	static final String ATTYPE = "@type";
	static final String ALLOF = "allOf";
	static final String PROPERTIES = "properties";
	static final String TYPE = "type";
	static final String ARRAY = "array";
	static final String ITEMS = "items";
	static final String ENUM = "enum";
	static final String NULLABLE = "nullable";
	static final String REQUIRED = "required";

	static final String TITLE = "title";

	static final String DISCRIMINATOR = "discriminator";
	
	static final String DESCRIPTION = "description";
	static final String REF = CoreAPIGraph.REF;
	static final String INCLUDE_INHERITED = CoreAPIGraph.INCLUDE_INHERITED;

	static final String EXPAND_INHERITED = "expandInherited";
	static final String EXPAND_ALL_PROPERTIES_FROM_ALLOFS = "expandPropertiesFromAllOfs";
	
	Set<String> additional_edges;
	
	protected Node() {
		
		this.properties = new LinkedList<>();
		this.placements = new EnumMap<>(Place.class);
		
		this.otherProperties = new LinkedList<>();
		
		this.enums = new LinkedList<>();
		
		this.inheritance = Optional.empty();
		//discriminatorMapping = new HashSet<>();
		this.externalDiscriminatorMapping = Optional.empty();
		
		this.localDiscriminatorMapping = new HashSet<>();
		
		this.actualInheritance = new HashSet<>();

		// inheritedDiscriminatorMapping = new HashSet<>();
		
		this.additional_edges = new HashSet<>();

	}
	
	public Node(String resource) {
		this();
		this.resource=resource;		
				
		LOG.debug("Node resource={}" , resource );

		nodeMap.put(this.resource, this);
		
		addDescription();
		
		setLocalDiscriminators();

		this.inline = getInlineDefinition();
		
		if(!this.inline.isEmpty()) LOG.debug("#1 inline={}" , this.inline );

		Optional<JSONObject> optExpanded = getExpandedJSON();
		
		LOG.debug("Node optExpanded={}" , optExpanded );

		if(optExpanded.isPresent() && !optExpanded.get().isEmpty()) {
			
			if(!this.inline.isEmpty()) LOG.debug("#2 inline='{}'" , this.inline );

			this.inline = convertExpanded(optExpanded.get());
			
			LOG.debug("Node::getExpandedJSON resource={} optExpanded={}" , resource, optExpanded.get().toString(2) );
			if(!this.inline.isEmpty()) LOG.debug("#2 node={} inline='{}'" , this.resource, this.inline );

		} 
		
		if(this.inline.isEmpty()) {
			
			LOG.debug("#3 resource={} inline='{}'" , resource, this.inline );

			
			Property.Visibility visibility = Config.getBoolean(INCLUDE_INHERITED) ? Property.VISIBLE_INHERITED : Property.HIDDEN_INHERITED;

			addPropertyDetails(Property.BASE);									
			addAllOfs(visibility);
			// addOneOfs(visibility);
			
		}
				
	}

	Set<String> propertyNames = new HashSet<>();
	
	public Node addProperty(Property property) {
		
		LOG.debug("Node::addProperty node={} property={}" , this.getName(), property );

		if(!this.propertyNames.contains(property.name)) {
			this.properties.add(property);
			this.propertyNames.add(property.name);
		}
		return this;
	}
	
	public Node addProperties(Collection<Property> properties) {
		properties.stream().forEach(this::addProperty);
		return this;
	}
	
	public Node setProperties(Collection<Property> properties) {
		this.properties.clear();
		this.addProperties(properties);
		return this;
	}
	
	public void updateDiscriminatorMapping() {
		Set<String> inherited = getAllDiscriminators();
		if(this.localDiscriminatorMapping.isEmpty() && inherited.contains(this.resource)) {
			this.localDiscriminatorMapping.add(this.resource);
		}
		this.externalDiscriminatorMapping = Optional.of(inherited); 
	}
	
	public Set<String> getLocalDiscriminators() {
		return this.localDiscriminatorMapping;
	}

	public void setLocalDiscriminators() {
		JSONObject mapping = APIModel.getMappingForResource(this.resource);
		if(mapping!=null) this.localDiscriminatorMapping.addAll( mapping.keySet() );
	}
	
	private String convertExpanded(JSONObject obj) {
		String res = "";
		if(obj.has(ITEMS)) {
			res = convertExpanded(obj.optJSONObject(ITEMS));
			
			String cardinality = APIModel.getCardinality(obj,false);
			if(!cardinality.isBlank()) {
				res = res + " [" + cardinality + "]";
			}
			
		} else if(obj.has(TYPE)) {
			res = APIModel.getStringOrNull(obj, TYPE);
			
			if(res==null) {			
				Out.printOnce("... expecting the {} property to be a string value in {}", TYPE, obj.toString(2));
				res="";
			} 
		}
		return res;
	}

	private String getInlineDefinition() {
		String res="";
		JSONObject def = APIModel.getDefinition(this.resource);
		if(APIModel.isArrayType(this.resource) && !APIModel.isSimpleType(def)) 
			res="";
		else 
			res = getInlineDefinition(def);
		
		if(!res.isEmpty()) LOG.debug("getInlineDefinition: type={} res={}",  this.resource, res);
		
		LOG.debug("getInlineDefinition: node={} res='{}'",  this.resource, res);
		
		return res;
	}
	

	private Optional<JSONObject> getExpandedJSON() {
		JSONObject def = APIModel.getDefinition(this.resource);
		return getExpandedJSON(def);
	}

	private Optional<JSONObject> getExpandedJSON(JSONObject obj) {
		Optional<JSONObject> res = Optional.empty();
		
		if(obj==null) return res;
		
		JSONObject clone = new JSONObject(obj.toString());
			
//			if(resource.contentEquals("ProductRef")) {
//				LOG.debug("Node::getFlatten resource={} def={}" , resource, obj.toString(2) );
//			}
			
		LOG.debug("Node::getFlatten resource={} def={}" , resource, obj.toString(2) );
		
		if(obj.has(ENUM) || obj.has(PROPERTIES) || obj.has(DISCRIMINATOR) ) return res;
		// if(obj.has(ENUM) ) return res;

		List<String> inlineException = Config.get("inlineException");
		Predicate<String> endsWith = x -> resource.endsWith(x);
		boolean exception = inlineException.stream().anyMatch(endsWith);
		
		if(exception) LOG.debug("Node::getExpandedJSON resource={} exception={}" , resource, exception );

		if(exception) return res;
		
		if(obj.has(REF)) {
			JSONObject refDef = APIModel.getDefinitionBySchemaObject(obj);
			res = getExpandedJSON(refDef);
			
		} else if(obj.has(ALLOF)) {
			
			JSONArray array = obj.optJSONArray(ALLOF);			
			res = getExpandedJSON(array);
			if(res.isPresent()) {
				partialOverwriteJSON(clone,res.get());
			}	
			clone.remove(ALLOF);
			res = Optional.of(clone);

		} else if(obj.has(ITEMS)) {
			
			JSONObject items = obj.optJSONObject(ITEMS);
			if(items!=null) {
				res = getExpandedJSON(items);
				if(res.isPresent()) {
					clone.put(ITEMS, res.get());
					res = Optional.of(clone);
				}
			} else {
				JSONArray array = obj.optJSONArray(ITEMS);
				res = getExpandedJSON(array);
				if(res.isPresent()) {
					clone.put(ITEMS, res.get());
					res = Optional.of(clone);
				}
				
			}
							
		} else {
			res = Optional.of(clone);
		}
		
		if(res.isPresent()) LOG.debug("Node::getFlatten resource={} res={}" , resource, res );

		return res;
		
	}
	
	
	
	private Optional<JSONObject> getExpandedJSON(JSONArray array) {
		Optional<JSONObject> res = Optional.empty();
		
		LOG.debug("getExpandedJSON:: array={}",  array.toString(2));

		JSONObject clone = new JSONObject();
		
		Iterator<Object> iter = array.iterator();
		while(iter.hasNext()) {
			Object o = iter.next();
			LOG.debug("getExpandedJSON:: o={}",  o);
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject) o;
				res = getExpandedJSON(obj);
				if(res.isPresent()) {
					partialOverwriteJSON(clone,res.get());
				} else {
					return res;
				}
			} else {
				LOG.debug("getExpandedJSON:: NOT PROCESSED o={}",  o);
			}
		}
		
		if(!clone.keySet().isEmpty()) {
			res = Optional.of(clone);
		} else {
			res = Optional.empty();
		}
		
		return res;
	}

	private void partialOverwriteJSON(JSONObject target, JSONObject source) {
		LOG.debug("partialOverwriteJSON:: source={}",  source.toString());
		for(String key : source.keySet()) {
			target.put(key, source.get(key));
		}
	}

	private String getInlineDefinition(JSONObject def) {
		String res = "";
		
		List<String> inlineException = Config.get("inlineException");
		Predicate<String> endsWith = x -> resource.endsWith(x);
		boolean exception = inlineException.stream().anyMatch(endsWith);
		
		if(exception) LOG.debug("Node::getInlineDefinition resource={} exception={}" , resource, exception );

		if(exception) return res;

		if(def!=null) {
			LOG.debug("Node::getInlineDefinition resource={} def={}" , resource, def.toString() );
			
			if(def.has(DISCRIMINATOR)) {
				res = "";
			} else if(def.has(ENUM)) {
				res = "";
			} else if(def.has(PROPERTIES)) {
				res = "";
			} else if(def.has(REF)) {
				JSONObject refDef = APIModel.getDefinitionBySchemaObject(def);
				String cardinality = APIModel.getCardinality(refDef, false);

				res = getInlineDefinition(refDef) ;
				
				if(!res.isEmpty() && !cardinality.isBlank()) res = res + getCardinalityString(cardinality);
				
				LOG.debug("Node::getInlineDefinition resource={} refDef={}" , resource, refDef.toString() );

			} else if(def.has(ALLOF)) {
				JSONArray array = def.optJSONArray(ALLOF);
				
				res = getInlineDefinition(array);

				if(!res.isEmpty()) {
					String cardinality = getCardinality(array, res);
					res = res + cardinality;
				}			

			} else if(def.has(ITEMS)) {				
				String cardinality = APIModel.getCardinality(def, false);

				JSONObject obj = def.optJSONObject(ITEMS);
				if(obj!=null) {
					res = getInlineDefinition(obj);
				} else {
					JSONArray array = def.optJSONArray(ITEMS);
					Iterator<Object> iter = array.iterator();
					while(iter.hasNext()) {
						Object o = iter.next();
						if(o instanceof JSONObject) {
							obj = (JSONObject) o;
							res = getInlineDefinition(obj);
						} 
						if(!res.isBlank()) break;
					}
				}
				
				res = res + getCardinalityString(cardinality);	
				
			} else {
				res = getType(def);			
				if(!res.isEmpty()) {
					String cardinality = APIModel.getCardinality(def, false);
					res = res + getCardinalityString(cardinality);	
				}	
				
				LOG.debug("Node::getInlineDefinition resource={} res={}" , resource, res );
			}
		}
		
		if(!res.isBlank()) LOG.debug("Node::getInlineDefinition resource={} res={}" , resource, res );

		return res;
		
	}

	private String getInlineDefinition(JSONArray array) {
		String res = "";
		for(Object o : array) {
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject) o;
				
				LOG.debug("getInlineDefinition: obj={}", obj);
				
				res = getInlineDefinition(obj);
			} 
			if(!res.isBlank()) break;
		}
		return res;
	}

	private String getCardinality(JSONArray array, String definition) {
		String res = "";
		for(Object o : array) {
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject) o;
				res = APIModel.getCardinality(obj, false);
			} 
			if(!res.isBlank()) break;
		}
		
		if(!definition.isBlank()) LOG.debug("getCardinality: definition={} res={} array={}",  definition, res, array);
		
		return res;
	}
	
	private String getCardinalityString(String cardinality) {
		String res = "";
		if(!cardinality.isBlank()) res = " [" + cardinality + "]";
		return res;
	}

	private String getType(JSONObject def) {
		String res="";
		
		if(def.has(ITEMS)) {
			if(def.optJSONArray(ITEMS)!=null) {
				JSONArray array = def.optJSONArray(ITEMS);
				res = getType(array);
			} else if(def.optJSONObject(ITEMS)!=null) {
				JSONObject obj = def.optJSONObject(ITEMS);
				res = getType(obj);
			} 
		}
		
		if(res.isBlank() && def.has(TYPE)) res = def.getString(TYPE);
		
		return res;
	}

	private String getType(JSONArray def) {
		String res="";
		
		for(Object o : def) {
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject) o;
				res = getType(obj);
			} 
			
			if(!res.isBlank()) break;
		}
				
		return res;
	}
	

	@LogMethod(level=LogLevel.DEBUG)
	private void addPropertyDetails(Property.Visibility visibility) {
		JSONObject propObj = APIModel.getPropertyObjectForResource(this.resource);
		addPropertyDetails(propObj, visibility, null);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void removeProperties(Collection<Property> remove) {
		Collection<String> toRemove = remove.stream().map(Property::getName).collect(toSet());
		
		LOG.debug("toRemove={}", toRemove);
		LOG.debug("properties={}", this.properties);

		this.setProperties(this.properties.stream().filter(p->!toRemove.contains(p.getName())).collect(toList()) );
		
		LOG.debug("properties={}", this.properties);

	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addPropertyDetails(JSONObject propObj, Property.Visibility visibility, JSONObject definition) {
			
		if(definition!=null) LOG.debug("addPropertyDetails: node={} definition={}" , this, definition.toString(2) );

		if(APIModel.isArrayType(definition)) LOG.debug("## addPropertyDetails: node={} definition={}" , this, definition.toString(2) );

		if(APIModel.isArrayType(definition) && !APIModel.isSimpleType(definition) && !APIModel.isAsyncAPI()) {
			Out.printAlways("addPropertyDetails: ARRAY handled as relationship definition=" + definition.toString(2) );
			return;
		} 
		
		if(APIModel.isArrayType(propObj) && !APIModel.isSimpleType(propObj) && !APIModel.isAsyncAPI()) {
			Out.printAlways("addPropertyDetails: ARRAY handled as relationship propObj=" + propObj.toString(2) );
			return;
		} 
		
		LOG.debug("addPropertyDetails obj={}", propObj);

		if(propObj.has(TYPE) && ARRAY.equals(propObj.opt(TYPE)) && !APIModel.isAsyncAPI()) {
			Out.printAlways("addPropertyDetails: NOT PROCESSED propObj=" + propObj.toString(2) );
		} else  {
			
			List<String> required = new LinkedList<>();
			if(definition!=null) {
				required = Config.getListAsObject(definition, REQUIRED).stream().map(Object::toString).collect(toList());
				
				APIModel.addExternalReferences(definition);

			}
			
			LOG.debug("addPropertyDetails: properties={}" , propObj.keySet() );

			for(String propName : propObj.keySet()) {
				JSONObject property = propObj.optJSONObject(propName);
								
				LOG.debug("addPropertyDetails: property={} definition={}" , propName, property );
								
				if(property==null) {
					if(!APIModel.isAsyncAPI() ) { // TBD CHECK ON POSSIBLE properties for async || !propName.contentEquals("name")) {
						String className = Utils.getLastPart(propObj.get(propName).getClass().toString(), ".");
						Out.printOnce("... ERROR: expecting property '{}' of '{}' to be a JSON object, found {}", propName, this.getName(), className);
						continue;
					} else {
						property = new JSONObject();
					}
				}
	
				if(property.has(TITLE)) {
					Out.printOnce(".. found unexpected EMBEDDED {}", property.optString(TITLE));
				} 	
				
				LOG.debug("addPropertyDetails: property={} isEmpty={}" , propName, property.isEmpty() );

				if(property.isEmpty()) continue;

				String type = APIModel.typeOfProperty(property, propName);		
				
				LOG.debug("Node::addPropertyDetails: #1 property={} typeOfProperty={}" , propName, type );

				if(APIModel.isAddedType(type)) {
					LOG.debug("Node::addPropertyDetails: #2 property={} typeOfProperty={}" , propName, type );
					property = new JSONObject();
					property.put(TYPE, "object");
					property.put(REF, "#/components/schemas/" + type);
					
					LOG.debug("###### addProperties: isAddedType new property={}", property);

					this.additional_edges.add(propName);

//					Node to = getOrAddNode(graph, type);
//					Edge edge = new Edge(from, propertyName, to, cardinality, isRequired);
//					LOG.debug("############# addProperties: edge={} isRequired={}", edge, isRequired);
//
				}

				
				// if(propName.contentEquals("itemTotalPrice")) LOG.debug("Node::addProperties: type={} cproperty={}", type, property );						

				String coreType = APIModel.removePrefix(type);
					
				LOG.debug("addPropertyDetails: property={} coreType={}" , propName, coreType );

				boolean isRequired = APIModel.isRequired(this.resource, propName) || required.contains(propName);
				String cardinality = APIModel.getCardinality(property, isRequired);
	
				LOG.debug("Node::addProperties: node={} propertyName={} type={} property={}", this.getName(), propName, type, property);

				if(property.has(REF) && APIModel.isArrayType(type)) {
					LOG.debug("Node::addProperties: isArrayType node={} propertyName={} type={} property={}", this.getName(), propName, type, property);
					
					property = APIModel.getDefinition(type);

					type = APIModel.getTypeName(property);
					coreType = APIModel.removePrefix(type);
					
					LOG.debug("Node::addProperties: isArrayType #2 type={} coreType={} property={} isSimpleType={}", type, coreType, property, APIModel.isSimpleType(type));						
				} 

				// if(propName.contentEquals("itemTotalPrice")) LOG.debug("Node::addProperties: type={} coreType={} property={} isSimpleType={}", type, coreType, property, APIModel.isSimpleType(type));						

				if(type.isEmpty()) continue;
				
				boolean seen = properties.stream().map(Property::getName).anyMatch(propName::contentEquals);
				
				if(isRequired) {
					LOG.debug("addPropertyDetails: node={} property={} isRequired={} contains={}", 
							this, propName,
							APIModel.isRequired(this.resource, propName),
							required.contains(propName));

				}
				
				if(!seen) {
					
					Property propDetails = new Property(propName, coreType, cardinality, isRequired, property.optString(DESCRIPTION), visibility );
					
					LOG.debug("### addPropertyDetails: node={} property={} " , this, propDetails );

					if(property.has(ENUM)) {
						
						List<Object> elements = Config.getListAsObject(property,ENUM);

						propDetails.addEnumValues( elements.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toList()) );

						LOG.debug("addPropertyDetails: property={} values={}" , propName, propDetails.getValues() );

						boolean candidateNullable = elements.stream().anyMatch(Objects::isNull);
						
						if(property.optBoolean(NULLABLE) && candidateNullable) propDetails.setNullable();

					}
					
					this.addProperty( propDetails );
					
				} else {
					LOG.debug("addPropertyDetails: node={} property={} seen={}" , this, propName, seen );

				}
				
				if(APIModel.isEnumType(type) && !enums.contains(coreType)) {
					enums.add(coreType);
				}
				
			}
		} 

	}

	Optional<Set<String>> allDiscriminators = Optional.empty();
	
	@LogMethod(level=LogLevel.DEBUG)
	private Set<String> getAllDiscriminators() {
		if(this.allDiscriminators.isEmpty()) {
			// allDiscriminators = Optional.of( new HashSet<>() );
			this.allDiscriminators = Optional.of( Node.getAllDiscriminatorsHelper(this));
		}
		
		LOG.debug("getAllDiscriminators: node={} allDiscriminators={}", this, this.allDiscriminators);

		return this.allDiscriminators.get();
	}

	
	public static Set<String> getAllDiscriminatorsHelper(Node resource) {
		Set<String> res = new HashSet<>();	
		
		res.addAll( resource.getInheritance().stream()
								.map(Node::getNodeByName)
								.filter(Objects::nonNull)
								.map(Node::getAllDiscriminators)
								.flatMap(Set::stream)
								.collect(toSet()) );
		
		res.addAll( resource.getLocalDiscriminators() );
		
		LOG.debug("getAllDiscriminatorsHelper: node={} inheritance={} res={}", resource, resource.inheritance.isPresent(), res);

		return res;

	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addAllOfs(Property.Visibility visibility) {
		if(Config.getBoolean(EXPAND_ALL_PROPERTIES_FROM_ALLOFS)) {
			JSONArray allOfs = APIModel.getAllOfForResource(this.resource);
			addAllOfs(allOfs, visibility);
		}
		
//		JSONArray allOfs = APIModel.getAllOfForResource(this.resource);
//		addAllOfs(allOfs, visibility);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addOneOfs(Property.Visibility visibility) {		
		JSONArray oneOfs = APIModel.getOneOfForResource(this.resource);
		addOneOfs(oneOfs, visibility);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addOneOfs(JSONArray oneOfs, Property.Visibility visibility) {
		
		LOG.debug("addOneOfs: node={} addOneOfs={}", this, oneOfs.toString(2));

		oneOfs.forEach(oneOf -> {
			if(oneOf instanceof JSONObject) {
				JSONObject definition = (JSONObject) oneOf;
				addOneOfObject(definition, visibility);
			}
		});
				
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addAllOfs(JSONArray allOfs, Property.Visibility visibility) {
		
		LOG.debug("addAllOfs: node={} addAllOfs={}", this, allOfs.toString(2));

		allOfs.forEach(allOf -> {
			if(allOf instanceof JSONObject) {
				JSONObject definition = (JSONObject) allOf;
				addAllOfObject(definition, visibility);
			}
		});
				
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void addAllOfObject(JSONObject definition, Property.Visibility visibility) {
		
		LOG.debug("addAllOfs: node={} definition={}", this, definition);

		LOG.debug("addAllOfs: node={} EXPAND_ALL_PROPERTIES_FROM_ALLOFS={} EXPAND_INHERITED={} INCLUDE_INHERITED={}", 
				this, 
				Config.getBoolean(EXPAND_ALL_PROPERTIES_FROM_ALLOFS),
				Config.getBoolean(EXPAND_INHERITED),
				Config.getBoolean(INCLUDE_INHERITED)
				);

		if(Config.getBoolean(EXPAND_ALL_PROPERTIES_FROM_ALLOFS)) {
			if(definition.has(REF)) {
				String type = APIModel.getTypeByReference(definition.optString(REF));
					
				if(Config.getBoolean(EXPAND_INHERITED)) {
					this.addInheritance(type);
				}
					
				if(Config.getBoolean(INCLUDE_INHERITED)) {
					JSONObject obj = APIModel.getDefinitionBySchemaObject(definition);
					addAllOfObject(obj,Property.VISIBLE_INHERITED);
				}	
			}
		} else {
			if(definition.has(REF)) {
				String type = APIModel.getTypeByReference(definition.optString(REF));
					
				if(Config.getBoolean(EXPAND_INHERITED)) {
					this.addInheritance(type);
				}
					
				if(Config.getBoolean(INCLUDE_INHERITED)) {
					JSONObject obj = APIModel.getDefinitionBySchemaObject(definition);
					addAllOfObject(obj,Property.VISIBLE_INHERITED);
				}	
			}
		}
		
		if(definition.has(PROPERTIES)) {
			JSONObject obj = definition.optJSONObject(PROPERTIES); // APIModel.getPropertyObjectBySchemaObject(definition);			
			if(obj!=null) {	
				LOG.debug("addAllOfs: node={} obj={}", this, obj);

				addPropertyDetails(obj,visibility,definition);				
			}
		}
		
		if(definition.has(ALLOF)) {
			addAllOfs(definition.optJSONArray(ALLOF), visibility);
		}
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addOneOfObject(JSONObject definition, Property.Visibility visibility) {
		
		LOG.debug("addOneOfObject: node={} definition={}", this, definition);

		if(definition.has(REF)) {
			String type = APIModel.getTypeByReference(definition.optString(REF));
				
			if(Config.getBoolean(INCLUDE_INHERITED)) {
				JSONObject obj = APIModel.getDefinitionBySchemaObject(definition);
				addOneOfObject(obj,Property.VISIBLE_INHERITED);
			}	
		}
	
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addDiscriminatorMapping() {		
//		
//		LOG.debug("##### addDiscriminatorMapping: node={} inheritance={}", this.getName(), this.inheritance);
//		
//		JSONObject mapping = APIModel.getMappingForResource(this.resource);
//		
//		Set<String> inheritedMapping = this.getAllDiscriminatorMapping();
//		
//		LOG.debug("##### addDiscriminatorMapping: node={} inheritance={}", this.getName(), this.inheritance);
//		LOG.debug("##### addDiscriminatorMapping: node={} inheritedMapping={}", this.getName(), inheritedMapping);
//
//		LOG.debug("addDiscriminatorMapping: node={} mapping  = {}",  this.getName(), mapping);
//		
//		// LOG.debug("addDiscriminatorMapping:: node={} inheritedDiscriminators={}", this.getName(), this.inheritedDiscriminatorMapping);	
//
//		Set<String> mappings = new HashSet<>();
//		if(mapping!=null && !mapping.isEmpty()) {
//			mappings.addAll(mapping.keySet());
//		}
//		
//		if(!mappings.isEmpty() || !inheritedMapping.isEmpty()) {
//						
//			// this.discriminatorMapping.addAll(mappings);
//			// this.discriminatorMapping.addAll(inheritedMapping);
//
////			this.externalDiscriminatorMapping.addAll(inheritedMapping);
////			this.externalDiscriminatorMapping.remove(this.resource);
//			
//			LOG.debug("addDiscriminatorMapping: node={} local  = {}",  this.getName(), this.getLocalDiscriminators());
////			LOG.debug("addDiscriminatorMapping: node={} external = {}",  this.getName(), this.externalDiscriminatorMapping);
//
////			if(this.discriminatorMapping.size()==1 && this.externalDiscriminatorMapping.size()>0) {
//			if(this.getLocalDiscriminators().size()==1 ) {
//				String localDiscriminator = this.getLocalDiscriminators().iterator().next();
//				LOG.debug("addDiscriminatorMapping: node={} mapping={}",  this.getName(), localDiscriminator);
//				Optional<Property> optAtType = this.properties.stream().filter(p -> p.getName().contentEquals(ATTYPE)).findFirst();
//				if(optAtType.isPresent()) {
//					optAtType.get().setDefaultValue(localDiscriminator);
//					
//					LOG.debug("addDiscriminatorMapping: node={} setDefaultValue={}",  this.getName(), localDiscriminator);
//
//				}
//			}
//
//		}
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void resetPlacement() {
		this.placements = new HashMap<>();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addDescription() {   	
	    this.description = APIModel.getDescription(this.resource);
	}

	
	public List<Property> getProperties() {
		return this.properties;
	}

	public List<Property> getInheritedProperties(Graph<Node, Edge> graph) {
		List<Property> res = new LinkedList<>();
		List<Node> related = graph.edgesOf(this).stream().filter(Edge::isAllOf).map(e->e.related).collect(toList());
		
		LOG.debug("getInheritedProperties:: node={} edges={}", this.getName(), graph.edgesOf(this));
		LOG.debug("getInheritedProperties:: node={} related={}", this.getName(), related);
		
		related.forEach(n -> {
			if(n==this) return;
			res.addAll( n.getInheritedProperties(graph));
			res.addAll( n.getProperties());
		});
		
		return res;
		
	}
	
	public List<OtherProperty> getOtherProperties() {
		return this.otherProperties;
	}

	public String getDescription() {
		
		LOG.debug("Node::getDescription:: node={} description={}",  this.getName(), this.description);
		
		return this.description;
	}
	
	public String toString() {
		return this.resource;
	}
	
	public int hashCode() {
		int res = this.resource.hashCode();
		LOG.trace("Node::hashCode: node=" + this.toString() + " res=" + res);

		return res;
	}
	
	public boolean equals(Object obj) {
		boolean res=false;
		if(obj instanceof Node) {
			res = ((Node) obj).getName().contentEquals(this.getName());
		} 
		LOG.trace("Node::equals: node=" + this.toString() + " obj=" + obj + " res=" + res);
		return res;
	}
	
	public String getName() {
		return this.resource;
	}

	public List<String> getEnums() {
		return this.enums;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType() {
		return isSimpleType(this.getName()) && !isEnumType(this.getName());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isEnumType(String type) {
		return APIModel.isEnumType(type);
	}


	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType(String type) {
		
		List<String> simpleEndings = Config.getSimpleEndings();
				
		boolean simpleEnding = simpleEndings.stream().anyMatch(type::endsWith);
		
		return  simpleEnding 
				|| APIModel.isSpecialSimpleType(type) 
				|| APIModel.isSimpleType(type) 
				|| Config.getSimpleTypes().contains(type) 
				|| APIModel.isEnumType(type);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Property> getReferencedProperties() {
		return properties.stream()
				.filter(this::isReferenceType)
				.collect(toSet());
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isReferenceType(Property property) {
		return !isSimpleType(property.getType());
	}
	
	public boolean startsWith(Node node) {
		return this.getName().startsWith(node.getName());
	}

	@Override
	public int compareTo(Object o) {
		if(o instanceof Node) {
			Node n = (Node)o;
			return this.getName().compareTo(n.getName());
		} else {
			return -1;
		}	
	}
	
	public String getDetails() {
		StringBuilder res = new StringBuilder();
		
		properties.stream().forEach( prop -> res.append(prop + " ")) ;
		
		return res.toString();
	}
	
	public boolean isEnumNode() {
		return this instanceof EnumNode;
	}

	public boolean isDiscriminatorNode() {
		return this instanceof DiscriminatorNode;
	}
	
	public boolean inheritsFrom(Graph<Node, Edge> graph, Node candidate) {
		// return graph.getAllEdges(this, candidate).stream().anyMatch(edge -> edge instanceof AllOf);
		return graph.getAllEdges(this, candidate).stream().anyMatch(Edge::isInheritance);

	}

	public void addInheritance(String type) {
		if(!this.inheritance.isPresent()) this.inheritance = Optional.of(new HashSet<>());
		this.inheritance.get().add(type);
	}
	
	public void addInheritance(Set<String> types) {
		if(!this.inheritance.isPresent()) this.inheritance = Optional.of(new HashSet<>());
		this.inheritance.get().addAll(types);
	}

	public Set<String> getInheritance() {
		if(this.inheritance.isPresent()) 
			return this.inheritance.get();
		else
			return new HashSet<>();
	}
	
	
	public Set<String> getDiscriminatorMapping() {
//		Set<String> all = this.discriminatorMapping;
//		
//		return all;
		return this.getAllDiscriminators();
	}

	public Set<String> getInheritedDiscriminatorMapping() {		
		Set<String> inherited = this.getAllDiscriminatorMapping();
				
//		Set<String> inherited = this.getAllDiscriminators();
		return inherited;
	}

	
	public Set<String> getAllDiscriminatorMapping() {		
		Set<String> all = new HashSet<>();
		
		all.addAll(this.getLocalDiscriminators());
		if(this.externalDiscriminatorMapping.isPresent()) all.addAll(this.externalDiscriminatorMapping.get());
				
		LOG.debug("getAllDiscriminatorMapping: node={} all={}", this, all);

		return all;
	}
	
	public Set<String> getExternalDiscriminatorMapping() {
		if(this.externalDiscriminatorMapping.isPresent())
			return this.externalDiscriminatorMapping.get();
		else 
			return new HashSet<>();
	}

	Set<Node> circleNodes = new HashSet<>();
	public void addCircleElements(Collection<Node> circle) {
		circleNodes.addAll(circle);
	}
	
	public boolean isPartOfCircle() {
		return !circleNodes.isEmpty();
	}
	
	public Set<Node> getCircleNodes() {
		return this.circleNodes;
	}

	public String getInline() {
		return inline;
	}

	public void setInheritedDiscriminatorMapping(Set<String> inheritedDiscriminators) {
		LOG.debug("setInheritedDiscriminatorMapping: node={} inheritedDiscriminators={}",  this.getName(), inheritedDiscriminators);
		if(this.externalDiscriminatorMapping.isPresent()) 
			this.externalDiscriminatorMapping.get().addAll( inheritedDiscriminators );
	}
	
	static private Node getNodeByName(String node) {
		return nodeMap.get(node);
	}

	public void clearInheritedDiscriminatorMapping() {
		if(this.externalDiscriminatorMapping.isPresent()) 
			this.externalDiscriminatorMapping.get().clear();
		
		LOG.debug("clearInheritedDiscriminatorMapping: node={} externalDiscriminatorMapping={}",  
					this.getName(), this.externalDiscriminatorMapping);

	}
	
	public void setDiscriminatorDefault() {
		Optional<Property> atType = properties.stream().filter(p -> p.getName().contentEquals(ATTYPE)).findAny();
		
		if(atType.isEmpty()) {
			atType = getInheritedProperties().stream().filter(p -> p.getName().contentEquals(ATTYPE)).findAny();
			if(atType.isPresent()) {
				Property p = new Property(atType.get());
				this.addProperty(p);
				atType = Optional.of(p);
			}
		}
		
		if(atType.isPresent()) {
			atType.get().setDefaultValue(this.getName());
			LOG.debug("setDiscriminatorDefault: node={} default={}",  this.getName(), atType.get().getDefaultValue());
		} else {
			LOG.debug("setDiscriminatorDefault: node={} atType={}",  this.getName(), atType);
		}
	}
	
	public void updatePropertiesFromFVO() {
		String fvoName = this.getName() + "_FVO";
		
		Set<String> fvoNames = nodeMap.keySet().stream().filter(s -> s.startsWith(fvoName)).collect(toSet());
		
		if(fvoNames.isEmpty()) return;
		
		LOG.debug("updatePropertiesFromFVO: node={} fvoNames={}",  this.getName(), fvoNames );

		fvoNames.forEach(fvo -> {
			if(!nodeMap.containsKey(fvo)) return;
			Node fvoNode = nodeMap.get(fvo);
				
			List<Property> requiredProperties = fvoNode.getProperties().stream().filter(Property::isRequired).collect(toList());

			List<String> requiredPropertiesByName = requiredProperties.stream().map(Property::getName).collect(toList());

			LOG.debug("updatePropertiesFromFVO: node={} requiredProperties={} requiredPropertiesByName={}",  this.getName(), requiredProperties, requiredPropertiesByName );

			requiredProperties.stream()
				.map(Property::getName)
				.map(this::getPropertyByName)
				.filter(Objects::nonNull)
				.forEach(Property::setRequired);
				// .forEach(p -> LOG.debug("p={}", p));

			
			if(Config.getBoolean("includeAsRequiredIfNotInPost")) {
				
				LOG.debug("updatePropertiesFromFVO: includeAsRequiredIfNotInPost node={}",  this.getName() );

				Set<String> fvoProperties = fvoNode.getProperties().stream().map(Property::getName).collect(toSet());
				
				Set<Property> additionalRequired = this.properties.stream().filter(p -> !fvoProperties.contains(p.getName())).collect(toSet());
				
				additionalRequired.forEach(Property::setRequired);
				
			}
				
		});
		
		if(!isAPIResource()) return;
		
		List<String> defaultMandatory = Config.get("includeDefaultMandatoryProperties");	
		if(!defaultMandatory.isEmpty()) {
			
			LOG.debug("updatePropertiesFromFVO: node={} defaultMandatory={}",  this.getName(), defaultMandatory );

			long found = this.properties.stream()
								.map(Property::getName)
								.filter(defaultMandatory::contains)
								.count();
			
			if(found==defaultMandatory.size()) {
				defaultMandatory.stream()
						.map(this::getPropertyByName)
						.filter(Objects::nonNull)
						.forEach(Property::setRequired);
			}
			
		}
	}
	
	private boolean isAPIResource() {
		return APIModel.getResources().contains(this.getName());
	}

	public Property getPropertyByName(String name) {
		Property res = this.properties.stream().filter(p -> p.getName().contentEquals(name)).findFirst().orElse(null);
		LOG.debug("getPropertyByName: name={} res={}",  name, res);
		return res;

	}

	Optional<Set<String>> deepInheritance = Optional.empty();

	public Set<String> getDeepInheritance() {
		
		if(!deepInheritance.isEmpty()) return deepInheritance.get();
		
		deepInheritance = deepInheritance.of(new HashSet<>());
		
		Set<String> inheritance = this.getInheritance();
		
		Set<String> indirectInheritance = inheritance.stream()
												.map(Node::getNodeByName)
												.filter(Objects::nonNull)
												.map(Node::getDeepInheritance)
												.flatMap(Set::stream).collect(toSet());
		
		inheritance.addAll(indirectInheritance);
		
		LOG.debug("getDeepInheritance: resource={} inheritance={}",  this.resource, inheritance);
		
		deepInheritance = deepInheritance.of(inheritance);
		
		return inheritance;
	}

	private boolean isVendorExtension=false;
	public void setVendorExtension() {
		this.isVendorExtension=true;
	}

	public boolean getVendorExtension() {
		return this.isVendorExtension;
	}

	public void setVendorAttributeExtension(List<String> extensionAttributes) {
		
		LOG.debug("Node:: {} extendedAttributes {}", this.getName(), extensionAttributes);
		
		this.properties.stream()
			.filter(prop -> extensionAttributes.contains(prop.getName()))
			.forEach(Property::setVendorExtension);
	}

	public void setVendorAttributeExtension(String propName, boolean required, boolean type, boolean cardinality) {
		
		LOG.debug("Node:: {} setVendorAttributeExtension {} {}", this.getName(), propName, required);

		Property p = this.getPropertyByName(propName);
		if(p!=null) {
			p.setVendorExtension();
			if(required) p.setRequiredExtension();
			if(type) p.setTypeExtension();
			
			if(cardinality) p.setCardinalityExtension();

		}
		
		LOG.debug("setVendorAttributeExtension: node={} p={} property={} required={}",  this.getName(), p, propName, required);
		
	}
	
	List<String> discriminatorExtension = new LinkedList<>();
	public void setVendorDiscriminatorExtension(List<String> discriminators) {
		this.discriminatorExtension=discriminators;
	}

	public List<String> getDiscriminatorExtension() {
		return this.discriminatorExtension;
	}

	public void clearInheritance() {
		if(this.inheritance.isPresent()) this.actualInheritance.addAll(this.inheritance.get());
		this.inheritance = Optional.empty();
	}

	public Set<String> getActualInheritance() {
		Set<String> res = this.actualInheritance;
		res.addAll(this.getInheritance());
		return res;
	}

	List<String> inheritanceExtension = new LinkedList<>();
	public void setVendorInheritanceExtension(List<String> inheritance) {
		LOG.debug("setVendorInheritanceExtension: node={} inheritance={}", this.getName(), inheritance);
		this.inheritanceExtension=inheritance;
	}
	
	public List<String> getInheritanceExtension() {
		return this.inheritanceExtension;
	}

	public Set<Property> getAllProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	public Collection<String> getPropertyNames() {
		return properties.stream().map(Property::getName).collect(toList());
	}

	public Collection<Property> getInheritedProperties() {
		Map<String, Property>  inherited = new HashMap<>();
		
		Set<String> inheritance = this.getInheritance();
		
		LOG.debug("getInheritedProperties: node={} inheritance={}", this.getName(), inheritance);

		inheritance.forEach(n -> {
			Node node = Node.getNodeByName(n);
			if(node!=null) {
				Collection<Property> properties = node.getProperties();
				properties.forEach(p -> {
					inherited.put(p.getName(), p);
				});
			}
		});
		
		LOG.debug("getInheritedProperties: node={} keys={}", this.getName(), inherited.keySet());

		return inherited.values();
	}

	boolean isDynamic = false;
	public void setDynamic(boolean status) {
		this.isDynamic = status;		
	}
	
	public boolean isDynamic() {
		if(this.isDynamic) LOG.debug("Node::isDynamic: node={} isDyanmic={}", this.getName(), this.isDynamic);

		return this.isDynamic;		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getClassName() {
		String className = APIModel.getMappedResource(this.resource);

		if(this.isDynamic || APIModel.isAsyncAPI()) {
			className = className.replaceAll("_[0-9]*$", "");
			LOG.debug("Node: name={} className={}", this.resource, className);
		}
		
		return className;
		
	}
}


