package no.paneon.api.utils;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import no.paneon.api.model.APIModel;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;


public class Config {

    private static final Logger LOG = LogManager.getLogger(Config.class);

    private static List<String> configFiles = new LinkedList<>();
    
    private static final String NEWLINE = "\n";

    private static JSONObject json = new JSONObject();
    
    public static void setConfigSources(List<String> files) {
    	configFiles.addAll(files);
    	forceConfig();
    }
    
    private static boolean skipInternalConfiguration=false;
    public static void setSkipInternalConfiguration(boolean val) {
    	skipInternalConfiguration = val;
    }
    
	@LogMethod(level=LogLevel.TRACE)
    public static void getConfig() {
       	Config.init();	
    }
    
	@LogMethod(level=LogLevel.TRACE)
    public static void forceConfig() {
    	initStatus=false;
    	getConfig();
    }
    
    private Config() {    	
    }
    
	@LogMethod(level=LogLevel.TRACE)
	public static void usage() {				
		if(json!=null) {
			Out.println(
					"Default configuration json (--config option):" + "\n" + 
					json.toString(2)
					);
			Out.println();
		}
	}
	
    private static boolean initStatus = false;
    
    public static void reset() {
    	configFiles = new LinkedList<>();
    }
    
    public static void init() {
    	if(initStatus) return;
    	initStatus = true;

		LOG.debug("Config.init");

    	try {
    		InputStream is ;
    		if(!skipInternalConfiguration) {
    			is = new ClassPathResource("configuration.json").getInputStream();
    			addConfiguration(is,"configuration.json");    
    			
    			LOG.debug("## config={}", json);

    		} 
    		
			LOG.debug("config={}", json);

			
    		for(String file : configFiles.stream().distinct().collect(toList())) {
    			Out.println("... adding configuration from file " + Utils.getBaseFileName(file));

    			is = Utils.openFileStream(workingDirectory, file);
    			
    			addConfiguration(is,file);
 
    		}
    		  	       		
    		Config.readRules();
    	    
		} catch (Exception e) {
			System.out.println("Error processing configuration files: " + e);
			System.exit(1);
		}
    }
    
