package no.paneon.api.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.regex.Pattern;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;

public class Schema {

    private static Logger LOG = LogManager.getLogger(Schema.class);
    
    // private static Config CONFIG = Config.getConfig();
    
    URL source;
    JSONObject jsonSource;
    JSONObject rawJSONSource = null;
    private String relativeName=null;
    private String baseName=null;

    
    Map<String,JSONObject> idMap = new HashMap<>();
    Map<String,String> idToTitle = new HashMap<>();
	private boolean fromSwagger = false;

    static Map<String,Schema> primarySourcesPathMap = new HashMap<>();
    static Map<String,Schema> localSourcesPathMap = new HashMap<>();
    static Map<String,Schema> fileMap = new HashMap<>();
    static Map<String,Schema> relativeFileMap = new HashMap<>();
    
    static Map<String,Map<String,Schema>> sourceAndDefinitionMap = new HashMap<>();


	private static Map<String,String> localSchemas = new HashMap<>();

	
	public Schema(URL source, File file, File baseDir) {
		this.source = source;
		this.baseName = baseDir.getAbsolutePath();
		LOG.debug("Schema: source=" + source);
		setRelativeRef(file, baseDir); 
	}
	
	public Schema(URL source, String baseName) {
		this.source = source;
		this.baseName=baseName;
	}

	public Schema(File file, File dir) {
		try {
			URL surl = file.toURI().toURL();
			this.source = surl;
			this.baseName = dir.getAbsolutePath();

			setRelativeRef(file, dir); 

		} catch(Exception e) {
			
		}
	}
	
	public Schema(File file) {
		try {
			URL surl = file.toURI().toURL();
			this.source = surl;
			this.baseName = file.getAbsolutePath();

			setRelativeRef(file, file); 

		} catch(Exception e) {
			
		}
	}
	
	public Schema(String resource, JSONObject swagger, String swaggerFile) {
		
		this.rawJSONSource=swagger;
		
		try {
			JSONObject defs = null; 
			String version = swagger.optString("openapi");
			
			if(version==null) 
				defs = swagger.getJSONObject("definitions");
			else
				defs = swagger.getJSONObject("components").getJSONObject("schemas");
			
			JSONObject json = defs.getJSONObject(resource);
			
			if(json!=null) {
				JSONObject schema = new JSONObject();
				schema.put("title", resource);
				
				JSONObject definitions = new JSONObject();
				definitions.put(resource,  json);

				schema.put("definitions",  definitions);
				
				this.jsonSource = schema;
				this.fromSwagger = true;
				this.baseName = swaggerFile;
				this.source = new File(swaggerFile).toURI().toURL();
				
				fileMap.put(resource, this);
				
				updateSourceAndDefinitionMap(resource,this,swaggerFile);

				LOG.debug("Schema::create from swagger resource=" + resource);

			}
			
		} catch(Exception e) {
			LOG.debug("Schema::create from swagger exception=" + e);
		}
	}

	private static void updateSourceAndDefinitionMap(String resource, Schema schema, String swaggerFile) {
		
		if(swaggerFile.contains("/")) {
			String[] parts = swaggerFile.split("/");
			swaggerFile = parts[parts.length-1];
		}
		
		LOG.debug("updateSourceAndDefinitionMap: resource=" + resource + " swaggerFile=" + swaggerFile);
		
		if(!sourceAndDefinitionMap.containsKey(swaggerFile)) sourceAndDefinitionMap.put(swaggerFile, new HashMap<>());
		
		sourceAndDefinitionMap.get(swaggerFile).put(resource, schema);
		
	}
	
	public static Schema getSchema(String resource, String source) {
		Schema res=null;
		
		if(sourceAndDefinitionMap.containsKey(source) && sourceAndDefinitionMap.get(source).containsKey(resource))
			res = sourceAndDefinitionMap.get(source).get(resource);
		
		if(res==null && source.contains("/")) {
			source = Utils.getLast(source,"/");
			if(sourceAndDefinitionMap.containsKey(source))
				res = sourceAndDefinitionMap.get(source).get(resource);
		}
		
		LOG.debug("getSchema: resource=" + resource + " source=" + source + " res=" + res);

		return res;
		
	}

