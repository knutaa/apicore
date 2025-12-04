package no.paneon.api.graph;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;

public class Example {

    static final Logger LOG = LogManager.getLogger(Property.class);

	public String example = "";
	
	public static final String EXAMPLE  = "example";
	public static final String EXAMPLES = "examples";

	public static final String NEWLINE = "\n";

	public Example(JSONObject payload) {
		
		if(!payload.isEmpty()) {
			try {
				if(!payload.has(EXAMPLE) && !payload.has(EXAMPLES)) {
					JSONObject o = new JSONObject();
					o.put(EXAMPLE,  payload);
					payload = o;
				}
				String ex =  Utils.convertJsonToYaml(payload);
				ex = ex.strip();
				
				this.example = ex;
				
				LOG.debug("Example: payload={}", this.example);

			} catch(Exception e) {
				Out.debug("ERROR: example - unable to convert to yaml - source={}",  payload);
			}
		}
		
	}
	
	public Example(String payload) {
		if(!payload.isEmpty()) {
			this.example = "example: " + payload.strip();
		}
	}

	public boolean isEmpty() {
		return this.example.isEmpty();
	}
	
}
