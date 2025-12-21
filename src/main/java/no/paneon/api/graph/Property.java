package no.paneon.api.graph;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class Property implements Comparable<Property> {

    static final Logger LOG = LogManager.getLogger(Property.class);

	String name;
	String type;
	String cardinality;
	boolean required = false;
	Visibility visibility;
	boolean isNullable = false;
	boolean isEnum = false;
	boolean isDeprected=false;

	String defaultValue = null;
	
	String description = "";
	
	static final String EXAMPLE = "example";
	static final String EXAMPLES = "examples";

	public enum Visibility {
		BASE,
		VISIBLE_INHERITED,
		HIDDEN_INHERITED
	}
	
	public static Visibility BASE = Visibility.BASE;
	public static Visibility VISIBLE_INHERITED = Visibility.VISIBLE_INHERITED;
	public static Visibility HIDDEN_INHERITED = Visibility.HIDDEN_INHERITED;

	public List<Example> examples = new LinkedList<>();
	
	public Property(String name, String type, String cardinality, boolean required, String description, Visibility visibility) {
		this.name = name;
		this.type = type;
		this.cardinality = cardinality;
		this.required = required;
		this.description = description;
		this.visibility = visibility;
		
		this.isEnum = APIModel.isEnumType(type);
		
		LOG.debug("Property: #1 name={} type={}",  name, type);

				
	}

	public Property(Property property) {
		this.name = property.name;
		this.type = property.type;
		this.cardinality = property.cardinality;
		this.required = property.required;
		this.description = property.description;
		this.visibility = property.visibility;
		
		this.isEnum = APIModel.isEnumType(this.type);	
		
		LOG.debug("Property: #2 name={} type={}",  name, type);

	}


	@LogMethod(level=LogLevel.DEBUG)
	public String getName() { 
		return name;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getType() {
		return type;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getCardinality() { 
		return cardinality;
	}

	@LogMethod(level=LogLevel.DEBUG)
	public String getDescription() { 
		return description;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isRequired() {
		return this.required;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Visibility getVisibility() {
		return visibility;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType() {
				
		List<String> simpleEndings = Config.getSimpleEndings();
				
		boolean simpleEnding = simpleEndings.stream().anyMatch(ending -> type.endsWith(ending));
		
		return  simpleEnding 
				|| APIModel.isSpecialSimpleType(type) 
				|| APIModel.isSimpleType(type) 
				|| Config.getSimpleTypes().contains(type) 
				|| APIModel.isEnumType(type);
		
	}
	
	public String toString() {
		return name + " : " + type + " required:" + required + " visibility:" + this.visibility + " (" + this.description + ")";
	}
	
	List<String> values = new LinkedList<>();
	public void addValues(List<String> val) {
		this.values.addAll(val);
	}
	
	public List<String> getValues() {
		return values;
	}

	public boolean isEnum() {
		return this.isEnum;
	}
	
	public void addEnumValues(List<String> values) {
		addValues(values);		
		isEnum=true;
	}

	public void setNullable() {
		this.isNullable=true;
	}
	
	public void setNullable(boolean value) {
		this.isNullable=value;
	}
	
	public boolean isNullable() {
		return this.isNullable;
	}

	public void setDefaultValue(String value) {
		this.defaultValue = value;
	}
	
	public String getDefaultValue() {
		return this.defaultValue;
	}
	
	public void setRequired() {
		LOG.debug("Property::setRequired: property={} current state={}", this.name, this.required);
		this.required=true;
	}
	
	private boolean isVendorExtension=false;
	public void setVendorExtension() {
		LOG.debug("Property::setVendorExtension: property={}", this.name);
		this.isVendorExtension=true;
	}

	public boolean getVendorExtension() {
		return this.isVendorExtension;
	}

	public void setCardinality(String val) {
		this.cardinality=val;
	}
	
	private boolean isRequiredExtension=false;
	public void setRequiredExtension() {
		LOG.debug("Property::setRequiredExtension: property={}", this.name);
		this.isRequiredExtension=true;
	}

	public boolean getRequiredExtension() {
		return this.isRequiredExtension;
	}

	private boolean isTypeExtension=false;
	public void setTypeExtension() {
		LOG.debug("Property::setTypeExtension: property={}", this.name);
		this.isTypeExtension=true;
	}

	public boolean getTypeExtension() {
		return this.isTypeExtension;
	}
 
	private boolean isCardinalityExtension=false;
	public boolean getCardinalityExtension() {
		return this.isCardinalityExtension;
	}
	
	public void setCardinalityExtension() {
		LOG.debug("Property::setCardinalityExtension: property={}", this.name);
		this.isCardinalityExtension=true;
	}
	
	public boolean getDeprected() {
		return isDeprected;
	}

	public void setDeprected(boolean value) {
		this.isDeprected=value;
	}
	
	 public int compareTo(Property o) {
		 return this.name.compareTo(o.name);
	 }

	 public void setExamples(JSONObject examples) {
		 Example ex = new Example(examples);
		 if(!ex.isEmpty())
			 this.examples.add(ex);
		 
	 }
	 
	 public void setExamples(String example) {
		 Example ex = new Example(example);
		 if(!ex.isEmpty())
			 this.examples.add(ex);
	 }
	 
	 public List<Example> getExamples() {
		return examples;
	 }

	 public void setExamplesFromDefinition(JSONObject property) {
		 
		if(property.has(EXAMPLE)) {
			LOG.debug("### setExamplesFromDefinition: property={} example={}" , this.name, property.optString(EXAMPLE));
			JSONObject example = new JSONObject();
			example.put(EXAMPLE, property.get(EXAMPLE));
			this.setExamples(example);
		}
		
		if(property.has(EXAMPLES)) {
			LOG.debug("### addPropertyDetails: property={} examples={}" , this.name, property.optString(EXAMPLES));
			JSONObject example = new JSONObject();
			example.put(EXAMPLES, property.get(EXAMPLES));
			this.setExamples(example);
		
		}		
	 }

	 public void update(Property property) {
		this.description = property.description;
		this.isDeprected = this.isDeprected | property.isDeprected;
	 }
	    	
}