	public List<String> load() {
		List<String> errors = new LinkedList<>();
		try {
			this.jsonSource = readJSON(this.source.getFile(), false);
			this.rawJSONSource = this.jsonSource;
			updateFileMap(this.source);
			// System.out.println("... read json schema file=" + source.getFile());

		} catch(Exception e) {
			System.out.println("Schema::load exception=" + e);

			LOG.debug("Schema::load exception=" + e);
			errors.add("Error reading json from file=" + source.getFile() + " error=" + e.getMessage());
		}
		return errors;
	}
	
	enum CachePolicy {
		ALL,
		PRIMARY_ONLY
	}
	
	public Schema getCachedSchema(URL url, CachePolicy cache) {
		Schema res = null;
		
		LOG.debug("getCachedSchema: url=" + url);

		if(primarySourcesPathMap.containsKey(url.getPath())) {
			res = primarySourcesPathMap.get(url.getPath());
		}
		if(res==null && cache==CachePolicy.ALL && localSourcesPathMap.containsKey(url.getPath())) {
			res = localSourcesPathMap.get(url.getPath());
		}
		
		String fileName = url.getFile();
		fileName = fileName.replace(this.baseName, "").replaceAll("^/", "");
		if(relativeFileMap.containsKey(fileName)) {
			res = relativeFileMap.get(fileName); 
			LOG.info("getCachedSchema: fileName=" + fileName + " found=" + res);
		}
		
		if(res==null && Config.ignoreSchemaDomains()) {
			fileName = getFileName(url);
			LOG.info("getCachedSchema: fileName=" + fileName + " found=" + fileMap.containsKey(fileName));
			if(fileMap.containsKey(fileName)) res = fileMap.get(fileName); 
		}
		
		LOG.debug("getCachedSchema: url=" + url + "res=" + res);
		
		return res;
	}
	
	private static String getFileName(URL url) {
		String res="";
		String s = url.getFile();
		String parts[] = s.split("/");
		if(parts.length>0) res = parts[parts.length-1];
		
		return res;
	}

	private String loadSchemaHelper(URL url) throws Exception {
		String content=null;
		URLConnection urlConnection = url.openConnection();
		InputStream inputStream = urlConnection.getInputStream();
		content = readFromInputStream(inputStream);
		LOG.debug("loadSchemaHelper: url=" + urlConnection + " content=" + content);
		return content;
	}

	public List<String> loadSchema() throws Exception {
		List<String> errors = new LinkedList<>();
		
		List<String> localSources = Config.getLocalSources();
		LOG.debug("loadSchema: source=" + source + " local sources=" + localSources);

		String content=null;
		try {
			content = loadSchemaHelper(source);
			
			try {
				jsonSource = new JSONObject(content);
				
				if(jsonSource.has("swagger")) {
					errors.add("Found swagger file - expected JSON schema (use --swagger option if intended)");
				}
				
			} catch(Exception e) {
				errors.add(this.getLocalName() + " :: " + e.getMessage());
			}
		
		} catch(FileNotFoundException e) {		
			
			LOG.debug("loadSchema: #1a exception=" + e + " relativeName=" + relativeName);
			
			Schema s = getCachedSchema(source, CachePolicy.ALL);
			if(s!=null) {
				source = s.source;
				jsonSource = s.jsonSource;
			}
			
		} catch(Exception e) {		
			
			LOG.debug("loadSchema: #1b exception=" + e + " relativeName=" + relativeName);
			
		} 

		if(jsonSource==null && content==null && relativeName!=null) {
			String[] parts = relativeName.split("#");
			String fname = parts[0];
			File f = new File(fname);
			fname = f.getName();
			
			if(localSchemas.containsKey(fname)) {
				String localSource = localSchemas.get(fname);
				URL surl = new File(localSource).toURI().toURL();

				try {
					content = loadSchemaHelper(surl);
				} catch(Exception e2) {
					LOG.debug("loadSchema: #1a exception=" + e2 + " relativeName=" + relativeName);
					
					Schema s = getCachedSchema(surl, CachePolicy.ALL);
					if(s!=null) {
						source = s.source;
						jsonSource = s.jsonSource;
					}
				}
			} else {
				LOG.debug("loadSchema: relativeName=" + relativeName);
			}
		
			if(jsonSource==null && content==null) {
				errors.add(fname + " :: Not found");				
				return errors;
			} else {
				addPrimary(); 
				
				LOG.debug("loadSchema: url=" + source + " path=" + source.getPath());
			}
		}
		
			
		if(content==null || !errors.isEmpty()) return errors;
		
		try {
			jsonSource = new JSONObject(content);
		} catch(Exception e) {
			errors.add(this.getLocalName() + " :: " + e.getMessage());
		}
		
		updateFileMap(source);


		if(Config.repairSchemas()) {
			repairSchema();
		}
		
		List<String> errs = extractIds(null,jsonSource);
		LOG.debug("loadSchema: extractIds errors=" + errs);

		errors.addAll(errs);

		errs = replaceRefs(jsonSource);
		
		LOG.debug("loadSchema: replaceRefs errors=" + errs);

		errors.addAll(errs);

		return errors;
	}
	
