package no.paneon.api.utils;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.paneon.api.logging.AspectLogger;

public class Out {

	private Out() {
	}
	
	static final Logger LOG = LogManager.getLogger(Out.class);

	public static boolean silentMode = false;

	public static void println(String s) {
		if(!silentMode) LOG.log(AspectLogger.VERBOSE, s);
	}
	
	public static void println() {
		if(!silentMode)  LOG.log(AspectLogger.VERBOSE, "");
	}
	
	public static void printAlways(String s) {
		LOG.log(AspectLogger.ALWAYS, s);
	}
	
	public static void printAlways(String format, Object ...args) {
		LOG.log(AspectLogger.ALWAYS, format, args);
	}

	public static void println(String ... args) {
		if(!silentMode && LOG.isInfoEnabled()) {
			StringBuilder builder = new StringBuilder();
			String delim = "";
			for(String s : args) {
				builder.append(delim + s);
				delim = " ";
			}
			LOG.log(AspectLogger.VERBOSE, builder.toString());
		}
	}
	
	public static void debug(String format, Object ...args) {
		LOG.log(AspectLogger.VERBOSE, format, args);
	}

	static Set<String> printedOnce = new HashSet<>();
	public static void printOnce(String format,  Object ...args) {
		format = format.replace("{}", "%s");		
		String res = String.format(format,args);
		if(!printedOnce.contains(res)) {
			printedOnce.add(res);
			printAlways(res);
		}
	}
}
