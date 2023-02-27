package no.paneon.api;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import no.paneon.api.graph.CoreAPIGraph;
import no.paneon.api.graph.Node;
import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.utils.Utils;

public class ExternalServerTest {

    static final Logger LOG = LogManager.getLogger(ExternalServerTest.class);

	static HttpTester tester;
	static Server server;
	
	static String RESOURCES = "src/test/resources/";

	@BeforeClass
	static public void setUp() throws Exception {
		
		Config.init();
		
	    server = new Server(8080);
	    ServletContextHandler context = new ServletContextHandler();
	    ServletHolder defaultServ = new ServletHolder("default", DefaultServlet.class);
	    
	    LOG.debug("user.dir={}", System.getProperty("user.dir"));
	    
	    defaultServ.setInitParameter("resourceBase",System.getProperty("user.dir") + "/" + RESOURCES);
	    defaultServ.setInitParameter("dirAllowed","true");
	    
	    context.addServlet(defaultServ,"/");
	    server.setHandler(context);

	    server.start();
			 
	}
	
	@AfterClass
	static public void tearDown() throws Exception {
	    server.stop();
	}
	
	@Test
	public void testDefaultServlet() throws Exception {
		String file = "TMF620-ProductCatalog-v4.1.0.swagger.json";
		
		URL url = new URL("http://localhost:8080/" + file);

		HttpURLConnection http = (HttpURLConnection) url.openConnection();
		http.connect();

		// Out.debug("response={}", http.getResponseCode());

		assert(http.getResponseCode()==HttpStatus.OK_200);

	}
	
	@Test
	public void testAPI1() throws Exception {
		String source = "http://localhost:8080/TMF620-ProductCatalog-v4.1.0.swagger.json";
		
		List<String> dirs = new LinkedList<>();
		
		try {
			InputStream is = Utils.getSource(source, dirs);
			
			assert(is!=null);
			
			APIModel.loadAPI(source, is);		
			List<String> resources = APIModel.getAllDefinitions();
			
			assert(!resources.isEmpty());
			
		} catch(Exception ex) {
			assert(false);
		}		

	}
	
	@Test
	public void testAPI2() throws Exception {
		String source = "https://forge.3gpp.org/rep/all/5G_APIs/-/raw/REL-18/TS24558_Eecs_ServiceProvisioning.yaml";
		
		// https://forge.3gpp.org/rep/all/5G_APIs/-/raw/REL-18/TS24558_Eecs_ServiceProvisioning.yaml 
			
		List<String> dirs = new LinkedList<>();
		
		try {
			InputStream is = Utils.getSource(source, dirs);
			
			assert(is!=null);
			
			APIModel.loadAPI(source, is);		
			List<String> resources = APIModel.getAllDefinitions();
			
			resources = APIModel.getAllOperations();
			Out.debug("resources={}", resources);

			assert(!resources.isEmpty());
			
		} catch(Exception ex) {
			assert(false);
		}		

	}

	@Test
	public void testAPI3() throws Exception {
		String source = "http://localhost:8080/ordering-api.swagger.json";
					
		List<String> dirs = new LinkedList<>();
		
		try {
			InputStream is = Utils.getSource(source, dirs);
			
			assert(is!=null);
			
			APIModel.loadAPI(source, is);		
			
			JSONObject resource = APIModel.getDefinition("ProductOrder");
			
			assert(resource!=null);
			
			Out.debug("productOrder keys={}", resource.keySet());
			Out.debug("productOrder={}", resource);

			Set<String> properties = APIModel.getProperties(resource);
			
			Out.debug("properties={}", properties);

			assert(properties.contains("expectedCompletionDate"));

			CoreAPIGraph graph = new CoreAPIGraph(APIModel.getCoreResources());
			
			Out.debug("graph nodes={}", graph.getCompleteGraph().vertexSet());

			Node node = graph.getNode("ProductOrder");
			assert(node!=null);

			
		} catch(Exception ex) {
			assert(false);
		}		

	}
	
	@Test
	public void testAPI4() throws Exception {
		String source = RESOURCES + "ordering-api.swagger.json";
					
		List<String> dirs = new LinkedList<>();
				
		try {
			InputStream is = Utils.getSource(source, dirs);
			
			assert(is!=null);
			
			APIModel.loadAPI(source, is);		
			
			List<String> resources = APIModel.getAllDefinitions();
			
			resources = APIModel.getAllDefinitions();
			Out.debug("resources={}", resources);

			assert(!resources.isEmpty());
			
		} catch(Exception ex) {
			assert(false);
		}		

	}
	
	
	
}

		
