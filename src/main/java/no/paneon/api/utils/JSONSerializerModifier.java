package no.paneon.api.utils;

import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
    
public class JSONSerializerModifier extends BeanSerializerModifier {
    	
	List<String> ordering = new LinkedList<>();
	
	public JSONSerializerModifier(List<String> ordering) {
		super();
		this.ordering = ordering;
	}
	
    @SuppressWarnings("unchecked")
	@Override
    public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDesc, JsonSerializer<?> serializer) {
 
        if (beanDesc.getBeanClass().equals(JSONObject.class)) {
            return new CustomJSONObjectSerializer((JsonSerializer<Object>) serializer, this.ordering);
        }
 
        if (beanDesc.getBeanClass().equals(JSONArray.class)) {
            return new CustomJSONArraySerializer((JsonSerializer<Object>) serializer, this.ordering);
        }
        
        return serializer;
    }

}