	public void updateFileMap() {
		updateFileMap(this.source);
	}
	
	private void updateFileMap(String key) {
		// TODO Auto-generated method stub
		System.out.println("... updateFileMap " + key + " this=" + this + " json=" + this.jsonSource.keySet());

	}
	
	private void updateFileMap(URL source) {
		
		if(source!=null) {
			String parts[] = source.getPath().split("/"); // Pattern.quote(File.separator));
			String key = parts[parts.length-1];
			
			// if(!fileMap.containsKey(key)) fileMap.put(key, this);
			if(fileMap.containsKey(key)) {
				Schema previous = fileMap.get(key);
				if(!this.baseName.equals(previous.baseName)) {
					System.out.println("... schema " + key+ " already found" + "\n" +
									   "... ... ignoring duplicate in " + this.baseName + "\n" + 
									   "... ... using version in " + previous.baseName
							);
				}
			}
			
			// System.out.println("... updateFileMap " + key + " this=" + this + " json=" + this.jsonSource.keySet());
					
			fileMap.put(key, this);
		}
	}
	
	private void updateRelativeFileMap(URL source) {
		String key = source.getPath().replace(this.baseName, "").replaceAll("^/",  "");
				
		if(relativeFileMap.containsKey(key)) {
			Schema previous = relativeFileMap.get(key);
			if(!this.baseName.equals(previous.baseName)) {
				System.out.println("... schema " + key+ " already found" + "\n" +
								   "... ... ignoring duplicate in " + this.baseName + "\n" + 
								   "... ... using version in " + previous.baseName
						);
			}
		}
		relativeFileMap.put(key, this);
	}

	
	private void repairSchema() {
		if(jsonSource!=null) {
			LOG.debug("repairSchema: jsonSource= " + jsonSource);
			JSONObject definitions = jsonSource.optJSONObject("definitions");
			if(definitions!=null) {
				String title = jsonSource.optString("title");
				if(title!=null) {
					JSONObject obj = definitions.optJSONObject(title);
					if(obj!=null && !obj.has("$id")) {
						obj.put("$id", "#" + title);
						LOG.info("repairSchema: adding missing $id to schema " + title);
					}
				}
			}
		}
	}
	
	
	public String lookupFileTree(File dir, String name) {
		String res=null;
	    if(dir.listFiles()==null){
	    	return res;
	    }
	    for (File entry : dir.listFiles()) {
        	LOG.debug("lookupFileTree: name=" + name + " dir=" + dir.getName() + " checking entry=" + entry.getName());
	        if (entry.isFile() && entry.getName().equals(name)) {
	        	res = entry.getPath();
	        }
	        else if(entry.isDirectory()) {
	        	res = lookupFileTree(entry, name);
	        }
	        if(res!=null)
	        	LOG.debug("lookupFileTree: name=" + name + " entry=" + entry + " res=" + res);

	        if(res!=null) return res;
	    }
	    return res;
	}

	private List<String> extractIds(String parent, JSONObject json) {
		List<String> errors = new LinkedList<>();
		if(json==null) return errors;
		
		LOG.trace("extractIds: json=" + json);
		json.keySet().forEach(key -> {
			if(key.equals("$id")) {
				String id = json.getString(key);
				setJSONById(id, json);
				if(parent!=null) setSchemaTitleById(id, parent);

				LOG.trace("extractIds: found $id=" + id);
			} else {
				JSONArray jsonArray = json.optJSONArray(key);
				if(jsonArray!=null) {
					jsonArray.forEach(obj -> {	
						if(obj instanceof JSONObject) {
							List<String> err = extractIds( key, (JSONObject)obj);
							errors.addAll(err);
						}
					});		
				} else {
					JSONObject jsonObject = json.optJSONObject(key);
					if(jsonObject!=null) {
						List<String> err = extractIds( key, jsonObject);
						errors.addAll(err);
					} 
				}
			}
		});
		return errors;
	}

