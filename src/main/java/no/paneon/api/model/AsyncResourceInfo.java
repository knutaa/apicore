package no.paneon.api.model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.utils.Out;

public class AsyncResourceInfo {

	static final Logger LOG = LogManager.getLogger(AsyncResourceInfo.class);

	public String resource;
	
	public Map<String, Operation> operations;

	public AsyncResourceInfo(String resource) {
		this.resource = resource;
		this.operations = new HashMap<>();
	}

//	public void addRequest(String baseId, Map<String,Object> obj, String path) {
//		if(!operations.containsKey(baseId)) operations.put(baseId,  new Operation() );
//		operations.get(baseId).request = obj;
//		
//		String parts[] = path.split("\\]\\[");		
//		operations.get(baseId).request.put("path",parts[1]);
//		
//		operations.get(baseId).request.put("hasDescription", operations.get(baseId).request.get("description"));
//		
//	}

	
//	public class Operation {
//		
//		public String baseOp;
//
//		public Map<String,Object> request;		
//		public Map<String,Object> reply;
//		
//		public List<String> getRequestMessages() {
//			return getMessages(request.get("message"));
//		}
//		
//		public List<String> getReplyMessages() {
//			return getMessages(request.get("message"));
//		}
//		
//		private List<String> getMessages(Object o) {
//			List<String> res = new LinkedList<>();
//			
//			if(o==null) return res;
//			
//			if(o instanceof JSONObject) {
//				JSONObject obj = (JSONObject)o;
//				
//			}
//			
//			return res;
//		}
//	}

	public class Operation {
		
		public String baseOp;

//		public Map<String,Object> request;		
//		public Map<String,Object> reply;
		
		public JSONArray requests;
		public JSONArray responses;
		public String requestChannel;
		public String responseChannel;

		public Operation(String op, String requestChannel, String responseChannel, JSONArray requests, JSONArray responses) {
			this.requests = requests;
			this.responses = responses;
			this.requestChannel = requestChannel;
			this.responseChannel = responseChannel;
			this.baseOp  = op;
		}

//		public List<String> getRequestMessages() {
//			return getMessages(request.get("message"));
//		}
//		
//		public List<String> getReplyMessages() {
//			return getMessages(request.get("message"));
//		}
		
		private List<String> getMessages(Object o) {
			List<String> res = new LinkedList<>();
			
			if(o==null) return res;
			
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject)o;
				
			}
			
			return res;
		}

		public String getTitle() {
			String res = "";
			if(requestChannel!=null) {
				JSONObject channel = APIModel.getByPath(requestChannel);
				
				LOG.debug("getTitle: requestChannel={} channel={}", requestChannel, channel);

				res = channel.optString("title");
			}
			return res;
		}


		public String getDescription() {
			String res = "";
			if(requestChannel!=null) {
				JSONObject channel = APIModel.getByPath(requestChannel);
				res = channel.optString("description");
			}
			return res;
		}

		public String getRequestTopic() {
			return requestChannel.replace("#/channels/", "");
		}

		public String getResponseTopic() {
			return responseChannel.replace("#/channels/", "");
		}
		
		private List<String> getMessages(String channel) {
			List<String> res = new LinkedList<>();
			if(channel!=null) {
				JSONObject channelDetails = APIModel.getByPath(channel);
				JSONObject messages = channelDetails.optJSONObject("messages");
				for(String key : messages.keySet()) {
					res.add(messages.optJSONObject(key).optString("$ref"));
				}
			}
			return res;		
		}
		
		public List<String> getRequestMessages() {
			return getMessages(requestChannel);
		}
		
		public List<String> getResponseMessages() {
			return getMessages(responseChannel);
		}
		
	}
	
	public void addOperation(String op, String requestChannel, String responseChannel, JSONArray requests, JSONArray responses) {
		this.operations.put(op, new Operation(op, requestChannel, responseChannel, requests, responses));
	}
		
}