	private static String workingDirectory; 
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setWorkingDirectory(String directory) {
		workingDirectory = directory;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public
    static void readRules() {
    	
		LOG.log(Level.TRACE, "readRules: rulesSource={}", rulesSource);

    	if(rulesSource==null) return;
    	
    	try {
			JSONObject o = Utils.readYamlAsJSON(rulesSource,true);
			
			LOG.debug("## setRulesSource: rules={}", o.toString(2));

			// next level, only one containing api attribute
			Optional<String> apiKey = o.keySet().stream().filter(k -> k.startsWith("api")).findFirst();
			
			if(apiKey.isPresent()) {
				rules=o.optJSONObject(apiKey.get());
				
				if(rules!=null) {
					LOG.debug("setRulesSource: rules={}", rules.toString(2));
				}
				
			} else {
				Out.println("... unable to read rules from {} ('api' property not found)", rulesSource);
			}
			
			if(LOG.isDebugEnabled() && rules!=null)
				LOG.debug("setRulesSource: rules={}", rules.toString(2));

		} catch(Exception e) {
			if(LOG.isDebugEnabled())
				LOG.log(Level.DEBUG, "setRulesSource: exception={}", e.getLocalizedMessage());
		}		
	}

	@LogMethod(level=LogLevel.TRACE)
	private static void addConfiguration(InputStream is, String name) throws InvalidJsonYamlException {
		try {
		    String config = IOUtils.toString(is, StandardCharsets.UTF_8.name());
		    
		    if(!config.isBlank()) {
			    if(name.endsWith("yaml")) config = Utils.convertYamlToJson(config);
			    			    
			    JSONObject deltaJSON = new JSONObject(config); 
			    
			    LOG.debug("addConfiguration:: deltaJSON={}",  deltaJSON);

			    addConfiguration(deltaJSON);
		    }
		    
		} catch(Exception ex) {
			throw(new InvalidJsonYamlException());
		}
   	
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void addConfiguration(JSONObject deltaJSON) {  	 		
	    for(String key : deltaJSON.keySet()) {	    	
	    	json.put(key, deltaJSON.get(key));
	    }	   	
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getConfiguration() {  	 		
		return json;   	
	}
	
	public static boolean has(String property) {
		init();
		return json!=null && json.has(property);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> get(String property) {
		try {
			JSONArray array = json.optJSONArray(property);
			return array.toList().stream().map(Object::toString).collect(Collectors.toList());
		} catch(Exception e) {
			return new LinkedList<>();
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean getBoolean(String property) {
		return json.optBoolean(property);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getString(String property) {
		return json.optString(property);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Map<String,String> getStringMap(String property) {
		Map<String,String> res = new HashMap<>();

		JSONObject obj = json.optJSONObject(property);			
		if(obj != null) {
			obj.keySet().forEach(key -> res.put(key, obj.get(key).toString()) );
		}

		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getObject(String property) {
		return json.optJSONObject(property);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONArray getArray(String property) {
		return json.optJSONArray(property);
	}

	private static String rulesSource=null;
	private static JSONObject rules=null;
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setRulesSource(String rulesFile) {
		
		if(rulesFile==null) return;
		
		rulesSource=rulesFile;
		readRules();
		
		LOG.debug("setRulesSource: rulesFile={}", rulesFile);

	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getRules() {
		if(rules==null && rulesSource!=null) readRules();
		return rules;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getStrings(String ... args) {
		List<String> res = new LinkedList<>();
		JSONObject o = getObject(args[0]);
		
		int i=1;
		while(i<args.length-1 && o!=null) {
			o = o.optJSONObject(args[i]);
			
			if(LOG.isDebugEnabled())	
				LOG.log(Level.DEBUG, "getStrings: i={} args={} o={}", i, args[i], ((o!=null) ? o.toString(2) : "null"));

			i++;
		}
		
		if(o==null) return res;

		if(o.optJSONArray(args[i])!=null) {
			res = o.optJSONArray(args[i]).toList().stream().map(Object::toString).collect(Collectors.toList());
			
			if(LOG.isDebugEnabled())
				LOG.log(Level.DEBUG, "getStrings: res={}", Utils.dump(res));
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getStringsByPath(String path, String element) {
		List<String> res = new LinkedList<>();
				
		try {
			Object o = getObjectByPath(getConfiguration(),path);
			if(o instanceof JSONObject) {
				JSONObject jo = (JSONObject)o;
				if(jo.optJSONArray(element)!=null) {
					res = jo.optJSONArray(element).toList().stream().map(Object::toString).collect(Collectors.toList());
					
					if(LOG.isDebugEnabled())
						LOG.log(Level.DEBUG, "getStringsByPath: res={}", Utils.dump(res));
				}
			}
			
		} catch(Exception e) {
			
			LOG.log(Level.DEBUG, "getStringsByPath: exception={}", e.getLocalizedMessage());
			
		}
		
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static Object getObjectByPath(JSONObject config, String path) {
		Object res = null;
		
		try {
			path = "#/" + path.replace(".",  "/");
			res = config.query(path);
			
		} catch(Exception ex) {
			Out.println("... configuration for '" + path + "' not found - exception: " + ex.getLocalizedMessage());
			Out.println("... configutation seen: " + config.toString(2));
		}
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Object getObjectByPath(JSONObject config, String path, boolean silentIfNotFound) {
		Object res = null;
		
		try {
			path = "#/" + path.replace(".",  "/");
			res = config.query(path);
			
		} catch(Exception ex) {
			if(!silentIfNotFound) {
				Out.println("... configuration for '" + path + "' not found - exception: " + ex.getLocalizedMessage());
				Out.println("... configutation seen: " + config.toString(2));
			}
		}
		
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getList(JSONObject config, String key) {
		List<String> res = new LinkedList<>();
				
		res.addAll( getListAsObject(config,key).stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toList()));
		
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<Object> getListAsObject(JSONObject config, String key) {
		List<Object> res = new LinkedList<>();
				
		if(config!=null && config.optJSONArray(key)!=null) {
			res.addAll(config.optJSONArray(key).toList());
		}
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getConfig(String key) {
		
		if(LOG.isTraceEnabled()) {
			LOG.log(Level.TRACE, "getConfig: key={} model={}", key, json.toString(2));
		}
	
		if(json==null || !json.has(key)) return new JSONObject();
		
		return json.optJSONObject(key);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getConfig(JSONObject config, String key) {
		JSONObject res=config;
		
		if(config==null) return res;
		
		JSONObject direct=config.optJSONObject(key);
		if(direct==null) {
			key=config.optString(key);
			if(!key.isEmpty()) res=getConfig(key);
		} else {
			res=direct;
		}
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getValuesForKey(JSONArray array, String key) {
	    return IntStream.range(0, array.length())
	      .mapToObj(index -> ((JSONObject)array.get(index)).optString(key))
	      .collect(Collectors.toList());
	}
	
	public static void setBoolean(String key, boolean value) {
		json.put(key, value);
	}

	public static Map<String, String> getTypeMapping() {
		return getMap("typeMapping");
	}

	public static Map<String, String> getFormatToType() {
		return getMap("formatToType");
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Map<String,String> getMap(String property) {
		Map<String,String> res = new HashMap<>();
		
		JSONObject obj = json.optJSONObject(property);
		
		if(obj!=null) obj.keySet().stream().forEach(key -> res.put(key,  obj.opt(key).toString()));

		return res;
	
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getSimpleTypes() {
		if(has("simpleTypes")) {
			return get("simpleTypes");
		} else {
			return new LinkedList<>( List.of("TimePeriod", "Money", "Quantity", "Tax", 
								 	 "Value", "Any", "object", "Number", "Date") );
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getCustomSimpleTypes() {
		List<String> res = new LinkedList<>();;
		if(has("customSimpleTypes")) {
			res.addAll( get("customSimpleTypes") );
		} 
		
		LOG.debug("getCustomSimpleTypes: res={}", res);

		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getSimpleEndings() {
		if(has("simpleEndings")) {
			return get("simpleEndings");
		} else {
			return List.of("Type", "Error");
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getNonSimpleEndings() {
		if(has("nonSimpleEndings")) {
			return get("nonSimpleEndings");
		} else {
			return List.of("RefType", "TypeRef");
		}
	}  
	
	
	private static String prefix = "";
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setPrefixToRemove(String str) {
		prefix=str;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String getPrefixToRemove() {
		return prefix;
	}
	

	private static String replacePrefix = "";
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setPrefixToReplace(String str) {
		replacePrefix=str;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String getPrefixToReplace() {
		return replacePrefix;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean getCompressCustomTypes() {
		return getBoolean("compressCustomTypes");
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getBaseTypesForResource(String resource) {
		List<String> res = new LinkedList<>();
		JSONObject baseTypeConfig = getJSONObject("baseTypes");
		
		JSONArray config = null;
		if(baseTypeConfig.has(resource)) {
			config = baseTypeConfig.optJSONArray(resource);
		} else {
			config = baseTypeConfig.optJSONArray("common");
		}
	
		if(config!=null) {
			res.addAll(config.toList().stream().map(Object::toString).toList());
		}
		
		return res;
	
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getLayout() {
		return getJSONObject("layout");
	}

	@LogMethod(level=LogLevel.TRACE)
	private static JSONObject getJSONObject(String label) {
		if(json.optJSONObject(label)!=null) 
			return json.optJSONObject(label);
		else
			return new JSONObject();
	}

	public static JSONObject getBaseTypes() {
		return getJSONObject("baseTypes");
	}
	
	private static Optional<Boolean> includeDebug = Optional.empty();

	@LogMethod(level=LogLevel.TRACE)
	public static void setIncludeDebug(boolean value) {
		LOG.debug("setIncludeDebug: " + value);

		includeDebug=Optional.of(value);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean getIncludeDebug() {
		boolean res=false;
		if(has("includeDebug")) 
			res=getBoolean("includeDebug");
		else
			res=includeDebug.isPresent() && includeDebug.get();
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getRequiredFormatting() {
		//if(optRequiredHighlighting.isPresent() && optRequiredHighlighting.get() && has("requiredHighlighting")) {
		if(getUseRequiredHighlighting()) {
			return getString("requiredHighlighting");
		} else {
			return "%s";
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String getDeprecatedFormatting() {
		return getString("deprecatedFormatting");
	}
	
	private static Optional<Boolean> optRequiredHighlighting = Optional.empty();
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setRequiredHighlighting(boolean value) {
		if(value) optRequiredHighlighting=Optional.of(value);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean getUseRequiredHighlighting() {
		LOG.debug("useRequiredHighlighting: opt=" + optIncludeDescription);
		if(has("useRequiredHighlighting")) {
			return getBoolean("useRequiredHighlighting");
		} else if(optRequiredHighlighting.isPresent()) {
			return optRequiredHighlighting.get();
		} else {
			return false;
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean includeDescription() {
		if(optIncludeDescription.isPresent()) 
			return optIncludeDescription.get();
		else if(has("includeDescription")) {
			return getBoolean("includeDescription");
		} else {
			return false;
		}
	}
	
	private static Optional<Boolean> optIncludeDescription = Optional.empty();
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setIncludeDescription(boolean value) {
		if(value) optIncludeDescription = Optional.of(value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getOrphanEnums() {
		List<String> res = new LinkedList<>();
		if(has("orphan-enums")) {
			res = get("orphan-enums");
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getOrphanEnums(String resource) {
		List<String> res = new LinkedList<>();
		
		JSONObject config = getJSONObject("orphan-enums-by-resource");
		
		if(config.has(resource)) {
			JSONArray enums = config.optJSONArray(resource);
			if(enums!=null)
				res = enums.toList().stream()
				.map(Object::toString)
				.collect(toList());
		}
				
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean getIncludeOrphanEnums() {
		boolean res = false;
		if(has("include-orphan-enums")) {
			res = getBoolean("include-orphan-enums");
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setOrphanEnums(String configFile) {
		try {
			JSONObject enum_config = null;
			if(configFile!=null) {
				LOG.debug("setOrphanEnums:: configFile=" + configFile);
				enum_config = Config.readJSONOrYaml(configFile);
			
				List<String> resources=new LinkedList<>();
				if(enum_config.has("orphan-enums") && enum_config.optJSONArray("orphan-enums")!=null) {
					resources.addAll( enum_config.getJSONArray("orphan-enums").toList().stream().map(Object::toString).toList() );
					
				}
				
				JSONObject config = enum_config.optJSONObject("orphan-enums-by-resource");
				if(config!=null) {
					json.put("orphan-enums-by-resource", config);
					resources.addAll( config.keySet().stream()
										.filter(item -> !resources.contains(item))
										.toList());
										
				}

				if(!resources.isEmpty()) json.put("orphan-enums", resources);

			}
			
		} catch(Exception e) {
			Out.println("error reading file: " + configFile);
			System.exit(0);
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject readJSONOrYaml(String file) {
		JSONObject res = null;
		try {
			if(file.endsWith(".yaml") || file.endsWith(".yml")) 
				res = readYamlAsJSON(file,false);
			else
				res = readJSON(file,false);
		} catch(Exception e) {
			Out.println("... unable to read file " + file + " (error: " + e.getLocalizedMessage() + ")");
			System.exit(0);
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	static JSONObject readJSON(String fileName, boolean errorOK) throws Exception {
		try {
			String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
	        File file = new File(path);
	        String content = FileUtils.readFileToString(file, "utf-8");
	        return new JSONObject(content); 
		} catch(Exception ex) {
			if(!errorOK) throw(ex);
			return new JSONObject();
		}
    }
	
	@LogMethod(level=LogLevel.TRACE)
	static JSONObject readYamlAsJSON(String fileName, boolean errorOK) throws Exception {
		try {
			String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
	        File file = new File(path);
	        String yaml = FileUtils.readFileToString(file, "utf-8");
	        if(yaml.isEmpty()) {
	        	return new JSONObject();
	        } else {
		        String json = convertYamlToJson(yaml);
		        return new JSONObject(json); 
	        }
		} catch(Exception ex) {
			if(!errorOK) throw(ex);
			return new JSONObject();
		}
    }
	
	@LogMethod(level=LogLevel.TRACE)
	static String convertJsonToYaml(JSONObject json) throws Exception {
		YAMLFactory yamlFactory = new YAMLFactory()	
			 .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES) 
	         .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
	         // .enable(YAMLGenerator.Feature.INDENT_ARRAYS)
	         ;
		
		YAMLMapper mapper = new YAMLMapper(yamlFactory);
	    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

		
	    ObjectMapper jsonMapper = new ObjectMapper();
	    jsonMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	    
	    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);

	    JsonNode json2 = mapper.readTree(json.toString());
	    
	    final Object obj = jsonMapper.treeToValue(json2, Object.class);
	    final String jsonString = jsonMapper.writeValueAsString(obj);

	    LOG.debug("convertJsonToYaml: json=" + jsonString);
	    
	    JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
        String jsonAsYaml = mapper.writeValueAsString(jsonNodeTree);
        return jsonAsYaml;
        
	}
	
	@LogMethod(level=LogLevel.TRACE)
    static String convertYamlToJson(String yaml) throws Exception {
	    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
	    Object obj = yamlReader.readValue(yaml, Object.class);

	    ObjectMapper jsonWriter = new ObjectMapper();
	    return jsonWriter.writeValueAsString(obj);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean getFloatingEnums() {
		boolean res=false;
		if(has("floatingEnums")) 
			res=getBoolean("floatingEnums");
		else
			res=floatingEnums.isPresent() && floatingEnums.get();
		return res;
	}

	private static Optional<Boolean> floatingEnums = Optional.empty();

	@LogMethod(level=LogLevel.TRACE)
	public static void setFloatingEnums(boolean value) {
		floatingEnums=Optional.of(value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void setArguments(String argfile) {
		if(argfile!=null) {
			 JSONObject args = readJSONOrYaml(argfile);
			 for(String key : args.keySet() ) {
				 json.put(key, args.get(key));
			 }
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean showDefaultCardinality() {
		if(has("showDefaultCardinality")) {
			return getBoolean("showDefaultCardinality");
		} else {
			return false;
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getDefaultCardinality() {
		if(has("defaultCardinality")) {
			return getString("defaultCardinality");
		} else {
			return "0..1";
		}
	}

	private static Optional<Boolean> showAllCardinality = Optional.empty();
	
	@LogMethod(level=LogLevel.TRACE)
	public static void setShowAllCardinality(boolean value) {
		showAllCardinality=Optional.of(value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean hideCardinalty(String cardinality) {		
		if(showAllCardinality.isPresent() && showAllCardinality.get())  
			return false;
		
		if(showDefaultCardinality()) 
			return false;
		
		return cardinality.equals(getDefaultCardinality());
	}
	
	 
	@LogMethod(level=LogLevel.TRACE)
	public static String getPuml() {
		if(has("puml")) {
			return String.join(NEWLINE, get("puml"));
		} else {
			return "@startuml" + NEWLINE +
			"'default config" + NEWLINE + 
            "hide circle" + NEWLINE +
            "hide methods" + NEWLINE +
            "hide stereotype" + NEWLINE +
            "show <<Enumeration>> stereotype" + NEWLINE +
            "skinparam class {" + NEWLINE +
            "   BackgroundColor<<Enumeration>> #E6F5F7" + NEWLINE +
            "   BackgroundColor<<Ref>> #FFFFE0" + NEWLINE +
            "   BackgroundColor<<Pivot>> #FFFFFFF" + NEWLINE +
            "   BackgroundColor #FCF2E3" + NEWLINE +
            "}" + NEWLINE +
            NEWLINE +
			"skinparam legend {" + NEWLINE +
		    "   borderRoundCorner 0" + NEWLINE +
		    "   borderColor red" + NEWLINE +
		    "   backgroundColor white" + NEWLINE +
			"}" + NEWLINE
            ;
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean includeHiddenEdges() {
		if(has("includeHiddenEdges")) {
			return getBoolean("includeHiddenEdges");
		} else {
			return false;
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static int getMaxLineLength() {
		int res=80;
		if(has("maxLineLength")) {
			res=json.getInt("maxLineLength");
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean hideCardinaltyForProperty(String cardinality) {
		return !getBoolean("showCardinalitySimpleProperties");
	}

	public static List<String> getAllSimpleTypes() {
		List<String> res = getSimpleTypes();
				
		res.addAll( APIModel.getMappedSimpleTypes() );
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean getSimplifyRefOrValue() {
		return getBoolean("simplifyRefOrValue");
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getDefaultStereoType() {
		return getString("defaultStereoType");
	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean processComplexity() {
		return getBoolean("processComplexity");
	}

    public static void setDefaults(String file) {
    	if(file!=null) {
     		JSONObject defaults = Utils.readJSONOrYaml(file);		
    		addConfiguration(defaults);
    		LOG.debug("setDefaults: data=" + defaults.toString(2));
    	}
    }

	@LogMethod(level=LogLevel.TRACE)
	private static void set(String label, Object value) {
		json.put(label,value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void setLayout(String layout) {
		if(layout!=null) {
			JSONObject o = readJSONOrYaml(layout);
			if(!o.isEmpty()) set("layout",o);
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void setConfig(String config) {
		if(config!=null) {
			JSONObject o = readJSONOrYaml(config);
			addConfiguration(o);
		}
	}

	public static List<String> getFlattenInheritance() {
		return get("coreInheritanceTypes");
	}

	public static List<String> getFlattenInheritanceRegexp() {
		return get("coreInheritanceRegexp");
	}
	
	public static List<String> getSubClassesExcludeRegexp() {
		return get("subClassExcludeRegexp");
	}

	public static int getInteger(String property) {
		try {
			String s = getString(property);
			return Integer.valueOf(s);
		} catch(Exception e) {
			Out.debug("... unable to process configuration property '{}' - expecting integer value", property);
			LOG.debug("... error={}",  e.getLocalizedMessage());
			
			return 0;
		}
	}


	public static List<String> getResourcesFromRules() {
		List<String> res = new LinkedList<>();
		
		JSONObject rules = Config.getRules();
		
		LOG.debug("getResourcesFromRules: rules={}", rules);	
		
		if(rules !=null && rules.optJSONArray("resources") != null) {
			LOG.debug("getResourcesFromRules: rules={}", rules);	

			boolean checkForExamples = Config.getBoolean("checkForResourceExamples");
			
			LOG.debug("getResourcesFromRules: checkForExamples={}", checkForExamples);

			Predicate<JSONObject> includeResource = obj -> obj.has("name") && 
					(!checkForExamples || checkForExamples && (obj.has("example") || obj.has("examples")));
			
			JSONArray resourcesRules = rules.optJSONArray("resources");
			Iterator<Object> iter = resourcesRules.iterator();
			while(iter.hasNext()) {
				Object o = iter.next();
				if(o instanceof JSONObject) {
					JSONObject rule = (JSONObject)o;
					if(includeResource.test(rule)) res.add(rule.optString("name"));
					
					LOG.debug("getResourcesFromRules: rule={}", rule.toString(2));

				}
			
			}

		}
				
		res = res.stream().distinct().collect(Collectors.toList());
		
		LOG.debug("#0 getResourcesFromRules: {}", res);

		return res;
		
	}
	
	public static JSONObject getRulesForResource(String resource) {
		JSONObject rules = Config.getRules();
		
		LOG.debug("getRulesForResource: resource={} rules={}", resource, rules);	

		if(rules !=null) {
			LOG.debug("getRulesForResource: resource={} rules={}", resource, rules);	

			String rulesKey = "rules " + resource;
			if(rules.has(rulesKey)) {
				JSONObject resourceRules = rules.optJSONObject(rulesKey);
				return resourceRules;
			}
			
			JSONArray resourcesRules = rules.optJSONArray("resources");
			if(resourcesRules!=null) {		

				Iterator<Object> iter = resourcesRules.iterator();
				while(iter.hasNext()) {
					Object o = iter.next();
					if(o instanceof JSONObject) {
						JSONObject resourceRules = (JSONObject)o;
						
						LOG.debug("getRulesForResource: resource={} rules={}", resource, rules);	

						if(resourceRules.has("name") && resourceRules.optString("name").contentEquals(resource)) {
							LOG.debug("getRulesForResource: resource={} rules={}", resource, rules);	
							return resourceRules;
						}
					}
				}				
			}
		}
		
		return null;
	}

	public static JSONObject getRulesForOperation(JSONObject rulesForResource, String op) {
		if(rulesForResource !=null) {

			JSONArray operationRules = rulesForResource.optJSONArray("supportedHttpMethods");
		
			if(operationRules==null) {
				
				JSONObject operationRulesObject = rulesForResource.optJSONObject("supportedHttpMethods");

				if(operationRulesObject.has(op)) {
					operationRulesObject = operationRulesObject.optJSONObject(op);
					
					return operationRulesObject;
				}
				
			} else {
	
				Iterator<Object> iter = operationRules.iterator();
				while(iter.hasNext()) {
					Object o = iter.next();
					if(o instanceof JSONObject) {
						JSONObject rulesForOperation = (JSONObject)o;
							
						if(rulesForOperation.has(op)) return rulesForOperation;
					}
				}	
			}
		}
		
		return null;
		
	}

	public static int getInteger(String property, int defaultValue) {
		int res=defaultValue;
		if(has(property)) {
			res=getInteger(property);
		}
		return res;
	}

	public static JSONObject getJSONObjectByPath(JSONObject config, String path) {
		Object o = config.optQuery(path);
		if(o!=null && o instanceof JSONObject) {
			return (JSONObject) o;
		} else {
			return null;
		}
	}


	public static void getCommandLineArgumentsFromConfig(Object args) {
		JSONObject cmdArgs = Config.getConfig("commandLineArguments");
		
		if(cmdArgs==null) return;
		
		for(String key : cmdArgs.keySet()) {
		    try {

		    	Class cls = Class.forName(args.getClass().getCanonicalName() );  // ("no.paneon.api.diagram.app.args.Diagram");

		        Field fld = cls.getField(key);
		        
				Object value = cmdArgs.get(key);
		        fld.set(args, value);
		        
				Out.debug("... using argument from configuration: {}={}", key, cmdArgs.get(key));

		    }
		    catch (Exception ex) {
				Out.debug("... unable to use argument '{}' from the configuration file, error={}", key, ex);
		    }
		}
	}
	
	public static Optional<Boolean> optIgnoreSchemaDomains = Optional.empty();;
	public static boolean ignoreSchemaDomains() {
		LOG.debug("ignoreSchemaDomains: opt=" + optIgnoreSchemaDomains);
		if(optIgnoreSchemaDomains.isPresent()) 
			return optIgnoreSchemaDomains.get();
		else if(has("ignoreSchemaDomains")) {
			return getBoolean("ignoreSchemaDomains");
		} else {
			return false;
		}
	}
	public static void setIgnoreSchemaDomains(boolean value) {
		optIgnoreSchemaDomains = Optional.of(value);
	}

	public static Optional<Boolean> optIncludeReferences = Optional.empty();;
	public static boolean includeReferences() {
		LOG.debug("includeReferences: opt=" + optIncludeReferences);
		if(optIncludeReferences.isPresent()) 
			return optIncludeReferences.get();
		else if(has("optIncludeReferences")) {
			return getBoolean("optIncludeReferences");
		} else {
			return false;
		}
	}
	
	public static void setIncludeReferences(boolean value) {
		optIncludeReferences = Optional.of(value);		
	}
	
	static List<String> localSources = new LinkedList<>();
	public static void setLocalSources(List<String> sources) {
		LOG.debug("setLocalSources: " + sources);
		localSources.addAll(sources);
	}
	
	public static List<String>  getLocalSources() {
		return localSources;
	}

	public static Optional<Boolean> optRepairSchemas = Optional.of(true);
	public static void setRepairSchemas(boolean value) {
		optRepairSchemas = Optional.of(value);
	}
	
	public static boolean repairSchemas() {
		return getBoolean("repairSchemas");
	}

	
}