	static List<String> processedSchema = new LinkedList<>();
	
	private List<String> replaceRefs(JSONObject json) {
		List<String> errors = new LinkedList<>();
		
		if(json==null) return errors;
		
		// if(processedSchema.contains(this.source.getFile())) return errors;
		
		// processedSchema.add(this.source.getFile());
		
		for( String key: json.keySet()) {
			if(key.equals("$ref")) {
				
				String ref = json.getString(key);
				LOG.trace("replaceRefs: found $ref=" + ref);
				
				if(processedSchema.contains(ref)) return errors;
				processedSchema.add(ref);
				
				if(ref.startsWith("#/")) continue;
				
				String parts[] = ref.split("#");
				String newPath = getPath()  + "/" + parts[0];
				
				LOG.trace("replaceRefs: newPath=" + newPath);

				newPath = newPath.replaceAll("[A-Za-z0-9_]+/[.]{2}/", "");

				URL url = null;
				try {
					url = new URL(source,newPath);
					LOG.trace("replaceRefs: url=" + url);

					// System.out.println("replaceRefs: url=" + url);

					// Schema s = getCachedSchema(url, CachePolicy.ALL);
					Schema s = getSchema(url.getPath());
					if(s==null) {
						LOG.info("getCachedSchema: url=" + url);
						s = new Schema(url, this.baseName);
						List<String> errs = s.loadSchema();
						errors.addAll(errs);
						if(!errs.isEmpty()) return errors;
						
					}
					
					if(parts.length==1) {
						
					} else if(!ref.contains("#/") && parts.length==2) {
						String id = "#" + parts[1];
						JSONObject o = s.getJSONById(id);
						if(o!=null) {
							LOG.debug("replaceRefs: id=" + id + " ref by id=" + o.toString(2));
							// TODO json.put("§dereferenced", o);
							String parent = s.getSchemaTitleById(id);
							// TODO if(parent!=null) json.put("§parent", s.getSchemaTitleById(id));
						} else {
							// TODO
						}

					} else {
						String pathElements[] = parts[1].split("/"); // Pattern.quote("/")); // File.separator
						LOG.debug("replaceRefs: parts=" + String.join(" ", pathElements));
						
						JSONObject o = s.jsonSource;
						if(o!=null) {
							for(String elem: pathElements) {
								if(o.has(elem)) {
									o = o.getJSONObject(elem);
								}
							}
						} else {
							errors.add( this.getRelativeName() + " :: " + "deferencing failed for reference " + ref + " as location " + s.getRelativeName());
						}
						
						LOG.debug("replaceRefs: o=" + o);

					}
					
				} catch(Exception ex) {
					LOG.debug("replaceRefs: ex2=" + ex);
					String path = url.getPath();
					if(path!=null) {
						parts = path.split("/");
						path = parts[parts.length-1];
					}
					errors.add(path + " :: " + ex); // .getMessage());
					ex.printStackTrace();
				}


			} else {
				JSONArray jsonArray = json.optJSONArray(key);
				if(jsonArray!=null) {
//					jsonArray.forEach(obj -> {	
//						if(obj instanceof JSONObject) {
//							List<String> errs = replaceRefs( (JSONObject)obj);
//							errors.addAll(errs);
//						}
//					});		
				} else {
					JSONObject jsonObject = json.optJSONObject(key);
					if(jsonObject!=null) {
						List<String> errs = replaceRefs(jsonObject);
						errors.addAll(errs);
					} 
				}
			}
		}	
		return errors;
		
	}
	

	private String getSchemaTitleById(String id) {
		String res=null;
		if(idToTitle.containsKey(id)) {
			res=idToTitle.get(id);
		}
		LOG.trace("getSchemaTitleById: id=" + id + " res=" + res + " keys=" + idMap.keySet());
		return res;
	}
	
	private void setSchemaTitleById(String id, String title) {
		LOG.trace("setJSONById: id=" + id + " title=" + title);

		if(!idToTitle.containsKey(id)) {
			idToTitle.put(id,title);
		}
	}
	
