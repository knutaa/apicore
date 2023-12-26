package no.paneon.api.model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

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
		
		public List<String> getRequestMessages() {
			return getMessages(request.get("message"));
		}
		
		public List<String> getReplyMessages() {
			return getMessages(request.get("message"));
		}
		
		private List<String> getMessages(Object o) {
			List<String> res = new LinkedList<>();
			
			if(o==null) return res;
			
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject)o;
				
			}
			
			return res;
		}
	}
		
}
