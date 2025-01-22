package no.paneon.api.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import no.paneon.api.utils.Out;

public class APIModelCache {
	
	static final Logger LOG = LogManager.getLogger(APIModelCache.class);

	private Map<String,JSONObject> resourceExpanded = new HashMap<>();

	private Map<String,Set<String>> propertiesForResource = null;

	private Map<String,String> referencedType = new HashMap<>();

	private Set<String> coreResource = null;
	
	public APIModelCache() {
		propertiesForResource = new HashMap<>();
		coreResource = new HashSet<>();
	}
	
	public Set<String> getPropertiesForResource(String resource) {
		Set<String> res = new HashSet<>();
		
		if(propertiesForResource.containsKey(resource)) {
			res = new HashSet<>( propertiesForResource.get(resource) );
		}
				
		if(this.coreResource.contains(resource)) {
//			Out.debug("APIModelCache::getPropertiesForResource resource={} properties={}", resource, res);
			
//			for(String key : propertiesForResource.keySet().stream().sorted().collect(Collectors.toList())) {
//				Out.debug("... APIModelCache::getPropertiesForResource resource={} properties={}", key, propertiesForResource.get(key));
//			}
			
		}
		
		return res;
		
	}
	
	public void reset() {
		
	}

	public void addPropertiesForResource(String resource, Set<String> properties) {

		Set<String> props = properties.stream().collect(Collectors.toSet());

		if(this.coreResource.contains(resource)) {
			LOG.debug("######### APIModelCache::addPropertiesForResource resources={} properties={}", resource, props);
		}
		
		propertiesForResource.put(resource, props );
		
	}

	public boolean hasPropertiesForResource(String resource) {
		boolean res = propertiesForResource.containsKey(resource);
		return res;
	}

	public void setCoreResources(List<String> resources) {
		coreResource.addAll(resources);	
	}

	public boolean hasExpandedResource(String resource) {
		boolean res = resourceExpanded.containsKey(resource);
		return res;
	}

	public JSONObject getExpandedResource(String resource) {
		JSONObject res = new JSONObject();
		
		if(resourceExpanded.containsKey(resource)) {
			res = resourceExpanded.get(resource);
		}
				
		if(this.coreResource.contains(resource)) {
//			Out.debug("APIModelCache::getExpandedResource resource={} res={}", resource, res);	
		}
		
		return res;
	}

	public void addResourceExpanded(String resource, JSONObject value) {
		resourceExpanded.put(resource, value );	
	}

	public boolean hasReferencedType(String type, String property) {
		String key = type + "_" + property;
		boolean res = referencedType.containsKey(key);
		return res;
	}

	public String getReferencedType(String type, String property) {
		String res = "";
		String key = type + "_" + property;

		if(referencedType.containsKey(key)) 
			res = referencedType.get(key);
		
		return res;
	}

	public void addReferencedType(String type, String property, String value) {
		String key = type + "_" + property;
		referencedType.put(key,value);
	}
	
	
}