	private JSONObject getJSONById(String id) {
		JSONObject res=null;
		if(idMap.containsKey(id)) {
			res=idMap.get(id);
		}
		LOG.trace("getJSONById: id=" + id + " res=" + res + " keys=" + idMap.keySet());
		return res;
	}
	
	private void setJSONById(String id, JSONObject o) {
		LOG.trace("setJSONById: id=" + id + " json=" + o);

		if(!idMap.containsKey(id)) {
			idMap.put(id,o);
		}
	}
	
	public String getTitle() {
		String res=null;
		if(jsonSource!=null && jsonSource.has("title")) {
			res=jsonSource.getString("title");
		}
		LOG.trace("getTitle: res=" + res);
		return res;
	}
	
	private String getPath() {
		String res = "";
		String path = source.getPath();
		if(path.contains("/")) {
			LOG.trace("getPath: path=" + path);

			int pos = path.lastIndexOf('/');
			res = path.substring(0,pos);
		} 
		LOG.trace("getPath: res=" + res);
		return res;
	}

	static JSONObject readJSON(String url, boolean errorOK) throws Exception {
		try {
			URL urlObject = new URL("file://" + url);
			LOG.debug("readJSON: protocol=" + urlObject.getProtocol());
			URLConnection urlConnection = urlObject.openConnection();
			InputStream inputStream = urlConnection.getInputStream();
			String content = readFromInputStream(inputStream);
			return new JSONObject(content); 
		} catch(Exception ex) {
			if(!errorOK) throw(ex);
			return new JSONObject();
		}
	}

	private static String readFromInputStream(InputStream inputStream)
			throws IOException {
		StringBuilder resultStringBuilder = new StringBuilder();
		try (BufferedReader br
				= new BufferedReader(new InputStreamReader(inputStream))) {
			String line;
			while ((line = br.readLine()) != null) {
				resultStringBuilder.append(line).append("\n");
			}
		}
		return resultStringBuilder.toString();
	}

	public boolean isSimpleType() {
		JSONObject definitions = getDefinitions();
		String coreType = definitions.optString("type");

		System.out.println("isSimpleType: coreType=" + coreType);
			
		if(coreType.equals("object")) return false;
		
		if(Utils.isSimpleType(coreType)) {
			return true;
		}

		System.out.println("isSimpleType: #2");

		String ref = definitions.optString("$ref");
		if(ref.length()>0) {
			System.out.println("isSimpleType: #3 ref=" + ref );
			Schema s = getSchema(ref);
			if(s!=null) return s.isSimpleType();
		}
		
		if(definitions.has("anyOf")) {
			JSONArray anyOf = definitions.getJSONArray("anyOf");
			System.out.println("isSimpleType:: anyOf = " + anyOf.toString(2));
			boolean simple=true;
			for(int i=0; i<anyOf.length(); i++) {
				JSONObject o = anyOf.optJSONObject(i);
				if(o.has("type")) simple = simple && o.optString("type").equals("string");
			}
			System.out.println("isSimpleType: keys=" + definitions.keySet().stream().collect(Collectors.joining(",")) + " simple=" + simple);
			return simple;
		}
		System.out.println("isSimpleType: keys=" + definitions.keySet().stream().collect(Collectors.joining(",")) );
		
		return false;
	}
	
