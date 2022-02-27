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

public class Property {

    static final Logger LOG = LogManager.getLogger(Property.class);

	String name;
	String type;
	String cardinality;
	boolean required = false;
	Visibility visibility;
	boolean isNullable = false;
	boolean isEnum = false;

	String defaultValue = null;
	
	String description = "";
	
	public enum Visibility {
		BASE,
		VISIBLE_INHERITED,
		HIDDEN_INHERITED
	}
	
	public static Visibility BASE = Visibility.BASE;
	public static Visibility VISIBLE_INHERITED = Visibility.VISIBLE_INHERITED;
	public static Visibility HIDDEN_INHERITED = Visibility.HIDDEN_INHERITED;

	public Property(String name, String type, String cardinality, boolean required, String description, Visibility visibility) {
		this.name = name;
		this.type = type;
		this.cardinality = cardinality;
		this.required = required;
		this.description = description;
		this.visibility = visibility;
		
		this.isEnum = APIModel.isEnumType(type);
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
		return name + " : " + type + " required:" + required;
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
	
}
