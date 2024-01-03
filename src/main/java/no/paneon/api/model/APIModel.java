package no.paneon.api.model;

import java.util.Arrays;
import java.util.Collection;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;

import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class APIModel {

	static final Logger LOG = LogManager.getLogger(APIModel.class);

	private static JSONObject swagger;

	static final Map<String,String> formatToType = new HashMap<>();
	static final Map<String,String> typeMapping = new HashMap<>();

	private static JSONObject resourceMapping;
	private static JSONObject reverseMapping;

	public static final List<String> ALL_OPS = List.of("GET", "POST", "DELETE", "PUT", "PATCH");

	private static final String CARDINALITY_REQUIRED_ONE = "cardinalityOne";
	private static final String CARDINALITY_ZERO_OR_ONE  = "cardinalityZeroOne"; 
	
	private static final String RESOURCE_MAPPING = "resourceMapping";
	
	private static final String FLATTEN_INHERITANCE = "expandInherited";
	private static final String TITLE = "title";
	private static final String FORMAT = "format";
	private static final String TYPE = "type";
	private static final String ARRAY = "array";
	private static final String OBJECT = "object";
	private static final String REF = "$ref";
	private static final String ITEMS = "items";
	private static final String PATHS = "paths";
	private static final String PROPERTIES = "properties";
	private static final String ENUM = "enum";
	private static final String RESPONSES = "responses";
	private static final String SCHEMA = "schema";
	private static final String DESCRIPTION = "description";
	
	private static final String REQUESTBODY = "requestBody";

	private static final String MIN_ITEMS = "minItems";
	private static final String MAX_ITEMS = "maxItems";

	private static final String REQUIRED = "required";

	private static final String EXAMPLE  = "example";
	private static final String EXAMPLES = "examples";
	private static final String VALUE    = "value";

	private static final String NOTIFICATIONS = "notifications";

	private static final String ALLOF = "allOf";
	private static final String ONEOF = "oneOf";

	private static final String DISCRIMINATOR = "discriminator";
	private static final String MAPPING = "mapping";

	private static final String NEWLINE = "\n";
	private static String swaggerSource;
	
	private static Map<String, JSONObject> resourcePropertyMap = new HashMap<>();

    private static boolean firstAPImessage=true;

    private static Map<String,Counter> operationCounter = null;
    
	private static Map<String,JSONObject> externalDefinitions = new HashMap<>();
	private static Map<String,JSONObject> externals = new HashMap<>();

	static Map<String,List<String>> pathsForResources = new HashMap<>();
	static Map<String,JSONObject> flattened = new HashMap<>();
	static Map<String,JSONObject> resourceMapExpanded = new HashMap<>();

	static JSONObject allDefinitions = new JSONObject();
	static Set<String> seenRefs = new HashSet<>();

	private static Set<String> typeWarnings = new HashSet<>();

    public static String getSource() {
    	return swaggerSource;
    }
    
	private APIModel() {
		// resourceMapping = Config.getConfig(RESOURCE_MAPPING);
		// reverseMapping = generateReverseMapping(resourceMapping);
		clean();

	}

	public APIModel(JSONObject api) {
		this();
		setSwagger(api);
	}

	private APIModel(String source) {
		this();
		setSwagger(Utils.readJSONOrYaml(source));
		swaggerSource=source;
	}

	public APIModel(String source, InputStream is) {
		this();
		try {
			APIModel.setSwaggerSource(source);
			setSwagger(Utils.readJSONOrYaml(is));
			swaggerSource=source;

		} catch(Exception ex) {
			Out.println("... unable to read API specification from source '" + source + "'");
			Out.println("... error=" + ex.getLocalizedMessage());
			ex.printStackTrace();

			System.exit(0);
		}
		
	}
	
	public APIModel(String filename, File file) {
		this();
		try {
			InputStream is = new FileInputStream(file);
			APIModel.setSwaggerSource(filename);
			setSwagger(Utils.readJSONOrYaml(is));
			swaggerSource=filename;

		} catch(Exception ex) {
			Out.println("... unable to read API specification from file '" + filename + "'");
			Out.println("... error=" + ex.getLocalizedMessage());
			// ex.printStackTrace();

			System.exit(0);
		}
		
	}

	public String toString() {
		return swagger.toString(2);
	}

	public static void clean() {
		allDefinitions = new JSONObject();	
		resourcePropertyMap = new HashMap<>();
		swagger = null;
		firstAPImessage=true;
		operationCounter = null;
		
		externals = new HashMap<>();
		externalDefinitions = new HashMap<>();
		
		resourceMapExpanded = new HashMap<>();
		
		resourcePropertyMap = new HashMap<>();

	    firstAPImessage=false;

	    operationCounter = null;
	    
		pathsForResources = new HashMap<>();
		flattened = new HashMap<>();
		resourceMapExpanded = new HashMap<>();
		
		_getResources = null;
		
		seenRefs.clear();
		typeWarnings.clear();
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static void setSwagger(JSONObject api) {
		
		clean();
		
		swagger = api;

		LOG.debug("setSwagger:: keys={}", swagger.keySet());

		fixResourceMapping();
		
		// rearrangeDefinitions(swagger);
		
		// refactorEmbeddedTitles();

	}

	private static void fixResourceMapping() {
		LOG.debug("fixResourceMapping:: resourceMapping={} reverseMapping={}", resourceMapping, reverseMapping);

		Collection<String> definitions = APIModel.getAllDefinitions();
				
		if(resourceMapping!=null) {
			Predicate<String> notInAPI = s -> !definitions.contains(s);
			Collection<String> mappingNotRelevant = resourceMapping.keySet().stream().filter(notInAPI).collect(toSet());		
			mappingNotRelevant.stream().forEach(resourceMapping::remove);
		}
		
		if(reverseMapping!=null) {
			definitions.forEach(reverseMapping::remove);
		}
		
		LOG.debug("fixResourceMapping:: resourceMapping={} reverseMapping={}", resourceMapping, reverseMapping);

	}

	private static void refactorEmbeddedTitles() {
		LOG.debug("refactorEmbeddedTitles");

		getAllDefinitions().forEach(APIModel::refactorEmbeddedTitles);
		
//		
//		for(String type : getAllDefinitions() ) {
//			JSONObject definition = getDefinition(type);
//			for(String property : APIModel.getProperties(type) ) {
//				JSONObject propObj = APIModel.getPropertySpecification(type, property);
//				if(propObj.has(TITLE)) {
//					String title=propObj.optString(TITLE);
//					LOG.debug("refactorEmbeddedTitles::embedded {}",  title);
//					String ref=addResource(title,propObj);
//					LOG.debug("refactorEmbeddedTitles::embedded {} ref={}",  title, ref);
//
//					resetWithReference(propObj,ref);
//		
//				} else if(propObj.has(ITEMS)) {
//					JSONObject items=propObj.optJSONObject(ITEMS);
//					if(items!=null && items.has(TITLE) && propObj.optString(TYPE).contentEquals(ARRAY)) {
//						String title=items.optString(TITLE);
//						LOG.debug("refactorEmbeddedTitles::embedded ARRAY title={}", title);
//						LOG.debug("refactorEmbeddedTitles::embedded obj={}", propObj.toString(2));
//
//					}
//
//				}
//			}
//		}
	}
	
	private static void refactorEmbeddedTitles(String type) {
		LOG.debug("refactorEmbeddedTitles resource={}", type);

		JSONObject definition = getDefinition(type);
		for(String property : APIModel.getProperties(type) ) {
			JSONObject propObj = APIModel.getPropertySpecification(type, property);
			if(propObj.has(TITLE)) {
				String title=propObj.optString(TITLE);
				LOG.debug("refactorEmbeddedTitles::embedded {}",  title);
				String ref=addResource(title,propObj);
				LOG.debug("refactorEmbeddedTitles::embedded {} ref={}",  title, ref);

				resetWithReference(propObj,ref);
				
				refactorEmbeddedTitles(title);
				
			} else if(propObj.has(ITEMS)) {
				JSONObject items=propObj.optJSONObject(ITEMS);
				if(items!=null && items.has(TITLE) && propObj.optString(TYPE).contentEquals(ARRAY)) {
					String title=items.optString(TITLE);
					LOG.debug("refactorEmbeddedTitles::embedded ARRAY title={}", title);
					LOG.debug("refactorEmbeddedTitles::embedded obj={}", propObj.toString(2));

					String ref=addResource(title,items);
					resetWithReference(items,ref);
					refactorEmbeddedTitles(title);

				}

			}
		}
	}


	private static void resetWithReference(JSONObject obj, String ref) {
		final Set<String> keys = obj.keySet().stream().collect(Collectors.toSet());
		for(String key : keys) {
			obj.remove(key);
		}
		obj.put(REF, ref);
	}

	private static String addResource(String title, JSONObject obj) {
		obj = new JSONObject(obj.toString());
		JSONObject embedded = swagger.optJSONObject("embedded");
		if(embedded==null) {
			swagger.put("embedded", new JSONObject());
			embedded = swagger.optJSONObject("embedded");
		}
		embedded.put(title, obj); // SIMPLE
		allDefinitions.put(title, obj);
		
		return "#/embedded/" + title;
		
	}

	private static void rearrangeDefinitions(JSONObject api) {
		
		LOG.debug("rearrangeDefinitions:: keys={}", api.keySet());
		
		if(true) return;  // TBD 2023-06-18
		
		for(String type : getAllDefinitions() ) {
			JSONObject definition = getDefinition(type);
			
			if(definition!=null && !definition.has(PROPERTIES)) {
				if(definition.has(ALLOF)) {
					JSONArray rewrittenAllOfs = new JSONArray();

					JSONArray allOfs = definition.optJSONArray(ALLOF);
					allOfs.forEach(allOf -> {
						if(allOf instanceof JSONObject) {
							JSONObject obj = (JSONObject) allOf;
							if(obj.has(REF)) {
								rewrittenAllOfs.put(obj);
							} else if(obj.has(PROPERTIES)) {
								definition.put(PROPERTIES, obj.get(PROPERTIES));
								if(obj.has(REQUIRED)) definition.put(REQUIRED, obj.get(REQUIRED));
								if(obj.has(DESCRIPTION) && !definition.has(DESCRIPTION)) {
									definition.put(DESCRIPTION, obj.get(DESCRIPTION));
								}
								if(obj.has(TYPE)) definition.put(TYPE, obj.get(TYPE));
								
								LOG.debug("rearrangeDefinitions:: rearrange type={} obj={}", type, definition.toString(2));
							} else {
								rewrittenAllOfs.put(obj);
							}
						} else {
							rewrittenAllOfs.put(allOf);
							LOG.debug("rearrangeDefinitions:: unexpected array element for type={} element={}", type, allOf);
						}
					});
					
					definition.put(ALLOF, rewrittenAllOfs);
					
					JSONObject newDef = getDefinition(type);
					
					LOG.debug("rearrangeDefinitions:: type={} old={} new={}", type, definition.keySet(), newDef.keySet());

					
					LOG.debug("rearrangeDefinitions:: type={} old={} new={}", type, definition.keySet(), newDef.keySet());

				}
				
			}
	
		}
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static void setSwaggerSource(String filename) {
		LOG.debug("setSwaggerSource: filename={}", filename);
		swaggerSource = filename;
		
//		resourceMapping = Config.getConfig(RESOURCE_MAPPING);
//		reverseMapping = generateReverseMapping(resourceMapping);
		
	}


	static List<String> _getResources = null;
	
	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getResources() {

		if(_getResources!=null) return _getResources;
		
		List<String> res = getCoreResources(); 
							
		Predicate<String> notAlreadySeen = s -> !res.contains(s);
		
		if(Config.getBoolean("includeResourcesFromRules")) {
			
			List<String> fromRules = Config.getResourcesFromRules();
			
			LOG.debug("getResources:: fromRules={}", fromRules);
				
			LOG.debug("getResources:: {}", res);
			
			fromRules = fromRules.stream().filter(notAlreadySeen).collect(toList());

			LOG.debug("getResources:: {}", res);
	
			res.addAll(fromRules);
			
		}
		
		LOG.debug("getResources:: {}", res);

		
		if(!Config.getBoolean("excludeResourcesFromDiscriminators")) {
			
			final Set<String> discriminators = new HashSet<>();
			res.forEach(resource -> {
				discriminators.addAll(getDiscriminators(resource));
			});
			
			res.addAll(discriminators.stream().filter(notAlreadySeen).collect(Collectors.toSet()));
		}
		
		List<String> result = res;
		
//		if(!Config.getBoolean("keepMVOFVOResources")) {
//			Predicate<String> MVO_or_FVO = s -> s.endsWith("_FVO") || s.endsWith("_MVO");
//			result = res.stream().filter(MVO_or_FVO.negate()).toList();
//		} 
		result = APIModel.filterMVOFVO(res);
		
		LOG.debug("getResources:: {}", result);

		_getResources = result;
		
		return result;
		
	}
	
	
	private static Set<String> getDiscriminators(String resource) {
		Set<String> res = new HashSet<>();
		JSONObject definition = getDefinition(resource);
		if(definition.has(DISCRIMINATOR)) {
			definition = definition.optJSONObject(DISCRIMINATOR);
			if(definition!=null && definition.has(MAPPING)) {
				definition = definition.optJSONObject(MAPPING);
				String discriminators[] = JSONObject.getNames(definition);
				LOG.debug("getDiscriminators:: node={} discriminators={}", resource, discriminators);
				for(String d : discriminators) res.add(d);
			}
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getCoreResources() {

		List<String> res = new LinkedList<>();
		
		if(!isAsyncAPI()) {
			res = getAllResponses()
					.map(APIModel::getNormalResponses)
					.flatMap(List::stream)
					.map(APIModel::getResourceFromResponse)
					.flatMap(List::stream)
					.distinct()
					// .map(APIModel::getMappedResource)
					.collect(toList());
			
		} else {
			LOG.debug("getCoreResources:: processing async api");
			LOG.debug("getCoreResources:: swagger={}", swagger);
								    
		     Configuration configuration = Configuration.builder()
		             .jsonProvider(new JacksonJsonProvider())
		             .build();

		     DocumentContext jsonContext = JsonPath.using(configuration).parse(swagger.toString());
		     List<String> tags = jsonContext.read("$.tags..name");			

		     res = tags.stream().map(Utils::upperCaseFirst).collect(toList());
		     	     
		}
		
		return res;
		
	}

	// $.channels..message.['$ref']
	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAsyncMessageTypes() {

		List<String> res = new LinkedList<>();

		String api = swagger.toString();

		Configuration configuration = Configuration.builder()
				// .jsonProvider(new JacksonJsonProvider())
				.build();

		String query = "$.channels..message..['$ref']";

		JsonPath jsonpath = JsonPath.compile(query);

		Predicate<String> selectPayloadMessages = s -> !s.startsWith("30") && !s.startsWith("40") && !s.startsWith("50");

		List<String> msg = jsonpath.read(api, configuration );

		msg = msg.stream()
				.map(Utils::selectLastReferencePart)
				.filter(selectPayloadMessages)
				.collect(toList());
		
		LOG.debug("getAsyncMessages:: messages={}", Utils.joining(msg, "\n"));  

		return msg;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAsyncMessagesByOperation(String op) {

		List<String> res = new LinkedList<>();

		String api = swagger.toString();

		Configuration configuration = Configuration.builder()
				// .jsonProvider(new JacksonJsonProvider())
				.build();

		String query = "$..[?(@.operationId=='" + op + "')]..['$ref']";

		JsonPath jsonpath = JsonPath.compile(query);

		List<String> msg = jsonpath.read(api, configuration );

		LOG.debug("getAsyncMessagesByOperation:: messages={}", Utils.joining(msg, "\n"));  

		Predicate<String> selectPayloadMessages = s -> !s.startsWith("30") && !s.startsWith("40") && !s.startsWith("50");

		msg = msg.stream()
				.map(Utils::selectLastReferencePart)
				.filter(selectPayloadMessages)
				.collect(toList());
		
		return msg;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Map<String,AsyncResourceInfo> getAsyncDetails() {

		Map<String,AsyncResourceInfo> res = new HashMap<>();

		String api = swagger.toString();

		Configuration configuration = Configuration.builder()
				.jsonProvider(new JacksonJsonProvider())
				.build();

		DocumentContext jsonContext = JsonPath.using(configuration).parse(swagger.toString());
		List<String> tags = jsonContext.read("$.tags..name");			

		tags.forEach(tag -> {

			String resource = Utils.upperCaseFirst(tag);

			String query = String.format("$.channels..tags.[?(@.name=='%s')]", tag);

			JsonPath jsonpath = JsonPath.compile(query);

			List<String> paths = jsonpath.read(api, Configuration.builder().options(Option.AS_PATH_LIST).build() );

			paths.forEach(path -> {
				path = path.replace("['tags'][0]", "");

				Map<String,Object> obj = jsonContext.read(path);
				// DocumentContext obj = jsonContext.read(path);

				LOG.debug("getCoreResources:: path={} obj={}", path, obj.getClass());  

				LOG.debug("getCoreResources:: obj={}", obj.keySet());  

				String opId = obj.get("operationId").toString();
				String baseId = obj.get("operationId").toString().replaceAll("Request$", "").replaceAll("Reply$", "");

				LOG.debug("getCoreResources:: baseId={}", baseId );  

				if(!res.containsKey(resource)) {
					res.put(resource, new AsyncResourceInfo(resource));
				}
				
				if(opId.endsWith("Request") ) {
					res.get(resource).addRequest(baseId,obj,path);
				} else if (opId.endsWith("Reply") ) {
					res.get(resource).addReply(baseId,obj,path);
				}

			});

			LOG.debug("getCoreResources:: paths={}", paths);  

		});


		return res;
	}

	
	public static boolean isAsyncAPI() {
		boolean res = swagger!=null && swagger.has("asyncapi");
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAllNotifications() {
		return getPaths().stream()
				.filter(x -> x.startsWith("/listener/"))
				.map(x -> x.replaceAll(".*/([A-Za-z0-9.]*)", "$1"))
				.distinct()
				.map(Utils::upperCaseFirst)
				.collect(toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Stream<JSONObject> getAllResponses() {
		
//		LOG.debug("getAllResponses: getPaths={}", 
//				getPaths().stream()
//				.map(APIModel::getPathObjectByKey)
//				.map(APIModel::getChildElements)
//				.flatMap(List::stream)
//				.filter(APIModel::hasResponses)
//				.map(APIModel::getResponseEntity)
//				.map(APIModel::getNormalResponses)
//				.flatMap(List::stream)
//				.map(APIModel::getResourceFromResponse)
//				.flatMap(List::stream)
//				
//				.collect(toSet())
//				
//				);

		return getPaths().stream()
				.map(APIModel::getPathObjectByKey)
				.map(APIModel::getChildElements)
				.flatMap(List::stream)
				.filter(APIModel::hasResponses)
				.map(APIModel::getResponseEntity);

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getResponseEntity(JSONObject obj) {
		return obj.optJSONObject(RESPONSES);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getResourceFromResponse(JSONObject obj) {
		List<String> res = new LinkedList<>();

		if(obj.has(REF)) {
			String ref=obj.optString(REF);
			LOG.debug("getResourceFromResponse: ref={}", ref );

			if(swagger.optQuery(ref)!=null) {
				Object o = swagger.optQuery(ref);
				if(o != null && o instanceof JSONObject) obj = (JSONObject)o;
			}
			// obj = APIModel.getDefinitionByReference(obj.optString(REF));
		}

		LOG.debug("getResourceFromResponse: obj={}", obj );

		JSONObject schema = getSchemaFromResponse(obj);

		LOG.debug("getResourceFromResponse: schema={}", schema );

		if(schema!=null) {
			if(schema.has(REF)) {
				res.add(schema.getString(REF));
			} else {
				Object ref = schema.optQuery( "/" + ITEMS + "/" + REF);
				if(ref!=null) {
					res.add(ref.toString());
				}
			}
		}

		res = res.stream().map(str -> str.replaceAll("[^/]+/","")).collect(toList());

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getSubclassesByResources(List<String> coreResources) {
		List<String> res = coreResources.stream()
							.map(APIModel::getSubclassesByResource) 
							.flatMap(List::stream)
							.collect(toList());
		
		LOG.debug("getSubclassesByResources: coreResources={} res={}", coreResources, res);
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getSubclassesByResource(String resource) {
		List<String> res = new LinkedList<>();

		res = APIModel.getAllDefinitions().stream()
				.filter(subResource -> isSubclass(subResource,resource))
				.filter(APIModel::includeSubclass)
				.collect(toList());

		LOG.debug("getSubclassesByResource: resource={} res={}", resource, res);
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static boolean includeSubclass(String resource) {
		
		List<String> excludePattern = Config.getSubClassesExcludeRegexp();

		boolean exclude = excludePattern.stream().anyMatch(pattern -> resource.matches(pattern));
				
		return !exclude;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isSubclass(String subResource, String resource) {
		boolean res=false;
		
		// JSONObject sub = getDefinition(subResource);
		JSONArray allOfs = APIModel.getAllOfForResource(subResource);
		Iterator<Object> iter = allOfs.iterator();
		while(iter.hasNext()) {
			Object o = iter.next();
			if(o instanceof JSONObject) {
				res = res || isSubclass((JSONObject)o,resource);
			}
		}
		return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isSubclass(JSONObject refs, String resource) {
		boolean res=false;
		
		String ref = refs.optString(REF);
		
		res = !ref.isEmpty() && ref.endsWith("/" + resource);

		return res;
	}
	
	
	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getSchemaFromResponse(JSONObject respObj) {
		JSONObject res=null;
		// V3 navigation
		
		LOG.debug("getSchemaFromResponse respObj={}", respObj);

		if(respObj.has("content")) respObj=respObj.getJSONObject("content");
		Optional<String> application = respObj.keySet().stream().filter(key -> key.startsWith("application/json")).findFirst();
		if(application.isPresent()) {
			respObj=respObj.getJSONObject(application.get());
		}

		if(respObj.has(SCHEMA)) res=respObj.getJSONObject(SCHEMA);
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String removePrefix(String resource) {
		final String prefix = Config.getPrefixToRemove();
		final String replacement = Config.getPrefixToReplace();

		String res = resource.replaceAll(prefix,replacement);

		LOG.debug("removePrefix resource={} prefix={} res={}", resource, prefix, res);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	boolean isSimpleType(String type, String property) {
		boolean res=true;
		JSONObject propertySpecification = getPropertySpecification(type,property);

		if(propertySpecification==null) return res;

		if(propertySpecification.has(TYPE)) {
			String jsonType = propertySpecification.getString(TYPE);
			res = !jsonType.equals(OBJECT) && !jsonType.equals(ARRAY);
		} 

		if(propertySpecification.has(ITEMS)) propertySpecification=propertySpecification.getJSONObject(ITEMS);

		if(propertySpecification.has(REF)) {
			String referencedType = getReferencedType(type, property);
			res = isSimpleType(referencedType);
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isSimpleType(String type) {
		JSONObject definition = getDefinition(type);
		LOG.debug("isSimpleType: type={} definition={}", type, definition);
		
		boolean res=isSimpleType(definition);
		
		LOG.debug("isSimpleType: type={} res={}",  type, res);
		
		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isSimpleType(JSONObject definition) {
		boolean res=true;

		LOG.debug("isSimpleType: definition={}", definition);

		if(definition!=null) {
			if(definition.has(TYPE)) {
				
				LOG.debug("isSimpleType: definition={}", definition);

				String jsonType = getStringOrNull(definition,TYPE);
				
				if(jsonType==null) {
				
					Out.printOnce("... expecting the {} property to be a string value in {}", TYPE, definition.toString(2));
					res=false;
					
				} else {

					if(jsonType.equals(OBJECT) || jsonType.equals(ARRAY)) res=false;
				
				}
				
			} else {

				if(definition.has(DISCRIMINATOR)) res=false;
				if(definition.has(ALLOF)) res=false;
				if(definition.has(ONEOF)) res=false;

				if(definition.has(PROPERTIES)) {
					JSONObject properties = definition.optJSONObject(PROPERTIES);
					if(properties!=null && properties.keySet().size()>1) res=false;
				}

				if(definition.has(ITEMS) && definition.optJSONObject(ITEMS)!=null) definition=definition.getJSONObject(ITEMS);

				if(definition.has(REF)) {
					String referencedType = getTypeByReference(definition.optString(REF));
					res = isSimpleType(referencedType);
				}

			}
		} 
		
		LOG.debug("isSimpleType: res={}", res);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isCustomSimple(String type) {
		boolean res=false;
		JSONObject definition = getDefinition(type);
//
//		if(definition!=null) {
//			// res = definition.has(TYPE) && ARRAY.contentEquals(definition.optString(TYPE));
//			APIModel.is
//			// res = res || definition.has(ALLOF);
//		}
		
		res = APIModel.isArrayType(type);
		if(res) {
			JSONObject property = APIModel.getDefinition(type);
			
			type = APIModel.getTypeName(property, type);

			res = !property.isEmpty() && !type.isEmpty() && APIModel.isSimpleType(type) || APIModel.isArrayType(type);
		}
		
		LOG.debug("isCustomSimple: type={} res={} definition={}", type, res, definition);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isArrayType(String type, String property) {
		return isArrayType( getPropertySpecification(type,property) );
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isArrayType(JSONObject property) {
		boolean res=false;

		try {
			if(property!=null && property.has(TYPE)) {
				String jsonType = property.getString(TYPE);
				if(jsonType!=null) res = jsonType.equals(ARRAY);
			}
		} catch(Exception e) {
			res=false;
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getReferencedType(String type, String property) {
		JSONObject specification = APIModel.getResourceExpanded(type);
//		JSONObject specification = getPropertySpecification(type,property);
		return getReferencedType(specification,property);	    	    
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getReferencedType(JSONObject specification, String property) {
		String res="";

		LOG.debug("getReferencedType: property={} specification={}",  property, specification);

		if(specification!=null && specification.has(PROPERTIES)) specification = specification.optJSONObject(PROPERTIES);
		
		if(specification!=null && specification.has(property)) specification = specification.optJSONObject(property);

		if(specification!=null && specification.has(ITEMS)) {
			specification = specification.optJSONObject(ITEMS);
		}
		if(specification!=null && specification.has(REF)) {
			String ref=specification.optString(REF);
			if(ref!=null) {
				String[] parts=ref.split("/");
				res = parts[parts.length-1];
			}
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getTypeByReference(String ref) {
		String[] parts=ref.split("/");
		String type = parts[parts.length-1];
			
		LOG.debug("getTypeByReference: ref={} type={}",  ref, type);

		if(isExternalReference(ref)) {
			
			JSONObject external=getExternal(ref);
			
			if(external!=null) {
				JSONObject definition=getExternalDefinition(external,ref);
				LOG.debug("getTypeByReference: ref={} type={} definition={}",  ref, type, definition);
	
				addDefinition(ref, definition);
				addLocalReferences(external,definition);
				addExternalReferences(definition);
	
				LOG.debug("getTypeByReference: ref={} type={}",  ref, type);
			}
		}
		
		return type;
		
	}

	private static void addLocalReferences(JSONObject external, JSONObject definition) {
		if(definition==null || external==null) return;
		
		Set<String> properties = definition.keySet().stream().collect(Collectors.toSet());
		
		for(String property : properties) {
			
			boolean isProperRef=property.contentEquals(REF) && definition.has(REF);
			
			if(isProperRef) {
				try {
					isProperRef = definition.getString(REF)!=null;
				} catch(Exception e) {
					isProperRef = false;
				}
			}
			
			if(isProperRef) {
				String ref=definition.optString(property);
				
				if(!isExternalReference(ref)) {
					String queryRef=ref.replace("#/","/");
					
					LOG.debug("APIModel::addLocalReferences:: queryRef={} external={}", queryRef, external );

					Object externalDefinition=external.optQuery(queryRef);

					LOG.debug("APIModel::addLocalReferences:: ref={} queryRef={} externalDefinition={}", ref, queryRef, externalDefinition );

					if(externalDefinition!=null) {
						JSONObject obj=(JSONObject)externalDefinition;
						LOG.debug("APIModel::addLocalReferences:: ref={} externalDefinition={}", definition.get(property), externalDefinition);
						addDefinition(ref,obj);
						addExternalReferences(obj);
						addLocalReferences(external,obj);
												
					} else {
						LOG.debug("APIModel::addLocalReferences:: ref={} external={}", ref, external.keySet());
						LOG.debug("APIModel::addLocalReferences:: external={}", external.toString(2) );

					}
				}
				
			} else if(definition.optJSONObject(property)!=null) {
				addLocalReferences(external,definition.optJSONObject(property));
				
			} else if(definition.optJSONArray(property)!=null) {
				JSONArray array=definition.optJSONArray(property);
				if(array!=null) {
					for(int i=0; i<array.length(); i++) {
						addLocalReferences(external,array.optJSONObject(i));
					}
				}
			}
		}
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getDefinitionByReference(String ref) {
		JSONObject res = new JSONObject();

		try {
			Object obj = APIModel.swagger.optQuery(ref);
			if(obj!=null) {
				if(obj instanceof JSONObject) {
					res = (JSONObject) obj;
					
					LOG.debug("## getDefinitionByReference: ref={} res={}",  ref, res);
	
	//				if(res!=null && res.has("content")) res = res.optJSONObject("content");
	//				if(res!=null && res.has("application/json")) res = res.optJSONObject("application/json");
	//				if(res!=null && res.has("schema")) res = res.optJSONObject("schema");
					
					if(res!=null) res = res.optJSONObject("content");
					if(res!=null) res = res.optJSONObject("application/json");
					if(res!=null) res = res.optJSONObject("schema");
					
					LOG.debug("## getDefinitionByReference: #1 ref={} schema={}",  ref, res);
	
					// if(res!=null && res.has(REF)) ref=res.optString(REF); // res = getDefinitionByReference(res.optString(REF));
					if(res!=null && res.has(REF)) res = getDefinitionByReference(res.optString(REF));
	
					LOG.debug("## getDefinitionByReference: #2 ref={} res={}",  ref, res);
					if(res!=null && res.has(PROPERTIES)) LOG.debug("## getDefinitionByReference: ref={} #3 properties={}",  ref, res.optJSONObject(PROPERTIES).keySet()); 
				}
	
				if(res!=null && !res.isEmpty()) {
					LOG.debug("############# getDefinitionByReference: ref={} res={}",  ref, res );
					// if(ref.contains("Customer_MVO")) LOG.debug("##01 getDefinitionByReference: #03 ref={} swagger={}",  ref, swagger.toString(2) );
					res = APIModel.getResourceExpandedHelper(ref, res);
					LOG.debug("############# getDefinitionByReference: ref={} res={}",  ref, res.keySet() );
					if(res.has(PROPERTIES)) LOG.debug("getDefinitionByReference: ref={} properties={}",  ref, res.optJSONObject(PROPERTIES).keySet() );
	
					if(res.has(PROPERTIES)) return new JSONObject(res.toString());
				}
				
			}
		} catch(Exception ex) {
			// ignore - not necessarily and error
		}
		
		int hashIndex = ref.indexOf("#/");
		LOG.debug("getDefinitionByReference: ref={} hashIndex={} externalSoure={} source={}",  ref, hashIndex );
		if(hashIndex>0) {
			String externalSource=ref.substring(0, hashIndex);
			LOG.debug("getDefinitionByReference: ref={} hashIndex={} externalSoure={} source={}",  ref, hashIndex, externalSource, swaggerSource);
			
			String candidateExternalSource=Utils.getRelativeFile(swaggerSource, externalSource);
			
			LOG.debug("getDefinitionByReference: ref={} candidateExternalSource={}",  ref, candidateExternalSource);

		}
		
		LOG.debug("getDefinitionByReference: #02 ref={}",  ref );

		if(ref.startsWith("#")) {
			String[] parts=ref.split("/");
	
			if(parts[0].contentEquals("#")) res = swagger;
	
			for(int idx=1; idx<parts.length; idx++) {
				if(res.has(parts[idx])) res = res.optJSONObject(parts[idx]);
			}
			
			LOG.debug("getDefinitionByReference: ref={} res={}",  ref, res );

		} else {
			
			LOG.debug("getDefinitionByReference: getExternal ref={}",  ref );

			res = APIModel.getExternal(ref);
		}

		// if(res.has(REF)) res = getDefinitionByReference(res.optString(REF));

		LOG.debug("##03 getDefinitionByReference: #03 ref={} res={}",  ref, res );

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	protected static JSONObject getPropertySpecification(String resource, String property) {
		JSONObject res=getPropertyObjectForResource(resource);
		res = res.optJSONObject(property);

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	protected static JSONObject getPropertySpecification(JSONObject resource, String property) {
		JSONObject res=null;
		if(resource.has(PROPERTIES)) resource=resource.optJSONObject(PROPERTIES);
		if(resource!=null) res = resource.optJSONObject(property);
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG) 
	public static JSONObject getPropertyObjectForResource(String coreResource) {
		JSONObject res=null;
		
		LOG.debug("getPropertyObjectForResource: resource={} {}={}",  coreResource, FLATTEN_INHERITANCE, Config.getBoolean(FLATTEN_INHERITANCE));

		if(resourcePropertyMap.containsKey(coreResource)) {
			return resourcePropertyMap.get(coreResource);
		} else {
			res = getDefinition(coreResource, PROPERTIES);
			
			if(res.isEmpty() && APIModel.isAsyncAPI()) {
				
				res = getDefinition(coreResource);

				if(res.isEmpty()) {
					res = getAsyncMessageDefinition(coreResource);
				}

				LOG.debug("#### getPropertyObjectForResource: resource={} res={}",  coreResource, res.keySet());

			}
			
			LOG.debug("getPropertyObjectForResource: resource={} res={}",  coreResource, res.keySet());
			LOG.debug("getPropertyObjectForResource: resource={} {}={}",  coreResource, FLATTEN_INHERITANCE, Config.getBoolean(FLATTEN_INHERITANCE));

			if(Config.getBoolean(FLATTEN_INHERITANCE)) {	
				JSONObject allOfs = getFlattenAllOfs(coreResource);
				
				LOG.debug("getPropertyObjectForResource: resource={} allOfs={}",  coreResource, allOfs.keySet());

				res = mergeJSON(res,allOfs); // ???? TBD - 2023-06-19
				
				resourcePropertyMap.put(coreResource, res);
				
				LOG.debug("getPropertyObjectForResource: resource={} properties={}",  coreResource, res.keySet());

			}
			
			resourcePropertyMap.put(coreResource, res);

		}

		return res;
	}

	
    static Map<String,AsyncResourceInfo> asyncDetails = null;
    
	private static JSONObject getAsyncMessageDefinition(String node) {
		JSONObject res = new JSONObject();
		
		Object obj = swagger.query("#/components/messages/" + node);
		
		if(obj!=null) {
			LOG.debug("getAsyncMessageDefinition: node={} obj={}", node, obj.toString());
			if(obj instanceof JSONObject) {
				res = (JSONObject) obj;
			}
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG) 
	public static JSONObject getFlattenAllOfs(String resource) {
		LOG.debug("getFlattenAllOfs: resource={}", resource);
		
		if(flattened.containsKey(resource)) return flattened.get(resource);
		
		flattened.put(resource, new JSONObject());
		
		final JSONObject target = new JSONObject();
		JSONObject definition = getDefinition(resource);

		if(definition!=null && definition.has(ALLOF)) {
			
			JSONArray allofs = definition.optJSONArray(ALLOF);
			LOG.debug("getFlattenAllOfs: resource={} allofs={}", resource, allofs);

			if(allofs!=null && !allofs.isEmpty()) {
				allofs.forEach(allof -> {
					if(allof instanceof JSONObject) {
						LOG.debug("getFlattenAllOfs: resource={} allof={}", resource, allof);

						JSONObject allOfDefinition = (JSONObject) allof;
						if(allOfDefinition.has(REF)) {
							String superior = getReferencedType(allOfDefinition, null); 
							
							LOG.debug("getFlattenAllOfs: allOf={} superior={}", allOfDefinition, superior);

							JSONObject inheritsFrom =  getPropertyObjectForResource(superior);
							
							LOG.debug("merging with resource {} keys {}",  resource, inheritsFrom.keySet());
							LOG.debug("merging with resource {} ",  inheritsFrom.toString(2));
	
							mergeJSON(target,inheritsFrom);
						}
					}
				});
			}
		}
		
		flattened.put(resource, target);
		
		return target;
	}
	
	@LogMethod(level=LogLevel.DEBUG) 
	public static JSONObject mergeJSON(JSONObject target, JSONObject delta) {
		LOG.debug("mergeJSON:: target={} delta={}",  target, delta);

		delta.keySet().forEach(key -> {
			target.put(key, delta.optJSONObject(key));	
		});
		return target;
	}

	
	@LogMethod(level=LogLevel.DEBUG) 
	public static JSONArray getAllOfForResource(String coreResource) {
		return getDefinitions(coreResource, ALLOF);
	}

	@LogMethod(level=LogLevel.DEBUG) 
	public static JSONArray getOneOfForResource(String coreResource) {
		return getDefinitions(coreResource, ONEOF);
	}
	
	@LogMethod(level=LogLevel.DEBUG) 
	public static JSONObject getPropertyObjectForResource(JSONObject resource) {
		if(resource!=null && resource.has(PROPERTIES)) resource=resource.optJSONObject(PROPERTIES);

		if(resource==null) resource=new JSONObject();
		return resource;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getPropertiesForResource(String resource) {
		JSONObject obj = getPropertyObjectForResource(resource);
		return obj.keySet();
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getProperties(JSONObject obj) {
		Set<String> res = new HashSet<>();

		if (obj == null){
			return res;
		}
		if(obj.has(PROPERTIES)) obj=obj.optJSONObject(PROPERTIES);
		if(obj!=null) res = obj.keySet();
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getDefinition(String ... args) {
		JSONObject res = null;
		if(args.length>0) {
			res = getDefinition(args[0]);
			int idx=1;
			while(res!=null && idx<args.length) {
				res = res.optJSONObject(args[idx]);
				idx++;
			}
		}

		if(res==null) res=new JSONObject();
		
		// return res;
		return new JSONObject(res.toString()); 
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getDefinition(JSONObject data, String ... args) {
		JSONObject res = data;
		int idx=0;
		while(res!=null && idx<args.length) {
			res = res.optJSONObject(args[idx]);
			idx++;
		}
		return res;
	}

	
	@LogMethod(level=LogLevel.DEBUG)
	private static JSONArray getDefinitions(String ... args) {
		JSONObject obj = null;
		JSONArray res = null;

		if(args.length>0) {
			obj = getDefinition(args[0]);
			int idx=1;
			while(obj!=null && idx<args.length-1) {
				obj = obj.optJSONObject(args[idx]);
				idx++;
			}
			if(obj!=null && idx<args.length) res = obj.optJSONArray(args[idx]);
		}

		if(res==null) res = new JSONArray();

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isEnumType(String type) {
		boolean res=false;
		JSONObject definition = getDefinition(type);
		if(definition!=null) {
			res = definition.has(ENUM);
		}
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getPaths() {
		if(swagger!=null && swagger.has(PATHS))
			return swagger.getJSONObject(PATHS).keySet();
		else
			return new HashSet<>();
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static List<JSONObject> getChildElements(JSONObject obj) {
		return new JSONObjectHelper(obj).getChildElements();
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static List<JSONObject> getNormalResponses(JSONObject respObj) {
		if(respObj==null) return new LinkedList<>();

		Set<String> keys = respObj.keySet().stream()
				.filter(resp -> !"default".equals(resp) && resp.startsWith("2") ) // Integer.parseInt(resp)<300)
				.collect(toSet());

		List<JSONObject> res = new JSONObjectHelper(respObj, keys).getChildElements();
		
		LOG.debug("getNormalResponses:: keys={} res={}", keys, res);

		return res; 


	}


	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getPathObjectByKey(String path) {
		return swagger.getJSONObject(PATHS).getJSONObject(path);
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static boolean hasResponses(JSONObject obj) {
		return obj.has(RESPONSES);
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isOpenAPIv2(JSONObject swagger) {
		return swagger!=null && !swagger.has("openapi");
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getDefinition(String node) {

		JSONObject res;
		JSONObject definitions = getDefinitions();

		LOG.debug("getDefinition: node={} definitions={}", node, definitions);

		if(definitions==null) {
			res=null;
		} else if(definitions.optJSONObject(node)!=null) {
			res = definitions.optJSONObject(node);
		} else if(!Config.getPrefixToRemove().isEmpty()) {
			Optional<String> actualDefinition = definitions.keySet().stream().filter(s -> removePrefix(s).contentEquals(node)).findFirst();
			LOG.debug("getDefinition: node={} actualDefinition={}", node, actualDefinition);

			if(actualDefinition.isPresent()) {
				res = definitions.optJSONObject(actualDefinition.get());
			} else {
				res = null;
			}
		} else if(isAsyncAPI()) {
			
			LOG.debug("getDefinition: node={} ASYNC", node);
			res = getAsyncMessageDefinition(node);

	    } else {
			res = null;
		}

		if(res!=null && res.has(REF)) {
			res = getDefinitionByReference(res.getString(REF));
		}
		
		LOG.debug("getDefinition: node={} res={}", node, res);

		if(res!=null) res = new JSONObject(res.toString());
		
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getDefinitions() {
		if(swagger!=null && allDefinitions.keySet().isEmpty()) {	
			
			LOG.debug("APIModel::getDefinitions:: get all definitions");
			
			addExternalReferences(swagger);
			
			JSONObject res=null;
			if(isAsyncAPI()) {
				JSONObject components = swagger.optJSONObject("components");
				if(components!=null) {	
					 res = components.optJSONObject("schemas");		
					
					 JSONObject messages = components.optJSONObject("messages");
					 if(messages!=null) {
						 JSONObject tmp = new JSONObject();
						 for(String key : res.keySet() ) {
							 tmp.put(key, res.get(key));
						 };
						 for(String key : messages.keySet() ) {
							 tmp.putOnce(key, messages.get(key));
							 
							LOG.debug("APIModel::getDefinitions:: async adding item={}", key);

						 };
						 res=tmp;
					 }
					 
				}
				
			} else if(isOpenAPIv2(swagger))
				res=swagger.optJSONObject("definitions");
			else {
				JSONObject components = swagger.optJSONObject("components");
				if(components!=null) res = components.optJSONObject("schemas");
			}
			
			if(res!=null) allDefinitions = res;
			
			LOG.debug("APIModel::getDefinitions:: keys={}", allDefinitions.keySet());

		}
		return allDefinitions;
	}


	public static void addExternalReferences(JSONObject api) {
		if(api==null || api.isEmpty()) return;
		
		Set<String> properties = api.keySet().stream().collect(Collectors.toSet());
		
		for(String property : properties) {
			
			LOG.debug("APIModel::addExternalReferences:: property={}", property);

			if(property.contentEquals(REF)) {
				String ref=api.getString(property);
				if(isExternalReference(ref)) {
					JSONObject external=getExternal(ref);
					JSONObject externalDefinition=getExternalDefinition(external,ref);
					if(externalDefinition!=null) {
						LOG.debug("APIModel::addExternalReferences:: ref={} externalDefinition={}", api.get(property), externalDefinition);
						addDefinition(ref,externalDefinition);
						addExternalReferences(externalDefinition);
						addLocalReferences(external,externalDefinition);
					}
					String localRef=getExternalReference(ref); //ref.substring(ref.indexOf("#/"));	
					api.put(REF,localRef);
				}
				
			} else if(property.contentEquals(PROPERTIES)) {
				//
			} else if(api.optJSONObject(property)!=null) {
				 addExternalReferences(api.optJSONObject(property));
			} else if(api.optJSONArray(property)!=null) {
				JSONArray array=api.optJSONArray(property);
				for(int i=0; i<array.length(); i++) {
					addExternalReferences(array.optJSONObject(i));
				}
			}
		}
		
	}

	private static void addDefinition(String ref, JSONObject definition) {
		//String localRef=ref.substring(ref.indexOf("#/"));
		String localRef=getExternalReference(ref);
		
		if(swagger!=null) {
			String parts[] = localRef.replace("#/", "").split("/");
		
			LOG.debug("addDefinition: localRef={} parts={}",  localRef, parts);
			JSONObject target=swagger;
			if(parts.length>1) {
				for(int idx=0; idx<parts.length-1; idx++) {
					
					if(target==null) break;
					
					String part=parts[idx];
					
					if(!target.has(part)) {
						target.put(part,new JSONObject());
						LOG.debug("addDefinition: add part={}",  part);
	
					}
					
					target=target.optJSONObject(part);
					
				}
				
				if(target!=null) {
					String type=parts[parts.length-1];
					
					if(target.has(type)) {
						String currentDef=target.get(type).toString();
						
						removeExternalReferencePart(definition);
						
						String newDef=definition.toString();
						if(!currentDef.contentEquals(newDef)) {
							Out.printOnce("... definition for type={} already seen",  type);
							Out.printOnce("... already seen as {}", currentDef);
							Out.printOnce("... new defintion as {}", newDef);
						}

					} else {
						target.put(type,  definition);
						allDefinitions.put(type, definition);

						LOG.debug("addDefinition: put type={} target={}",  type, target.keySet());
					}
					
				}
			}

		}
	}

	private static void removeExternalReferencePart(JSONObject definition) {
		if(definition==null) return;
		if(definition.has(REF)) {
			String ref=definition.optString(REF);
			if(isExternalReference(ref)) {
				ref=ref.substring(ref.indexOf("#/"));
				definition.put(REF,ref);
			}
		} else {
			Set<String> properties = definition.keySet().stream().collect(Collectors.toSet());
			for(String property : properties) {
				if(definition.optJSONObject(property)!=null) {
					removeExternalReferencePart(definition.optJSONObject(property));
				} else if(definition.optJSONArray(property)!=null) {
					removeExternalReferencePart(definition.optJSONArray(property));
				}
			}
		}
	}

	private static void removeExternalReferencePart(JSONArray array) {
		if(array==null) return;
		
		for(int i=0; i<array.length(); i++) {
			if(array.optJSONObject(i)!=null) {
				removeExternalReferencePart(array.optJSONObject(i));
			} else if(array.optJSONArray(i)!=null) {
				removeExternalReferencePart(array.optJSONArray(i));
			}
		}		
	}

//	private static JSONObject getExternalDefinition_old(String ref) {
//		JSONObject res=null;
//		if(isExternalReference(ref)) {
//			String externalSource=getExternalReference(ref); // ref.substring(0, hashIndex);
//			LOG.debug("getExternalDefinition: ref={} externalSoure={} source={}",  ref, externalSource, swaggerSource);
//			
//			String candidateExternalSource=Utils.getRelativeFile(swaggerSource, externalSource);
//			
//			LOG.debug("getExternalDefinition: ref={} candidateExternalSource={}",  ref, candidateExternalSource);
//			
//			if(candidateExternalSource!=null) {
//				JSONObject externalDefinitions=Utils.readJSONOrYaml(candidateExternalSource);
//				
//				String localRef=getLocalPart(ref);
//				if(localRef!=null && !localRef.isEmpty()) {
//					LOG.debug("getExternalDefinition: ref={} localRef={}",  ref, localRef);
//
//					Object definition=externalDefinitions.optQuery(localRef);
//				
//					LOG.debug("getExternalDefinition: ref={} localRef={} definition={}",  ref, localRef, definition);
//
//					if(definition!=null) res=(JSONObject)definition;
//				
//				} else {
//					res=externalDefinitions;
//				}
//				
//			}
//
//		}
//		
//		if(res==null) {
//			Out.printAlways("... unable to locate definition for external reference {}",  ref);
//		}
//		
//		return res;
//		
//	}
	
	private static String getLocalPart(String ref) {
		String res=null;
		int hashIndex=ref.indexOf("#/");
		if(hashIndex>0) {
			res=ref.substring(hashIndex);
		}
		return res;
	}

	private static String getExternalReference(String ref) {
		int hashIndex=ref.indexOf("#/");
		if(hashIndex>=0) {
			return ref.substring(0, hashIndex);
		} else if(ref.indexOf("#")<0) {
			return ref;
		} else {
			Out.printAlways("... ERROR: Unable to determine external reference from '{}'", ref);
			System.exit(0);
		}
		return null;
	}

	private static JSONObject getExternalDefinition(JSONObject external, String ref) {
		JSONObject res=null;
		
		if(externalDefinitions.containsKey(ref)) return externalDefinitions.get(ref);
		
		if(isExternalReference(ref)) {
			// String localRef=getExternalReference(ref);
			
			LOG.debug("getExternalDefinition: ref={} external={}",  ref, external);

			String localRef=getLocalPart(ref);
			if(localRef!=null && !localRef.isEmpty()) {
				LOG.debug("getExternalDefinition: ref={} localRef={}",  ref, localRef);

				Object definition=external.optQuery(localRef);
			
				LOG.debug("getExternalDefinition: ref={} localRef={} definition={}",  ref, localRef, definition);

				if(definition!=null) res=(JSONObject)definition;
			
			} else {
				res=external;
			}
			
//			LOG.debug("getExternalDefinition: ref={} localRef={}",  ref, localRef);
//
//			Object definition=external.optQuery(localRef);
//				
//			LOG.debug("getExternalDefinition: ref={} localRef={} definition={}",  ref, localRef, definition);
//
//			if(definition!=null) {
//				res=(JSONObject)definition;
//				externalDefinitions.put(ref,res);
//			}

		}
		
		if(res==null) {
			Out.printAlways("... unable to locate definition for external reference {}",  ref);
		}
		
		return res;
		
	}

			
	private static JSONObject getExternal(String ref) {
		JSONObject res=null;
		
		String key = getKey(ref);
		
		if(externals.containsKey(key)) {
			res=externals.get(key);
			
			LOG.debug("getExternal: FOUND key={} keys={} ",  key, externals.keySet());

		} else {
				
			if(seenRefs.contains(ref) && !externals.isEmpty()) {
				Out.debug("getExternal: RECURSIVE ref={}", ref );
				Out.debug("getExternal: externals keys={}", externals.keySet() );

				return new JSONObject();
			}

			seenRefs.add(ref);

			String externalSource = APIModel.getExternalReference(ref);
			if(externalSource!=null && !externalSource.isEmpty()) {	
				// String externalSource=ref.substring(0, hashIndex);
				
				Out.printOnce("... retrieve external source {}",  externalSource);

				LOG.debug("... retrieve external source {} key={} keys={}",  externalSource, key, externals.keySet());
					
				String candidateExternalSource=Utils.getRelativeFile(swaggerSource, externalSource);							
				if(candidateExternalSource!=null) {		
					
					// candidateExternalSource = candidateExternalSource.replace("0//", "0/");
					
					LOG.debug("getExternal: readJSONOrYaml candidateExternalSource={}", candidateExternalSource);
	
					res=Utils.readJSONOrYaml(candidateExternalSource);
					
					externals.put(key, res);
									
				}
	
			}
		}
				
		LOG.debug("getExternal: ref={} res={}",  ref, res);

		return res;
		
	}
		
	private static String getKey(String ref) {
		return getExternalReference(ref);
	}

	public static boolean isExternalReference(String ref) {
		int hashIndex=ref.indexOf("#/");
		return hashIndex>0 || ref.indexOf('#')<0;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getPaths(String resource, String operation) {
		List<String> res = new LinkedList<>();

		if(swagger==null) return res;

		JSONObject allpaths = swagger.optJSONObject(PATHS);

		String prefix = "/" + resource.toUpperCase();

		allpaths.keySet().stream()
				.filter(path -> isPathForResource(path,prefix))
				.forEach(path -> {
					JSONObject allOps = allpaths.optJSONObject(path);
					if(allOps!=null && allOps.has(operation.toLowerCase())) res.add(path); 
				});

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getPaths(String resource) {
		List<String> res = new LinkedList<>();

		if(swagger==null) return res;

		JSONObject allpaths = swagger.optJSONObject(PATHS);

		String prefix = "/" + resource.toUpperCase();

		if(allpaths!=null) {
			res = allpaths.keySet().stream()
					.filter(path -> isPathForResource(path,prefix))
					.collect(toList());
		}
		
		return res;
		
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isPathForResource(String path, String prefix) {
		return path.equalsIgnoreCase(prefix) || path.toUpperCase().startsWith(prefix+"/");
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getOperationDescription(String path, String operation) {
		String res="";
		try {
			res = getPathObjectByKey(path).optJSONObject(operation.toLowerCase()).getString(DESCRIPTION);
		} catch(Exception e) {
			LOG.debug(String.format("Unable to find description for path=%s and operation=%s", path, operation));
		}
		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	public Map<String, List<String>> getAllNotifications(List<String> resources, JSONObject rules) {
		Map<String,List<String>> res = new HashMap<>();

		if(rules==null) {
			Out.println("... API rules not found - unable to process notification conformance");
			return res;
		}

		for( String resource : resources) {
			String key = "rules " + resource;
			JSONObject rule = rules.optJSONObject(key);
			if(rule!=null) {
				JSONArray notif = rule.optJSONArray(NOTIFICATIONS);
				if(notif!=null) {
					res.put(resource, notif.toList().stream().map(Object::toString).toList());
				}
			}
		}
		return res;
	}

	private static void setSeenAPImessage() {
		firstAPImessage=false;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getNotificationsByResource(String resource, JSONObject rules) {
		List<String> res = new LinkedList<>();

		if(rules==null || rules.isEmpty()) {
			Out.printOnce("... extracting notification support from API");
			return getNotificationsFromSwagger(resource);
		}

		String key = "rules " + resource;

		JSONObject rule = rules.optJSONObject(key);
		if(rule!=null) {
			JSONArray notif = rule.optJSONArray(NOTIFICATIONS);
			if(notif!=null) {
				res.addAll(notif.toList().stream().map(Object::toString).toList());
			} else {
				String list = rule.optString(NOTIFICATIONS);
				if(!list.isEmpty()) {
					String[] parts = list.split(",");
					if(parts.length>0) {
						res.addAll(List.of(parts));
					}
				}
			}
		}

		res = res.stream()
				.map(notification -> getNotificationLabel(resource, notification))
				.collect(toList());

		return res;
	}

	
	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getNotificationsDetails(String resource, JSONObject rules, String notification) {
		List<String> res = new LinkedList<>();

		LOG.debug("getNotificationsDetails: resource={} notification={}",  resource, notification);
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<JSONObject> getNotificationFromRules(String resource, String notification) {
		String NOTIF = notification.toUpperCase();
		
		List<JSONObject> res = new LinkedList<>();

		JSONObject rulesFragment = Config.getRulesForResource(resource); 
		
		if(rulesFragment==null) {
			Out.printOnce("... missing rules details - specify using --rules argument");
			return res;
		}
		
		JSONArray  rules = rulesFragment.optJSONArray("notifications");
				
		if(rules==null) {
			Out.printOnce("... WARNING: notification rules not found for resource {}", resource);
			return res;
		}
		
		for(int i=0; i<rules.length(); i++) {
			JSONObject notificationRule = rules.optJSONObject(i);
			
			LOG.debug("notificationRule: resource={} notification={} notificationRule={}",  resource, notification, notificationRule.toString(2));

			String name = notificationRule.optString("name", "").toUpperCase();
			
			if(notificationRule!=null && notification.toUpperCase().contains(name)) {
				JSONArray examples = notificationRule.optJSONArray("examples");
				if(examples!=null) {
					for(int j=0; j<examples.length(); j++) {
						JSONObject example = examples.optJSONObject(j);
						res.add(example);
					}
				}
			}
		}
		
						
		LOG.debug("getNotificationsDetails: resource={} notification={} res={}",  resource, notification, res);
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getNotificationsFromSwagger(String resource) {				
		List<String> res = new LinkedList<>();
		
		List<String> allDefs = getAllDefinitions();
		List<String> allResources = getResources();

		Set<String> events = allDefs.stream().filter(x -> x.startsWith(resource) &&  x.endsWith("Event")).collect(toSet());			

		Set<String> moreSpecificResources = allResources.stream().filter(x -> x.startsWith(resource) && x.length()>resource.length()).collect(toSet());
		
		Set<String> removeCandidates = moreSpecificResources.stream().map(s -> events.stream().filter(e -> e.startsWith(s)).collect(toSet()))
					.flatMap(Set::stream).collect(toSet());
		
		LOG.debug("getNotificationsFromSwagger: resource={} removeCandidates={}", resource, removeCandidates);

		res.addAll(events);
		res.removeAll(removeCandidates);
		
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAllDefinitions() {
		
		Set<String> res = getDefinitions().keySet();
		
		// included 2023-11-19
		res = filterMVOFVO(res);
		

		return res.stream()
				// 2022-11-04 .map(APIModel::getMappedResource)
				.collect(toList());
	}

	public static Set<String> filterMVOFVO(Set<String> resources) {
		if(!Config.getBoolean("keepMVOFVOResources")) {
			Predicate<String> MVO_or_FVO = s -> s.endsWith("_FVO") || s.endsWith("_MVO");
			resources = resources.stream().filter(MVO_or_FVO.negate()).collect(toSet());
		} 
		return resources;
	}
	
	public static List<String> filterMVOFVO(List<String> resources) {
		if(!Config.getBoolean("keepMVOFVOResources")) {
			Predicate<String> MVO_or_FVO = s -> s.endsWith("_FVO") || s.endsWith("_MVO");
			resources = resources.stream()
					.filter(MVO_or_FVO.negate())
					.collect(toList());
		} 
		return resources;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static String getNotificationLabel(String resource, String notification) {
		return resource + notification.substring(0,1).toUpperCase() + notification.substring(1) + "Event";
	}

	public static JSONObject getInfo() {
		return swagger.optJSONObject("info");
	}

	public static List<String> getEnumValues(String orphanEnum) {
		List<String> res = new LinkedList<>();
		JSONObject def = getDefinition(orphanEnum);
		if(def!=null && def.has("enum")) {
			JSONArray values = def.optJSONArray("enum");
			if(values!=null) res.addAll(values.toList().stream().map(Object::toString).collect(toList()));
		}
		return res;
	}

	public static Collection<String> getAllReferenced() {
		List<String> res = new LinkedList<>();

		for(String resource: getAllDefinitions() ) {
			res.addAll( getAllReferenced(resource) );
		}

		return res.stream().distinct().collect(toList());
	}

	private static List<String> getAllReferenced(String resource) {
		List<String> res = new LinkedList<>();

		JSONObject def = getDefinition(resource);

		if(def!=null && def.has(PROPERTIES) && def.optJSONObject(PROPERTIES)!=null) {

			res.addAll( getAllReferenced( def ));
		}

		return res;		
	}

	private static List<String> getAllReferenced(JSONObject definition) {
		List<String> res = new LinkedList<>();

		for(String property : getProperties(definition) ) {
			JSONObject o = getProperty(definition,property);
			if(o!=null) {
				if(o.has(REF)) {
					String ref = o.optString(REF);
					if(!ref.isEmpty()) res.add( lastElement(ref,"/") );
					//				} else if (o.has(ITEMS) ) {
					//					String ref = o.optJSONObject(ITEMS).optString(REF);
					//					if(!ref.isEmpty()) res.add( lastElement(ref,"/") );
					//				}
				} else {
					Object ref = o.optQuery( "/" + ITEMS + "/" + REF);
					if(ref!=null) {
						String sref = ref.toString();
						if(!sref.isEmpty()) res.add( lastElement(sref,"/") );
					}
				}
			}
		}

		return res;
	}

	private static JSONObject getProperty(JSONObject definition, String property) {
		JSONObject res = null;

		if(definition!=null && definition.has(PROPERTIES) && definition.optJSONObject(PROPERTIES)!=null) {
			JSONObject properties = definition.optJSONObject(PROPERTIES);
			res = properties.optJSONObject(property);
		}

		return res;

	}

	private static String lastElement(String ref, String delim) {
		String[] s = ref.split(delim);
		return s[s.length-1];
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String type(JSONObject property, String ref) {
		if(ref!=null) {
			return ref;
		} else {
			return typeOfProperty(property);
		}
	}
	

	@LogMethod(level=LogLevel.DEBUG)
	public static String typeOfProperty(JSONObject property) {
		return typeOfProperty(property,null);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String typeOfProperty(JSONObject property, String name) {

		String res="";

		try {
			if(property==null) {
				return res;
			} else if(property.has(FORMAT)) {
				String format = property.getString(FORMAT);
				String formatMapping = formatToType.get(format);
	
				if(formatMapping!=null) {
					res=formatMapping;
	
				} else if (Config.getFormatToType().containsKey(format)) {
					res = Config.getFormatToType().get(format);
	
				} else {
					if(!typeWarnings.contains(format) ) {
						Out.debug("... format: {} has no mapping, using type and format", format);
						typeWarnings.add(format);
					}
					res = property.getString(TYPE) + '/' + format;
				}
	
			} else if(property.has(REF)) {
	
				res = getReference(property);
	
			} else if(property.has(ITEMS)) {
	
				res = typeOfProperty( property.optJSONObject(ITEMS), name );
	
			} else if(property.has(TYPE)) {
	
				String type = getStringOrNull(property,TYPE);
								
				if(type==null) {				
					Out.printOnce("... expecting the {} property to be a string value in {}", TYPE, property.toString(2));
				}  else  if(typeMapping.containsKey(type)) {
					res = typeMapping.get(type);
				} else if(Config.getTypeMapping().containsKey(type)) {
					res = Config.getTypeMapping().get(type);
				} else {
					res = type;
				}
	
			} else {
				LOG.debug("typeOfProperty:: name={}", name);

				if(isAsyncAPI()) {
					
					if(!isSecialProperty(name)) { 
						
						LOG.debug("## typeOfProperty:: adding definition name={} definition={}", name, property);

						res = APIModel.createAsyncType(name, property);
						
					}
				
				} else if(!isSecialProperty(name)) {  
					Out.printOnce("... Possible issue: No type information for {} in '{}' ({}) - using '{}'", name, property.toString(2), Utils.getBaseFileName(swaggerSource), "{}");
					res = "{}"; // property.toString(); // should not really happen

				}
			}
		} catch(Exception ex) {
			LOG.debug("APIModel::type: execption={}", ex.getLocalizedMessage());
			// ex.printStackTrace();
		}

		LOG.debug("... format: res={}", res);

		return res;

	}


	@LogMethod(level=LogLevel.DEBUG)
	private static boolean isSecialProperty(String name) {
		List<String> specials = Config.get("specialProperties");
		return specials.contains(name);
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getType(JSONObject property) {
		if(property.has(ITEMS) && property.optJSONObject(ITEMS)!=null) {
			property = property.optJSONObject(ITEMS);
			return getType(property);
		}
		return property;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getTypeName(JSONObject property) {
		return getTypeName(property,null);
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static String getTypeName(JSONObject property, String name) {

		String res=null;
		if(property==null) {
			res = "";
		} else if(property.has(ITEMS)) {
			property = property.optJSONObject(ITEMS);
			res = getTypeName(property, name);
		} else if(property.has(REF)) {
			res = getReference(property); 
		} else if(property.has(TYPE)){
			res = getStringOrNull(property, TYPE);
						
			if(res==null) {			
				Out.printOnce("... expecting the {} property to be a string value in {}", TYPE, property.toString(2));
			} 
		}

		if(res==null) {
			if(isAsyncAPI() ) {
				
				if(!property.has(ALLOF) && !property.has(ONEOF) && !name.contentEquals("value")) {
					Out.printOnce("... ### Possible issue: No type information in '{}' ({}) - using '{}'", property, Utils.getBaseFileName(swaggerSource), "{}");
				}
				
				if(!isSecialProperty(name)) { 
					
					LOG.debug("typeOfProperty:: adding definition name={} definition={}", name, property);

					res = APIModel.createAsyncType(name, property);
					
				} else {						
					res = name;	
				}
				
			} else if(!isSecialProperty(name)) {
				Out.printOnce("... ## Possible issue: No type information in '{}' ({}) - using '{}'", property, Utils.getBaseFileName(swaggerSource), "{}");
			}
				// System.exit(1);
			res="{}";
		}
		
		return res;
	}

	public static String getStringOrNull(JSONObject obj, String key) {
		try {
			return obj.getString(key);
		} catch(Exception ex) {
			return null;
		}
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCardinality(JSONObject property, boolean isRequired) {
		return getCardinality(property,isRequired, Optional.empty(), Optional.empty());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCardinality(JSONObject property, boolean isRequired, Optional<Integer> minItems, Optional<Integer> maxItems) {

		int min=0;
		String max="*";

		if(minItems.isPresent()) {
			min = minItems.get(); 
		} else if(property.has(MIN_ITEMS)) {
			min=property.optInt(MIN_ITEMS);
		} else if(isRequired) {
			min=1;
		}

		if(maxItems.isPresent()) {
			max = Integer.toString(maxItems.get());
		} else if(property.has(MAX_ITEMS)) {
			int optMax=property.optInt(MAX_ITEMS);
			max = Integer.toString(optMax);
		}

		String res="";
		if( isArrayType(property) ) { 
			res = Integer.toString(min) +  ".." + max;
		} else {
			res = (min==1) ? Config.getString(CARDINALITY_REQUIRED_ONE) : Config.getString(CARDINALITY_ZERO_OR_ONE);
		}

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCardinality(Optional<Integer> minItems, Optional<Integer> maxItems) {

		int min=0;
		String max="*";

		if(minItems.isPresent()) {
			min = minItems.get(); 
		} 

		if(maxItems.isPresent()) {
			max = Integer.toString(maxItems.get());
		} 
		String res="";
		res = Integer.toString(min) +  ".." + max;

		return res;

	}


	@LogMethod(level=LogLevel.DEBUG)
	private static String getReference(JSONObject property, String items, String ref) {
		return property.getJSONObject(items).getString(ref).replaceAll(".*/([A-Za-z0-9.]*)", "$1");
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getReference(JSONObject property) {
		if(property.has(ITEMS)) {
			property = property.optJSONObject(ITEMS);
			return getReference(property);
		} else {
			String ref=property.optString(REF);
			if(isExternalReference(ref)) {
				addExternalReferences(property);
				LOG.debug("getReference: addExternalReferences ref={}", ref);

			}
			return ref.replaceAll(".*/([A-Za-z0-9.]*)", "$1");
		}
	}	

	@LogMethod(level=LogLevel.DEBUG)
	public static Collection<String> getMappedSimpleTypes() {
		Collection<String> res = new HashSet<>();

		res.addAll( APIModel.typeMapping.values() );
		res.addAll( APIModel.formatToType.values() );

		Map<String,String> configTypeMapping = Config.getTypeMapping();
		res.addAll( configTypeMapping.values() );

		Map<String,String> configFormatToType = Config.getFormatToType();
		res.addAll( configFormatToType.values() );

		LOG.debug("getMappedSimpleTypes: res=" + res);
		
		return res;
	}	

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isSpecialSimpleType(String type) {
		return type.contains("/");
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static boolean isRequired(String resource, String property) {
		boolean res=false;
		
		JSONObject definition = getDefinition(resource);
		if(definition!=null) {	
			JSONArray required = definition.optJSONArray("required");

			if(required!=null) {
				res = required.toList().stream().filter(o -> o instanceof String).map(o -> (String)o).anyMatch(s -> s.equals(property));
				
				LOG.debug("isRequired: resource={} property={} required={} res={}",  resource, property, required, res);

			}
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static APIModel loadAPI(String file) {

		try {

			if(!file.endsWith(".json") && !file.endsWith(".yaml") && !file.endsWith(".yml")) {
				Out.println("file " + file + " is not of expected type (.json or .yaml/.yml)");
				System.exit(2);
			}

			return new APIModel(file);

		} catch(Exception ex) {
			Out.println("Exception: " + ex.getLocalizedMessage());
			System.exit(1);
		}

		return null;

	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static APIModel loadAPI(String filename, File file) {
		
		LOG.debug("APIModel::loadAPI:: filename={}", filename);
		
		if(file==null) {
			Out.println("... API file '" + filename + "' not found");
			System.exit(0);
			
		} else if(!file.exists()) {
			Out.println("... API file '" + filename + "' does not exist");
			System.exit(0);
			
		}

		try {
			return new APIModel(filename, file);
			
		} catch(Exception ex) {
			Out.printAlways("... error processing API specification: exception=" + ex.getLocalizedMessage() );
			System.exit(1);
		}

		return null;

	}

	
	@LogMethod(level=LogLevel.DEBUG)
	public static APIModel loadAPI(String source, InputStream is) {
		
		LOG.debug("APIModel::loadAPI:: source={}", source);
		
		if(is==null) {
			Out.println("... API source '" + source + "' not found");
			System.exit(0);
			
		} 

		try {
			return new APIModel(source, is);
			
		} catch(Exception ex) {
			Out.printAlways("... error processing API specification: exception=" + ex.getLocalizedMessage() );
			System.exit(1);
		}

		return null;

	}

	
	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getPropertyObjectBySchemaObject(JSONObject obj) {
		JSONObject res = getDefinitionBySchemaObject(obj);
		if(res.has(PROPERTIES)) res = res.optJSONObject(PROPERTIES);
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getDefinitionBySchemaObject(JSONObject obj) {
		JSONObject res = null;
		if(obj.has(REF)) {
			res = getDefinitionByReference(obj.optString(REF));
		} else if(obj.has(PROPERTIES)) {
			res = obj;
		} else {
			res = new JSONObject();
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getDescription(String resource) {
		String res="";
		JSONObject obj = getDefinition(resource);
		if(obj!=null) {
			res = obj.optString(DESCRIPTION);
			if(res.isBlank()) {
				if(obj.has(ALLOF)) {
					JSONArray allofs = obj.getJSONArray(ALLOF);
					for(int i=0; i<allofs.length(); i++) {
						JSONObject allof = allofs.optJSONObject(i);
						if(allof!=null) {
							res = allof.optString(DESCRIPTION);
							if(!res.isEmpty()) break;
						}					
					}
					if(res.isEmpty()) LOG.debug("getDescription: resource={} - EMPTY - after ALLOF check", resource);
		
				}
			}	
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCustomPuml(String type) {
		return getCustomPuml(type, Optional.empty(), Optional.empty());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCustomPuml(String type, Optional<Integer> minItems, Optional<Integer> maxItems) {
		String res;

		LOG.debug("getCustomPuml: type={}", type);

		if(typeMapping.containsKey(type)) {
			res = typeMapping.get(type);
		} else {
			JSONObject definition = getDefinition(type);

			LOG.debug("getCustomPuml: type={} definition={}", type, definition);

			res = getCustomPuml(definition, minItems, maxItems);

		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCustomPuml(JSONObject definition, Optional<Integer> minItems, Optional<Integer> maxItems) {
		StringBuilder res = new StringBuilder();

		if(definition==null) return res.toString();

		LOG.debug("getCustomPuml: definition={} minItems={} maxItems={}", definition, minItems, maxItems);

		if(definition.has(TYPE) && ARRAY.contentEquals(definition.getString(TYPE)) && definition.has(ITEMS)) {

			minItems = APIModel.updateMinMaxItems(definition, minItems, "minItems");
			maxItems = APIModel.updateMinMaxItems(definition, maxItems, "maxItems");

			definition = definition.optJSONObject(ITEMS);
			res.append( getCustomPuml(definition, minItems, maxItems) );

		} else if(definition.has(REF)) {
			String type = getTypeByReference(definition.optString(REF));
			if(Config.getCompressCustomTypes()) {
				res.append( getCustomPuml(type, minItems, maxItems) );
			} else {
				String cardinality = "[" + getCardinality(minItems, maxItems) + "]";
				res.append( type + " " + cardinality + NEWLINE); 
				res.append( type + " : " + getCustomPuml(type, Optional.empty(), Optional.empty()) );
			}

		} else if(definition.has(TYPE)) {

			String type = definition.getString(TYPE);
			if(typeMapping.containsKey(type)) {
				res.append( typeMapping.get(type) );
			} else {
				res.append( type );
			}
			if(minItems.isPresent() || maxItems.isPresent()) {
				String cardinality = "[" + getCardinality(minItems, maxItems) + "]";
				res.append( " " + cardinality);
			}

		} else if(definition.has(ALLOF) && definition.optJSONArray(ALLOF)!=null) {
			JSONArray allOfs = definition.optJSONArray(ALLOF);
			Optional<Integer> allOfMinItems = APIModel.getMinMaxItems(definition, "minItems");
			Optional<Integer> allOfMaxItems = APIModel.getMinMaxItems(definition, "maxItems");

			for(Object allof : allOfs) {
				if(allof instanceof JSONObject) {
					definition = (JSONObject) allof;
					if(definition.has(REF)) {
						String type = getTypeByReference(definition.optString(REF));
						// res.append( getCustomPuml(type, allOfMinItems, allOfMaxItems) );

						if(Config.getCompressCustomTypes()) {
							res.append( getCustomPuml(type, allOfMinItems, allOfMaxItems) );
						} else {
							definition = getDefinition(type);
							// String cardinality = "[" + getCardinality(definition, isRequired, allOfMinItems, allOfMaxItems) + "]";
							// res.append( type + " " + cardinality + NEWLINE); 
							// res.append( type + " : " + getCustomPuml(type, Optional.empty(), Optional.empty()) );
							res.append( getCustomPuml(definition, allOfMinItems, allOfMaxItems) );
						}
					} 
				}
			}
		}

		return res.toString();
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Optional<Integer> getMinMaxItems(JSONObject definition, String key) {
		Optional<Integer> res=Optional.empty();

		if(definition!=null) {
			if(definition.has(key)) {
				Integer cardinality = definition.optInt(key);	    		
				res = Optional.of(cardinality);

			} else if(definition.has(ALLOF) && definition.optJSONArray(ALLOF)!=null) {
				JSONArray allOfs = definition.optJSONArray(ALLOF);
				for(Object allof : allOfs) {
					if(allof instanceof JSONObject) {
						definition = (JSONObject) allof;
						res = getMinMaxItems(definition,key);
						if(res.isPresent()) break;
					}
				}
			}
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Optional<Integer> updateMinMaxItems(JSONObject definition, Optional<Integer> minMax, String key) {
		Optional<Integer> res=minMax;

		if(definition!=null && definition.has(key) && !minMax.isPresent()) {
			Integer cardinality = definition.optInt(key);	    		
			res = Optional.of(cardinality);
		}

		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getCustomPuml_old(JSONObject definition) {
		String res="";

		boolean isRequired=false;
		if(definition!=null) {
			if(definition.has(TYPE) && ARRAY.contentEquals(definition.optString(TYPE)) && definition.has(ITEMS)) {
				String cardinality = "[" + getCardinality(definition, isRequired) + "]";
				definition = definition.optJSONObject(ITEMS);
				res = getCustomPuml(definition, Optional.empty(), Optional.empty()) + " " + cardinality;

			} else if(definition.has(REF)) {
				res = getTypeByReference(definition.optString(REF));

			} else if(definition.has(TYPE)) {
				res = definition.optString(TYPE);
				if(typeMapping.containsKey(res)) res = typeMapping.get(res);

			} else if(definition.has(ALLOF) && definition.optJSONArray(ALLOF)!=null) {
				JSONArray allOfs = definition.optJSONArray(ALLOF);
				for(Object allof : allOfs) {
					if(allof instanceof JSONObject) {
						definition = (JSONObject) allof;
						if(definition.has(REF)) {
							res = getTypeByReference(definition.optString(REF));
						} 
					}
				}
			}
		}

		return res;
	}
	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAllCustomSimpleTypes(List<String> customSimpleTypes) {
		List<String> res = new LinkedList<>(customSimpleTypes);
		final List<String> referenced = new LinkedList<>();

		for(String type : customSimpleTypes) {
			if(!typeMapping.containsKey(type)) {

				JSONObject definition = getDefinition(type);

				LOG.debug("getAllCustomSimpleTypes: type={} definition={}", type, definition);

				if(definition==null) continue;

				if(definition.has(TYPE) && ARRAY.contentEquals(definition.getString(TYPE)) && definition.has(ITEMS)) {
					definition = definition.optJSONObject(ITEMS);
				}

				if(definition.has(REF)) {
					type = getTypeByReference(definition.optString(REF));
					if(!res.contains(type)) referenced.add(type); 
				} 


				if(definition.has(ALLOF) && definition.optJSONArray(ALLOF)!=null) {
					JSONArray allOfs = definition.optJSONArray(ALLOF);
					for(Object allof : allOfs) {
						if(allof instanceof JSONObject) {
							definition = (JSONObject) allof;
							if(definition.has(REF)) {
								type = getTypeByReference(definition.optString(REF));
								if(!res.contains(type)) referenced.add(type); 
							}  
						}
					}
				}

			}
		}

		LOG.debug("getAllCustomSimpleTypes: #1 referenced={}",  referenced);

		referenced.removeAll(res);
		if(!referenced.isEmpty()) {
			List<String> refres = getAllCustomSimpleTypes(referenced);
			refres.removeAll(referenced);
			referenced.addAll( refres );

			LOG.debug("getAllCustomSimpleTypes: #2 referenced={}",  referenced);

		}

		referenced.removeAll(res);
		res.addAll(referenced);

		LOG.debug("getAllCustomSimpleTypes: res={}",  res);

		return res;
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getOperationsByResource(String resource) {

		if(isAsyncAPI()) {
		
			List<String> res = new LinkedList<>();
			
			Map<String, AsyncResourceInfo> asyncDetails = APIModel.getAsyncDetails();
			
			AsyncResourceInfo resourceInfo = asyncDetails.get(resource);
			
			if(resourceInfo!=null) {
				res.addAll(resourceInfo.operations.keySet());
			}
			
			return res;
			
//			List<String> res = new LinkedList<>();
//
//			String api = swagger.toString();
//
//			Configuration configuration = Configuration.builder()
//					// .jsonProvider(new JacksonJsonProvider())
//					.build();
//
//			String query = "$.channels..message..['$ref']";
//
//			JsonPath jsonpath = JsonPath.compile(query);
//
//			List<String> msg = jsonpath.read(api, configuration );
//
//			LOG.debug("getAsyncMessages:: messages={}", Utils.joining(msg, "\n"));  
//
//			return msg;
		
		
		} else {
			List<String> allPaths = getPathsForResource(resource);
	
			List<String> res = allPaths.stream()
					.map(APIModel::getPath)
					.map(JSONObject::keySet)
					.flatMap(Set::stream)
					.map(String::toUpperCase)
					.distinct()
					.collect(toList());
	
			// the first part will not find DELETE operations
			// look for paths of the form /.../{..} where we have seen the first part, i.e. /.../
			getPaths().forEach( path ->  {
				String corePath = path.replaceAll("/\\{[^}]+\\}$", "");
	
				if(!allPaths.contains(corePath)) return;
	
				res.addAll( getOperationsForPath(path) );
	
			});
	
			return res.stream().distinct().map(String::toUpperCase).collect(toList());
		}

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getOperationsForPath(String path) {
		List<String> res=new LinkedList<>();
		JSONObject pathObj = getPathObjectByKey(path);
		if(pathObj!=null) {
			res.addAll( pathObj.keySet().stream()
					.map(String::toUpperCase)
					.toList() );
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static JSONObject getPath(String path) {
		return swagger.getJSONObject(PATHS).optJSONObject(path);
	}


	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getPathsForResource(String resource) {

		if(!pathsForResources.containsKey(resource)) {
			pathsForResources.put(resource, new LinkedList<>());

			getPaths().forEach( path ->  {

				List<String> foundResources = getResponseResourcesByPath(path);

				foundResources.forEach(found -> {
					if(!pathsForResources.containsKey(found)) {
						pathsForResources.put(found, new LinkedList<>());
					}
					pathsForResources.get(found).add(path);
				});

			});

		}

		return pathsForResources.get(resource);

	}

	@LogMethod(level=LogLevel.DEBUG)
	private static List<String> getResponseResourcesByPath(String path) {

		return getChildStream(getPathObjectByKey(path))
				.filter(APIModel::hasResponses)
				.map(APIModel::getResponseEntity)
				.map(APIModel::getNormalResponses)
				.flatMap(List::stream)
				.map(APIModel::getResourceFromResponse)
				.flatMap(List::stream)
				// 2022-11-04 .map(APIModel::getMappedResource)
				.collect(toList());

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getMappedResource(String resource) {
		String res=resource;
		LOG.debug("getMappedResource: resource={} resourceMapping={}", resource, resourceMapping);

		if(resourceMapping!=null && resourceMapping.has(resource) && resourceMapping.optString(resource)!=null) {
			res = resourceMapping.getString(resource);
			LOG.debug("getMappedResource: resource={} res={}", resource, res);
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static String getReverseResourceMapping(String resource) {
		String res=resource;
		if(reverseMapping!=null && reverseMapping.has(resource) && reverseMapping.optString(resource)!=null) {
			res = reverseMapping.getString(resource);
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject generateReverseMapping(JSONObject map) {
		JSONObject res = new JSONObject();

		if(map==null) return res;
		
		for(String key : map.keySet()) {
			if(map.optString(key)!=null) {
				res.put(map.getString(key), key);
			}
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Stream<JSONObject> getChildStream(JSONObject obj) {
		return new JSONObjectHelper(obj).getChildStream();
	}


	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getProperties(String resource) {
		Set<String> res = new HashSet<>();
		JSONObject obj = getPropertyObjectForResource(resource);
		if(obj!=null) res.addAll(obj.keySet());
		return res;
	}       

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getPropertiesExpanded(String resource) {
		Set<String> res = new HashSet<>();
		JSONObject obj = getPropertyObjectForResourceExpanded(resource);
		if(obj!=null) res.addAll(obj.keySet());
		return res;
	} 

	@LogMethod(level=LogLevel.DEBUG)
	public static Map<String,String> getMandatoryOptional(JSONObject resource) {
		Map<String,String> res = new HashMap<>();

		JSONObject core = getPropertyObjectForResource( resource );

		for(String property : core.keySet()) {
			String coreCondition = getMandatoryOptionalHelper(resource, property);
			if(coreCondition.contains("M")) {
				res.put(property, coreCondition);

			}
		}

		return res;
	}

	
	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getPropertyObjectForResourceExpanded(String node) {
		LOG.debug("getPropertyObjectForResourceExpanded: node={}",  node);

		JSONObject resource = getDefinition(node);
		
		LOG.debug("getPropertyObjectForResourceExpanded: node={} resource={}",  node, resource);

		JSONObject res = getPropertyObjectForResourceExpanded(node,resource);	
		
		LOG.debug("getPropertyObjectForResourceExpanded: node={} res={}",  node, res.toString(2));

		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG) 
	private static JSONObject getPropertyObjectForResourceExpanded(String node, JSONObject resource) {
		JSONObject res=getResourceExpanded(node,resource);
		if(res!=null && res.has(PROPERTIES)) return res.optJSONObject(PROPERTIES);
		
		return new JSONObject();
	}
	
		
	@LogMethod(level=LogLevel.DEBUG) 
	private static JSONObject getResourceExpanded(String node) {
		LOG.debug("getResourceExpanded: resource={}",  node);

		return getResourceExpanded(node,null);
	}
	
	@LogMethod(level=LogLevel.DEBUG) 
	private static JSONObject getResourceExpanded(String node, JSONObject resource) {
		JSONObject res=null;
		
		if(!resourceMapExpanded.containsKey(node)) {
						
			if(resource==null) resource=getDefinition(node);
			
			if(resource==null) return null;

			LOG.debug("getResourceExpanded: resource={} keys={}",  node, resource.keySet());
			LOG.debug("getResourceExpanded: resource={} required={}",  node, resource.optJSONArray(REQUIRED));

			res = resource;
			
			if(res!=null && res.has(ALLOF)) {
				
				LOG.debug("getResourceExpanded: resource={} def={}",  node, res.keySet());

				JSONObject allOfs = expandAllOfs(node, res.optJSONArray(ALLOF));
					
				LOG.debug("getResourceExpanded: resource={} allOfs={}",  node, allOfs.keySet());
				LOG.debug("getResourceExpanded: resource={} allOfs required={}",  node, allOfs.optJSONObject(REQUIRED));

				merge(node,res,allOfs);
			
				LOG.debug("getResourceExpanded: resource={} res required={}",  node, res.optJSONArray(REQUIRED));

				LOG.debug("getResourceExpanded: resource={} required={}",  node, res.optJSONArray(REQUIRED));

			}
			
			if(res!=null && res.has(ONEOF)) {
				
				LOG.debug("getResourceExpanded: resource={} def={}",  node, res.keySet());

				JSONObject allOfs = expandAllOfs(node, res.optJSONArray(ONEOF));
					
				LOG.debug("getResourceExpanded: resource={} allOfs={}",  node, allOfs.keySet());
				LOG.debug("getResourceExpanded: resource={} allOfs required={}",  node, allOfs.optJSONObject(REQUIRED));

				merge(node,res,allOfs);
			
				LOG.debug("getResourceExpanded: resource={} res required={}",  node, res.optJSONArray(REQUIRED));

				LOG.debug("getResourceExpanded: resource={} required={}",  node, res.optJSONArray(REQUIRED));

			}
			
			resourceMapExpanded.put(node, res);
			
			LOG.debug("getResourceExpanded: add resurceMapExpanded resource={} properties={}",  node, res);
			
			LOG.debug("getResourceExpanded: resource={} required={}",  node, res.optJSONArray(REQUIRED));

		}

		LOG.debug("getResourceExpanded: resource={} res={}",  node, resourceMapExpanded.get(node));

		return resourceMapExpanded.get(node);
		
	}

	
	@LogMethod(level=LogLevel.DEBUG) 
	private static JSONObject getResourceExpandedHelper(String node, JSONObject resource) {
		JSONObject res=new JSONObject(resource.toString());
		
		LOG.debug("getResourceExpanded: resource={} node={} keys={}",  resource, node, resource.keySet());
		LOG.debug("getResourceExpanded: resource={} required={}",  node, resource.optJSONArray(REQUIRED));
		
		if(res!=null && res.has(ALLOF)) {
			
			JSONObject allOfs = expandAllOfs(node, res.optJSONArray(ALLOF));
				
			merge(node,res,allOfs);
		
		}
			
		return res;
		
	}
	
	private static JSONObject expandAllOfs(String node, JSONArray allOfs) {
		JSONObject res = new JSONObject();
				
		if(allOfs!=null) {
			allOfs.forEach(allof -> {
				if(allof instanceof JSONObject) {
					LOG.debug("expandAllOfs: allof={}", allof);

					JSONObject allOfDefinition = (JSONObject) allof;
					if(!allOfDefinition.has(REF)) {
						LOG.debug("expandAllOfs: merging with allOfDefinition {}",  allOfDefinition);
						merge(node, res, allOfDefinition);
					}
				}
			});
			
			LOG.debug("expandAllOfs: res={}", res);

			allOfs.forEach(allof -> {
				if(allof instanceof JSONObject) {
					LOG.debug("expandAllOfs: allof={}", allof);

					JSONObject allOfDefinition = (JSONObject) allof;
					if(allOfDefinition.has(REF)) {
						String superior = getReferencedType(allOfDefinition, null); 
						
						LOG.debug("expandAllOfs: allOf={} superior={}", allOfDefinition, superior);

						JSONObject expanded =  getResourceExpanded(superior); // getPropertyObjectForResource(superior);
						
						LOG.debug("expandAllOfs: merging with resource {} keys {}",  superior, expanded.keySet());
						// LOG.debug("expandAllOfs: merging with resource {} ",  expanded.toString(2));

						merge(node, res, expanded);
						
						LOG.debug("expandAllOfs: merged res={}", res.keySet());

					}
				}
			});
		}
		
		LOG.debug("expandAllOfs: res={}", res);

		return res;
	}

	private static void merge(String node, JSONObject target, JSONObject add) {
		add = new JSONObject(add.toString());
		
		LOG.debug("merge: start {}", node);

		if(!target.has(PROPERTIES) && !add.has(PROPERTIES)) {
			// nothing to do
		} else if(!target.has(PROPERTIES) && add.has(PROPERTIES)) {
			target.put(PROPERTIES, add.get(PROPERTIES));
			mergeRequired(target,add);
		} else if(target.has(PROPERTIES) && add.has(PROPERTIES)) {
			JSONObject props = add.optJSONObject(PROPERTIES);
			JSONObject targetProps = target.optJSONObject(PROPERTIES);
			if(props!=null && targetProps!=null) {
				for(String prop : props.keySet()) {
					targetProps.put(prop,props.get(prop));
				}
				mergeRequired(target,add);

			}
		}
		
		for(String prop : add.keySet()) {
			if(!target.has(prop)) {
				target.put(prop, add.get(prop));
				LOG.debug("merge: add property {}", prop);
			}
		}
		
		target.remove(ALLOF);
		
		LOG.debug("merge: end: node={} target={}", node, target);

	}


	private static void mergeRequired(JSONObject target, JSONObject add) {
		LOG.debug("mergeRequired: target={} add={}", target, add);

		if(!target.has(REQUIRED) && add.has(REQUIRED)) {
			target.put(REQUIRED, add.get(REQUIRED));
		} else if(target.has(REQUIRED) && add.has(REQUIRED)) {
			JSONArray targetRequired = target.optJSONArray(REQUIRED);
			JSONArray addRequired = add.optJSONArray(REQUIRED);
			if(targetRequired!=null && addRequired!=null) {
				List<Object> l = targetRequired.toList(); 
				l.addAll(addRequired.toList());
				target.put(REQUIRED, l);
			}

		}
		
		LOG.debug("mergeRequired: target={}", target);

	}

	
	@LogMethod(level=LogLevel.DEBUG)
	public static String getMandatoryOptionalHelper(JSONObject definition, String property) {
		boolean required = false;

		String res="O";

		if(definition==null) return res;

		LOG.debug("getMandatoryOptionalHelper: property={} keys={}", property, definition.keySet());

		if(definition.optJSONArray("required")!=null) {

			JSONArray requiredProperties = definition.optJSONArray("required");

			required = requiredProperties.toList().stream()
					.map(Object::toString)
					.anyMatch(s -> s.equals(property));

		}

		if(!required) {
			JSONObject propertyDef = getPropertySpecification(definition, property); // TBD - need to check for embedded
			if(propertyDef!=null && propertyDef.optInt("minItems")>0) required=true;
		}

		return required ? "M" : "O";

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Map<String,String> getMandatoryOptional(String node, JSONObject resource) {
		Map<String,String> res = new HashMap<>();

		JSONObject core = getPropertyObjectForResourceExpanded( node, resource );

		LOG.debug("getMandatoryOptional: node={} core={}",node, core.keySet());

		for(String property : core.keySet()) {
			String coreCondition = getMandatoryOptionalHelper(resource, property);
			if(coreCondition.contains("M")) {
				res.put(property, coreCondition);

			}
		}

		return res;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public static Map<String,String> getMandatoryOptional(String resource, boolean includeSetByServer) {
		Map<String,String> res = new HashMap<>();

		JSONObject coreResource = getDefinition(resource);

		JSONObject core = getResourceExpanded(resource); // getPropertyObjectForResource( coreResource );

		LOG.debug("getMandatoryOptional: resource={} core={}",  resource, core);

		if(core==null) return res;

		LOG.debug("getMandatoryOptional: resource={}",  resource);
		
//		JSONObject createResource = getDefinition( getReverseResourceMapping(resource) + "_Create");
//		if(createResource==null) createResource = getDefinition( getReverseResourceMapping(resource) + "_FVO");
//		
//		JSONObject inputResource = getDefinition( getReverseResourceMapping(resource) + "Input");
//		if(inputResource==null) inputResource = getDefinition( getReverseResourceMapping(resource) + "_MVO");
//		
//		createResource = (createResource!=null) ? createResource : inputResource;
//
//		JSONObject create = getPropertyObjectForResourceExpanded( createResource ); // getPropertyObjectForResource( createResource );

		JSONObject createResource = getResourceExpanded( getReverseResourceMapping(resource) + "_Create");
		if(createResource==null) createResource = getResourceExpanded( getReverseResourceMapping(resource) + "_FVO");
		
		JSONObject inputResource = getResourceExpanded( getReverseResourceMapping(resource) + "Input");
		if(inputResource==null) inputResource = getResourceExpanded( getReverseResourceMapping(resource) + "_MVO");
		
		createResource = createResource!=null ? createResource : inputResource;

		// JSONObject create = getPropertyObjectForResourceExpanded( createResource ); // getPropertyObjectForResource( createResource );
				
		LOG.debug("getMandatoryOptional: resource={} createResource={}",  resource, createResource);

		Set<String> coreProperties = getPropertyKeys( core );
		Set<String> createProperties = getPropertyKeys( createResource );

		LOG.debug("getMandatoryOptional: resource={} coreProperties={} createProperties={}",  resource, coreProperties, createProperties);
		LOG.debug("getMandatoryOptional: resource={} createProperties={}",  resource, createProperties);

		if(core.has(PROPERTIES)) core=core.optJSONObject(PROPERTIES);
		
		for(String property : core.keySet()) {

			String coreCondition = getMandatoryOptionalHelper(coreResource, property);
			String createCondition = getMandatoryOptionalHelper(createResource, property);

			LOG.debug("getMandatoryOptional: resource={} property={} coreCondition={} createCondition={}",  resource, property, coreCondition, createCondition);

			if(coreCondition.contains("M")) {
				res.put(property, coreCondition);

			} else if(createCondition.contains("M")) {
				res.put(property, createCondition);

			} else if(createProperties!=null && !createProperties.isEmpty()) {

				Set<String> setByServer = Utils.difference(coreProperties, createProperties);
				List<String> globals = Config.get("globalsSetByServer");  // Arrays.asList("href", "id");

				if(includeSetByServer && setByServer.contains(property) && globals.contains(property)) {
					res.put(property, Config.getString("setByServerRule"));
				}
			}
		}

		return res;
	}

	private static Set<String> getPropertyKeys(JSONObject obj) {
		Set<String> res = new HashSet<>();
		if(obj!=null && obj.has(PROPERTIES)) obj=obj.optJSONObject(PROPERTIES);
		if(obj!=null) res=obj.keySet();
		return res;
	}

	public static JSONObject getResourceForPost(JSONObject opDetail, String resource) {
		JSONObject res = null;

		res = getDefinition( getReverseResourceMapping(resource) + "_Create");
		if(res==null) res = getDefinition( getReverseResourceMapping(resource) + "_FVO");
		if(res==null) res = getDefinition( getReverseResourceMapping(resource) + "Input");
		if(res==null) res = getDefinition( getReverseResourceMapping(resource) + "_MVO");

		if(opDetail!=null && opDetail.has("requestBody")) {
			JSONObject request = opDetail.optJSONObject("requestBody");
			if(request.has(REF)) res = APIModel.getDefinitionByReference(request.optString(REF));
			
		}
		
		if(false && res!=null) {
			LOG.debug("getResourceForPost: OPDETAIL resource={} res={}", resource, res.keySet());
			return res;
			
		} else {
			String name = getReverseResourceMapping(resource);
			List<String> suffix = List.of("_Create", "_FVO", "_Input", "_MVO");
			
			for(String suff : suffix) {
				res = APIModel.getResourceExpanded(name+suff); // getPropertyObjectForResourceExpanded( name + suff);
				if(res!=null && !res.isEmpty()) {
					LOG.debug("getResourceForPost: resource={} suffix={} res={}", resource, suff, res.keySet());
					LOG.debug("getResourceForPost: resource={} suffix={} required={}", resource, suff, res.optJSONArray(REQUIRED));
					break;
				}
			}
		}
		
		if(res!=null) LOG.debug("getResourceForPost: resource={} res={}", resource, res.keySet());

		return res;
	}

	public static JSONObject getResourceForPatch(String resource) {
		JSONObject res = getDefinition( getReverseResourceMapping(resource) + "_Update");
		if(res==null) res = getDefinition( getReverseResourceMapping(resource) + "_MVO");
		return res;
	}




	@LogMethod(level=LogLevel.DEBUG)
	private static List<JSONObject> getPathObjs() {
		return getPaths().stream().map(APIModel::getPathObjectByKey).collect(toList());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<String> getAllOperations() {
		List<String> res = new LinkedList<>();

		if(swagger==null) {
			LOG.info("... missing API specification (swagger)");
			return res;
		}

		swagger.getJSONObject(PATHS).keySet().forEach( path ->  {
			JSONObject pathObj = swagger.getJSONObject(PATHS).getJSONObject(path);
			pathObj.keySet().forEach( op ->
			res.add(op.toUpperCase())
					);
		});

		return res.stream().distinct().collect(toList());

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getDocumentDetails() {
		JSONObject res = new JSONObject();
		JSONObject variables = new JSONObject();

		if(swagger==null) return res;

		JSONObject info = swagger.optJSONObject("info");

		if(info!=null) {
			variables.put("ApiName", info.get("title"));
			variables.put("DocumentVersion", info.get("version"));

			String description = info.optString(DESCRIPTION);

			String documentNumber = "";

			Pattern pattern = Pattern.compile("[^:]*[ ]*TMF[^0-9]*([0-9]+)[.]*");
			Matcher matcher = pattern.matcher(description);
			if (matcher.find()) {
				documentNumber = matcher.group(1);
			}

			if(!documentNumber.isEmpty()) variables.put("DocumentNumber", "TMF" + documentNumber);

			LocalDate localDate = LocalDate.now();
			int year  = localDate.getYear();
			String month = Utils.pascalCase(localDate.getMonth().name());
			String date = month + " " + year;

			variables.put("Year", year);
			variables.put("Date", date);

			pattern = Pattern.compile("Release[^0-9]*([0-9]+.[0-9]+.?[0-9]?)");
			matcher = pattern.matcher(description);
			if (matcher.find()) {
				String release = matcher.group(1).trim();
				if(!release.isEmpty())      variables.put("Release", release);

			}

		}

		String basePath = swagger.optString("basePath");
		if(!basePath.isEmpty()) variables.put("basePath", basePath);

		if(!variables.isEmpty()) res.put("variables", variables);

		return res;

	}


	@LogMethod(level=LogLevel.DEBUG)
	public static String getResourceByPath(String path) {
		String res=null;

		Optional<String> optResource = getResponseResourcesByPath(path).stream().distinct().findFirst();

		if(optResource.isPresent()) res = optResource.get().replace("#/definitions/", "").replace("#/components/schemas/","");

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static List<JSONObject> getOperationsDetailsByResource(String resource, String operation) {
		List<JSONObject> res = new LinkedList<>();

		if(swagger==null) return res;

		JSONObject allpaths = swagger.optJSONObject(PATHS);

		String prefix = "/" + resource.toUpperCase();

		allpaths.keySet().stream()
				.filter(path -> isPathForResource(path,prefix))
				.sorted()
				.distinct()
				.forEach(path -> {
					JSONObject ops = allpaths.optJSONObject(path);					
					if(ops!=null && ops.has(operation.toLowerCase())) res.add(ops.optJSONObject(operation.toLowerCase())); 
				});

		return res;

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static JSONObject getOperationsDetailsByPath(String path, String op) {
		JSONObject res = null;

		if(swagger==null) return res;

		JSONObject allPaths = swagger.optJSONObject(PATHS);

		if(allPaths!=null && allPaths.has(path) && allPaths.optJSONObject(path)!=null) {
			JSONObject endpoint = allPaths.optJSONObject(path);
			if(endpoint.has(op.toLowerCase())) {
				res = endpoint.optJSONObject(op.toLowerCase());
			}
		}

		LOG.debug("getOperationsDetailsByPath: path={} op={} res={}",  path, op, res);

		return res;

	}

	public static String getSuccessResponseCode(String path, String op) {
		String res="";
		JSONObject operation = APIModel.getOperationsDetailsByPath(path, op);
		
		if(operation!=null && operation.has(RESPONSES)) {
			Set<String> responseCodes = operation.optJSONObject(RESPONSES).keySet().stream().filter(v -> v.startsWith("2")).collect(toSet());
			
			LOG.debug("getSuccessResponseCode: path={} op={} responseCodes={}",  path, op, responseCodes);
			
//			if(responseCodes.size()==1) {
//				res = responseCodes.iterator().next();
//			} else if(!responseCodes.isEmpty()) {
//				res = responseCodes.stream().sorted().distinct().findFirst().get();
//			} else {
//				Out.printAlways("... unable to extract unique success response code for " + path + " - found alternatives: " + responseCodes);
//			}
			if(!responseCodes.isEmpty()) {
				Optional<String> optRes = responseCodes.stream().sorted().distinct().findFirst();
				if(optRes.isPresent()) res = optRes.get();
			}
			if(!Config.getBoolean("ignore204Delete") && op.contentEquals("DELETE") && responseCodes.contains("204")) {
				res = "204";
			}
			
			LOG.debug("getSuccessResponseCode: path={} op={} responseCodes={} res={}",  path, op, responseCodes, res);

		}
		
		if(res.isEmpty()) {
			Out.printAlways("... unable to extract unique success response code for " + path + " and operation " + op);
		}

		return res;
	}

	public static JSONObject getMappingForResource(String resource) {
		
		return getDefinition(resource, DISCRIMINATOR, MAPPING);

	}

	@LogMethod(level=LogLevel.DEBUG)
	public static Set<String> getResourcesByOperation(String operation) {
		Set<String> res = new HashSet<>();

		if(operationCounter==null) {
			operationCounter = extractAllOperationsForResources(swagger);
		}

		if(operationCounter.containsKey(operation)) {
			res.addAll(operationCounter.get(operation).counts.keySet());
		}
		return res;
	}

	@LogMethod(level=LogLevel.DEBUG)
	private static Map<String,Counter> extractAllOperationsForResources(JSONObject swagger) {

		// map of counters per operation / method
		Map<String,Counter>  res = ALL_OPS.stream().collect(Collectors.toMap(p -> p, p -> new Counter()));

		if(swagger==null) {
			LOG.info("... missing API specification (swagger)");
			return res;
		}

		for(JSONObject pathObj : getPathObjs() ) {
			for(String op : pathObj.keySet()) {

				JSONObject opObj = pathObj.getJSONObject(op);

				List<String> resources = getChildStream(opObj)
						.filter(APIModel::hasResponses)
						.map(APIModel::getResponseEntity)
						.map(APIModel::getNormalResponses)
						.flatMap(List::stream)
						.map(APIModel::getResourceFromResponse)
						.flatMap(List::stream)
						// 2022-11-04 .map(APIModel::getMappedResource)
						.toList();

				for(String resource : resources) {
					res.get(op).increment(resource);
				}

			}
		}

		return res;

	}

	public static Map<String, JSONObject> getOperationResponsesByResource(JSONObject opDetails) {
		Map<String,JSONObject> res = new HashMap<>();
		
		if(opDetails.has(RESPONSES)) {
			JSONObject responses = opDetails.optJSONObject(RESPONSES);
			if(responses !=null) {
				for(String key : responses.keySet()) {
					res.put(key,  responses.getJSONObject(key));
				}
			}
			
		}
		
		return res;
		
	}
	
	public static JSONObject getOperationRequestsByResource(JSONObject opDetails) {
	
//	     #/requestBody:
//	         content:
//	           application/json:
//	             schema:
//	               $ref: '#/components/requestBodies/Pet'
//	             examples:
//		
		JSONObject res = new JSONObject();

		try {
			List<JSONPointer> alternativePaths = List.of ( 
													new JSONPointer( List.of( "requestBody", "content", "application/json") ),
													new JSONPointer( List.of( "requestBody" ) ) );

			Iterator<JSONPointer> iter = alternativePaths.iterator();
			while(iter.hasNext()) {
				JSONPointer p = iter.next();
				Object o = opDetails.optQuery(p);
				if(o!=null && (o instanceof JSONObject) ) {
					res = (JSONObject)o;
					
					LOG.debug("getOperationRequestsByResource:: res={}",  res);
					
					break;
				}
			}

		} catch(Exception e) {
			
		}
		
		return res;
		
	}

	public static Map<String, JSONObject> getOperationExamples(JSONObject data) {
		Map<String,JSONObject> res = new HashMap<>();
		
		LOG.debug("getOperationExamples:: data={}",  data.keySet());

		if(data.has(REF)) {
			data = APIModel.getDefinitionByReference(data.getString(REF));
			data = APIModel.getDefinition(data, "content", "application/json");
			
			LOG.debug("getOperationExamples:: data={}",  data);

		}
			
		if(data==null) return res;
		
		if(data.has(EXAMPLES)) {
			JSONObject examples = data.optJSONObject(EXAMPLES);
			if(examples !=null) {
				for(String key : examples.keySet()) {
					JSONObject payload = examples.getJSONObject(key);
					
					LOG.debug("getOperationExamples:: payload={}",  payload);

					if(payload.has(REF)) payload = APIModel.getDefinitionByReference(payload.getString(REF));
				
					if(payload.has(VALUE)) payload = payload.optJSONObject(VALUE);
					
					LOG.debug("getOperationExamples:: payload={}",  payload);

					res.put(key, payload);
				}
			}
		} else if(data.has(EXAMPLE)) {
			JSONObject example= data.optJSONObject(EXAMPLE);
			if(example.has(REF)) example = APIModel.getDefinitionByReference(example.getString(REF));

			if(example !=null) {
				res.put("default",  example);
			}
		} else {
	
		}
				
		LOG.debug("getOperationExamples:: res={}",  res);

		return res;

	}

	public static String getDiscriminatorReference(String node, String discriminator) {
		String res="";
		JSONObject def = APIModel.getDefinition(node);
		if(def!=null) def = def.optJSONObject(DISCRIMINATOR);
		if(def!=null) def = def.optJSONObject(MAPPING);
		if(def!=null) res = def.optString(discriminator);

		String parts[] = res.split("/");
		res=parts[parts.length-1];
		
		LOG.debug("getDiscriminatorReference: node={} discriminator={} res={}", node, discriminator, res);
		
		return res;
	}

	public static boolean isArrayType(String type) {
		boolean res=false;
		JSONObject definition = getDefinition(type);
		
		if(definition!=null && definition.has(TYPE) && definition.optString(TYPE).contentEquals(ARRAY) && definition.optJSONObject(ITEMS)!=null && definition.optJSONObject(ITEMS).has(REF)) res=true;
		
		LOG.debug("isArrayType: type={} res={} definition={}", type, res, definition);

		return res;
	}

	static Map<String,Integer> createdTypeCount = new HashMap<>();
	static Set<String> addedTypes = new HashSet<>();

	public static String createAsyncType(String typeName, JSONObject property) {
		LOG.debug("createAsyncType: typeName={} res={} property={}", typeName, property);
		
		String name = Utils.upperCaseFirst(typeName);
		
		if(createdTypeCount.containsKey(name)) {
			createdTypeCount.put(name, createdTypeCount.get(name)+1);
		} else {
			createdTypeCount.put(name, 1);
		}
		
		name = name + "_" + createdTypeCount.get(name);

		addedTypes.add(name);

		JSONObject def = new JSONObject();
		def.put("type",  "object");
		for(String key : property.keySet()) {
			def.put(key,  property.get(key));
		}
		
		allDefinitions.put(name, def);
		
		return name;
	}

	public static Set<String> getAddedAsyncTypes() {
		return addedTypes;
	}
	
	public static boolean isAddedType(String type) {
		return addedTypes.contains(type);
	}

}
