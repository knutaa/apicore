package no.paneon.api.model;

import java.util.HashMap;
import java.util.Map;

public class AsyncResourceInfo {

	public String resource;
	
	public Map<String, Operation> operations;

	public AsyncResourceInfo(String resource) {
		this.resource = resource;
		this.operations = new HashMap<>();
	}

	public void addRequest(String baseId, Map<String,Object> obj, String path) {
		if(!operations.containsKey(baseId)) operations.put(baseId,  new Operation() );
		operations.get(baseId).request = obj;
		
		String parts[] = path.split("\\]\\[");		
		operations.get(baseId).request.put("path",parts[1]);
		
		operations.get(baseId).request.put("hasDescription", operations.get(baseId).request.get("description"));
		
	}

	public void addReply(String baseId, Map<String,Object> obj, String path) {
		if(!operations.containsKey(baseId)) operations.put(baseId,  new Operation() );
		operations.get(baseId).reply = obj;	
		
		String parts[] = path.split("\\]\\[");
		operations.get(baseId).reply.put("path",parts[1]);

		operations.get(baseId).reply.put("hasDescription", operations.get(baseId).reply.get("description"));

	}
	
	public class Operation {
		
		public String baseOp;

		public Map<String,Object> request;		
		public Map<String,Object> reply;
		
	}
		
}