	public Schema getSchema(String ref) {
		Schema res=null;
		
		String[] parts = ref.split("/");
		String resource = parts[parts.length-1];
	    String sourcedFrom;

		parts = ref.split("#");
		if(parts.length>1) {
			sourcedFrom=parts[0];
		} else {
			sourcedFrom=this.baseName;
		}
		
		LOG.debug("getSchema: ref=" + ref + " fromSwagger=" + fromSwagger);

//		if(ref.startsWith("#") && !fromSwagger) {
//			return this;
//		}
		
//		if(ref.startsWith("#/") && fromSwagger) {
//						
//			LOG.debug("getSchema: ref=" + ref + " lookup from swagger resource=" + resource);
//
//			res = getSchemaByName(resource);
//			return res;
//			
//		}
	
		if(ref.contains("#") && !ref.startsWith("#")) {
	    	if(ref.contains("/")) {
		    		
		    	res = Schema.getSchema(resource, sourcedFrom);
		    		
				LOG.debug("getSchema: #1 ref=" + ref + " resource=" + resource + " res=" + res);

		    	if(res!=null) return res;
		    	   
		    }
		} else if(ref.contains("#") && ref.startsWith("#")) {
	    	res = Schema.getSchema(resource, this.baseName);
    		
			LOG.debug("getSchema: #2 ref=" + ref + " resource=" + resource + " res=" + res);

	    	if(res!=null) return res;
		}
				
		String newPath = getPath()  + "/" + parts[0];
		
		LOG.debug("getSchema: newPath=" + newPath);
	
		newPath = newPath.replaceAll("[A-Za-z0-9_]+/[.]{2}/", "");
		
		try {
			URL url1 = new URL(source,newPath);
			URL url2 = null;

			LOG.debug("getSchema: #1 url=" + url1);
			
			res = getCachedSchema(url1, CachePolicy.ALL);
		
			if(res==null) {
				newPath = parts[0].replaceAll("[.]{2}/", "");
				url2 = new URL(source, newPath);
				LOG.debug("getSchema: #2 url=" + url2);
				res = getCachedSchema(url2, CachePolicy.ALL);

			}
			
			if(res==null && Config.ignoreSchemaDomains()) {
				resource = parts[parts.length-1];
				
				LOG.debug("getSchema: ref=" + ref + " lookup by name resource=" + resource);

				res = getSchemaByName(resource);
			}
			
			if(res==null) {
				Schema s = new Schema(url1,this.baseName);
				try {
					List<String> errs = s.load();
					if(errs.isEmpty()) {
						res = s;
					} else {
						s = new Schema(url1,this.baseName);
						errs = s.load();
						if(errs.isEmpty()) {
							res = s;
						}
					}
				} catch(Exception e) {
					LOG.error("getSchema: ex=" + e);
				}
			}
			
			if(res!=null && fromSwagger) {
				parts = ref.split("/");
				resource = parts[parts.length-1];
				LOG.debug("getSchema: ref=" + ref + " keys=" + res.getDefinitionsKeys().stream().collect(Collectors.joining(",")));
			}
			
		} catch(Exception ex) {
			LOG.error("getSchema: ex=" + ex);
		}
		
		LOG.debug("getSchema: #99 res=" + res + ((res!=null) ? " schema=" + res.getTitle() : ""));

		return res;
	}
	
	
	static boolean first=true;
	private static Schema getCachedSchemaByFilename(String file) {
		if(first) System.out.println("relativeFileMap:: " + relativeFileMap.keySet().stream().collect(Collectors.joining(",")));
		first=false;
		return relativeFileMap.get(file);
	}

	private boolean isPivot = false;
	
	public boolean isPivot() {
		return isPivot;
	}

	public void setPivot() {
		isPivot=true;
	}
	
	public boolean hasMultipleDefinitions() {
		boolean res=false;
		
		JSONObject definitions = this.getRawDefinitions();
		
		if(!isSourcedFromAPI())
			res = definitions.keySet().size()>1;
		    
	    return res;
	}
	
	private boolean isSourcedFromAPI() {
		return this.isOpenAPIv3() || this.isOpenAPIv2();
	}

	public Set<String> getDefinitionsKeys() {
		
		JSONObject definitions = this.getRawDefinitions();
		
		if(definitions!=null)
			return definitions.keySet();
		else   
			return new HashSet<>();
	}
	
	public JSONObject getDefinitions() {
		JSONObject res = jsonSource;
		String title=getTitle();
			    	    
	    LOG.debug("### getDefinitions: schema=" + title);
	    
		JSONObject definitions = this.getRawDefinitions();

	    if(!definitions.has(title)) {
	        LOG.debug("getDefinitions: schema " + title + " not found in specification - returning base object");
	    } else {
	    	res = definitions.getJSONObject(title);
	    }	
	    
	    return res;
	}
	
	public JSONObject getDefinitions(String key) {
		
		if(key.contains("#")) {
			String[] parts = key.split("/");
			key = parts[parts.length-1];
		}
		
		JSONObject res = new JSONObject();

		JSONObject definitions = getRawDefinitions();

	    if(definitions==null || !definitions.has(key)) {
	        return res;
	    } 
	   
	    res = definitions.getJSONObject(key);
	    
	    return res;
	}

