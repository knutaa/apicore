package no.paneon.api.core.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.paneon.api.utils.Out;
	
public class App {

    static final Logger LOG = LogManager.getLogger(App.class);
		
	App(String ... argv) {
		     		
	}
	
	public static void main(String ... args) {
		App app = new App(args);
				
		try {			
			app.run();
		} catch(Exception ex) {
			Out.println("error: " + ex.getLocalizedMessage());	
			System.exit(1);			
		}
		
	}


	void run() {
		
		System.out.println("....");
		Out.printAlways("... this is the core API library for oas2puml and oastooling ...");
		System.out.println("....");
		
		LOG.debug("some test");

	}


}
