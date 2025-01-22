package no.paneon.api.utils;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CustomJSONArraySerializer extends StdSerializer<JSONArray> {
       
	static final Logger LOG = LogManager.getLogger(CustomJSONArraySerializer.class);

	private static final long serialVersionUID = 1L;
	
	private transient JsonSerializer<Object> defaultSerializer;
    
    public CustomJSONArraySerializer(JsonSerializer<Object> defaultSerializer) {
        super(JSONArray.class);
        this.defaultSerializer = defaultSerializer;
        
        // the following is just to have a silly use of the defaultSerializer ... 
        if(LOG.isTraceEnabled()) {
        	LOG.log(Level.TRACE, "defaultSerializer: {}", this.defaultSerializer);
        }
    }
           
	List<String> ordering = new LinkedList<>();

    public CustomJSONArraySerializer(JsonSerializer<Object> serializer, List<String> ordering) {
    	this(serializer);
    	this.ordering = ordering;
    }

	@Override
    public void serialize(JSONArray value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      	
        gen.writeStartArray();
        	
    	Iterator<Object> iter = value.iterator();
    	while(iter.hasNext()) {
    		provider.defaultSerializeValue(iter.next(), gen);
    	}
        
        gen.writeEndArray();
        
    }
}