	private JSONObject getRawDefinitions() {
		JSONObject res = null;
		try {
			if(isOpenAPIv3()) {
				res = rawJSONSource.optJSONObject("components").getJSONObject("schemas");
			} else {
				res = rawJSONSource.getJSONObject("definitions");
			}
		} catch(Exception e) {
			res = new JSONObject();
		}
		return res;
	}

	private boolean isOpenAPIv3() {
		return rawJSONSource!=null && rawJSONSource.optString("openapi").startsWith("3");
	}
	
	private boolean isOpenAPIv2() {
		return rawJSONSource!=null && rawJSONSource.optString("swagger").startsWith("2");
	}

	public static Schema getSchemaByRef(String ref) {
		Schema res=null;
		
		LOG.debug("getSchemaByRef: ref=" + ref);
			
		if(ref.startsWith("#")) {
			if(ref.contains("/")) {
				String parts[] = ref.split("/");
				ref = parts[parts.length-1];	
			}
			res = getSchemaByName(ref);
			
			return res;
		} 
		
		try {
			String[] parts = ref.split("#");
					
			LOG.debug("getSchemaByRef: len=" + parts.length);

			if(parts.length>1) {
				
				LOG.debug("getSchemaByRef: parts=" + Arrays.asList(parts).stream().collect(Collectors.joining(",")));

				String resource = Utils.getLast(parts[1],"/");
				String source = parts[0];
			
				LOG.debug("getSchemaByRef: resource=" + resource + " source=" + source);

				res = getSchema(resource, source);
			} else {
				String resource = Utils.getLast(parts[0],"/");
				
				res = Schema.getSchemaByName(resource);
			}
			
			LOG.debug("getSchemaByRef: ref=" + ref + " res=" + res);

			
		} catch(Exception ex) {
			LOG.debug("getSchema: ex=" + ex);
			ex.printStackTrace();
		}
		
		return res;
	}

	private static Schema getCachedSchemaByRef(String ref) {
		Schema res=null;
		
		// System.out.println("getCachedSchemaByRef: ref=" + ref);

		LOG.debug("getCachedSchemaByRef: ref=" + ref);

		// System.out.println("## getSchemaByRef: ref=" + ref);
		// System.out.println("## getSchemaByRef: fileMap=" + fileMap.keySet());

		if(fileMap.containsKey(ref)) {
			res = fileMap.get(ref);
			
			LOG.debug("####### getCachedSchemaByRef: ref=" + ref + " res=" + res);

		} else {
			// System.out.println("getCachedSchemaByRef: not found ref=" + ref);
			// System.out.println("getCachedSchemaByRef: keys=" + fileMap.keySet());


		}
		return res;
	}

	public void addPrimary() {
		primarySourcesPathMap.put(this.source.getPath(),this);
	}
	
	public void addLocalSource() {
		localSourcesPathMap.put(this.source.getPath(),this);
		updateFileMap(this.source);
		updateRelativeFileMap(this.source);
	}

	public void setRelativeRef(File file, File baseDir) {
		this.relativeName = getRelativePath(file, baseDir);
	}
	
	public static String getRelativePath(File file, File baseDir) {
		String res="";
		
		try {
			if(file!=null && baseDir!=null) {
				String path = file.getParentFile().getPath();
				String base = baseDir.getPath();
				res = path.replace(base, "");
				res = res + "/";
			}
		} catch(Exception ex) {
        	LOG.error("Exception #3: " + ex);
        	ex.printStackTrace();	
		}
		
		return res;
	}
	
	public String getRelativeName() {
		String s = getRelativePath(this.source,this.baseName) + getLocalName();
		if(s.startsWith("/")) s = s.substring(1);
		return s;
	}

	private String getRelativePath(URL source, String baseName) {
		String res="";
		if(source!=null && baseName!=null)
			res = getRelativePath(new File(source.getFile()), new File(baseName));
		return res;
	}
	
	private String getLocalName() {
		String res = "";
		if(source!=null) {
			String fileName = source.getFile();
			String parts[] = fileName.split("/");
			if(parts.length>0)
				res = parts[parts.length-1];
		}
		return res;
	}

	public static void dump() {
		LOG.debug("Filemap:");
		fileMap.keySet().forEach(file -> { LOG.debug("... map " + file + " :: " + fileMap.get(file).baseName);});
	}
	
	public static Set<String> getKeys() {
		return fileMap.keySet();
	}

