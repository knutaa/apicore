package no.paneon.api.graph;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;

public class EnumNode extends Node {
	
    static final Logger LOG = LogManager.getLogger(EnumNode.class);

	String node;
	String type;
	
	Node placedByNode;
	Place placedInDirection;
	
	List<String> values;
	
	final static String ANYOF = "anyOf";

	public EnumNode(String type) {
		super(type);
		
		this.type=type;
		this.values = new LinkedList<>();
		
		processEnum();
	}
	
	@LogMethod(level=LogLevel.TRACE)
	void setPlacement(Node node, Place direction) {
		this.placedByNode = node;
		this.placedInDirection = direction;
	}
			
	@LogMethod(level=LogLevel.TRACE)
	public void addValue(String value) {
		this.values.add(value);
	}

	@LogMethod(level=LogLevel.TRACE)
	public void addValues(List<String> enumValues) {
		enumValues.forEach(this::addValue);
	}

	@LogMethod(level=LogLevel.TRACE)
	public void setFloatingPlacement() {
		// TODO Auto-generated method stub
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getType() {
		return type;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public List<String> getValues() {
		return this.values;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void processEnum() {
		
	    if(!APIModel.isEnumType(type)) return;
	    	  
	    JSONObject definition = APIModel.getDefinition(type);

	    List<Object> elements = Config.getListAsObject(definition, ENUM);
	    	    	    
		LOG.debug("processEnum:: elements={}", elements);
		LOG.debug("processEnum:: definition={}", definition);

	    if(definition.has(ANYOF)) {
			JSONArray anyofs = definition.optJSONArray(ANYOF);
			
			LOG.debug("processEnum:: anyofs={}", anyofs);

			if(anyofs!=null) {
				for(int i=0; i<anyofs.length(); i++) {
					JSONObject item = anyofs.getJSONObject(i);
					List<Object> candidates = Config.getListAsObject(item, ENUM);
					
					LOG.debug("processEnum:: candidates={}", candidates);

					elements.addAll(candidates);
				}
			}
	    }
	    
	    elements.stream().filter(Objects::nonNull).map(Object::toString).forEach(this::addValue);
	    
	    boolean candidateNullable =  elements.stream().anyMatch(Objects::isNull);
	    boolean nullable = definition.optBoolean("nullable");
	    
	    this.setNullable(candidateNullable && nullable);
	    	        
	}
	
	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType() {
		return false;
	}
	
	boolean nullable = false;
	@LogMethod(level=LogLevel.DEBUG)
	public boolean getNullable() {
		return this.nullable;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void setNullable(boolean v) {
		this.nullable = v;
	}
	
}