	public static Schema getSchemaByKey(String key) {
		return fileMap.get(key);
	}

	public static Schema getSchemaByName(String resource) {
		Schema res=null;
		
		LOG.debug("getSchemaByName: resource=" + resource);
				
		try {		
			
			if(res==null) {
				res = getCachedSchemaByRef(resource);
			}
			
			if(res==null) {
				res = getCachedSchemaByRef(resource + ".schema.json");
			}
			
			// System.out.println("getSchemaByName: #1 resource=" + resource + " res=" + res);
			
			if(res==null) {
				res = getCachedSchemaByRef(resource.replace(".schema.json", ""));
			}
			
			System.out.println("getSchemaByName: #2 resource=" + resource + " res=" + res);

			LOG.debug("getSchemaByName: resource=" + resource + " res=" + res.jsonSource);

		} catch(Exception ex) {
			LOG.debug("getSchemaByName: ex=" + ex);	
		}
		
		return res;
	}

	public static List<String> loadLocalSources() {
    	List<String> errors = new LinkedList<>();

		List<String> localSources = Config.getLocalSources();

		Out.debug("loading local schemas: localSource=" + localSources.stream().collect(Collectors.joining(",")) );

		try {
			for(String s : localSources) {
				LOG.info("loading local schemas: localSource=" + s );
				File dir = new File(s);
				if(dir.isDirectory()) {
					LOG.info("loading ... from local directory ... " + dir.getName());
					List<File> files = getSchemaFiles(dir);
					for(File file : files) {
						try {
							Schema schema = new Schema(file, dir);
							List<String> errs = schema.load();
							errors.addAll(errs);
							schema.addLocalSource();
						
							APIModel.addResource(schema.getTitle(), schema.getDefinitions());
							
		        			LOG.debug("loading local file={} errs={} title={} def={}", file.getName(), errs, schema.getTitle(), schema.getDefinitions());
		        			
						} catch(Exception e) {
				        	LOG.error("Exception: " + e);
							errors.add("Error reading json from file=" + file.getName() + " error=" + e.getMessage());							
						}
					}
				}
			}
		} catch(Exception ex) {
        	LOG.error("Exception: " + ex);
			errors.add("Error processing local schema sources error=" + ex.getMessage());
		}
		
		return errors;
	
	}

	private static List<File> getSchemaFiles(File dir) {
    	List<File> res = new LinkedList<>();
	    for (File entry : dir.listFiles()) {
        	String fname = entry.getName();
	        if (entry.isFile() && fname.endsWith("schema.json")) {
	        	res.add(entry);
	        }
	        else if(entry.isDirectory()) {
	        	List<File> subs = getSchemaFiles(entry);
	        	res.addAll(subs);
	        }
	    }
	    return res;
	}

	public static List<Schema> loadAllFromSwagger(JSONObject swagger, String swaggerFile) {
		List<Schema> schemas = new LinkedList<>();
    	JSONObject definitions = swagger.optJSONObject("definitions");
    	if(definitions!=null) {
    		Set<String> keys = definitions.keySet();
    		for(String key : keys) {
    			Schema s = new Schema(key, swagger, swaggerFile);
    			schemas.add(s);
				s.updateFileMap();

    		}
    	}
    	return schemas;
	}
	
	public static List<Schema> loadAllFromAPIv3(JSONObject swagger, String swaggerFile) {
		List<Schema> schemas = new LinkedList<>();
				
    	JSONObject definitions = swagger.optJSONObject("components").optJSONObject("schemas");
    	
    	if(definitions!=null) {
    		Set<String> keys = definitions.keySet();
    		for(String key : keys) {
    			Schema s = new Schema(key, swagger, swaggerFile);
    			schemas.add(s);
				s.updateFileMap(key);
				Schema.relativeFileMap.put(key, s);

    		}
    	}
    	return schemas;
	}


	public static boolean hasSeen(String resource) {
		boolean res = false;
		res = fileMap.containsKey(resource);
		res = res || localSchemas.containsKey(resource);
		
		resource = resource + ".schema.json";
		res = res || fileMap.containsKey(resource);
		res = res || localSchemas.containsKey(resource);
		
		return res;
	}

	public String getBaseSimpleType() {
		LOG.debug("getBaseSimpleType: title=" + getTitle());
		return null;
	}
	
}
