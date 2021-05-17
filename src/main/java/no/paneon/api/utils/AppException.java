package no.paneon.api.utils;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

public class AppException extends Exception {

    static final Logger LOG = LogManager.getLogger(AppException.class);

	protected static final long serialVersionUID = 3649840739892120559L;

	public AppException() {
		if(LOG.isDebugEnabled())
			LOG.log(Level.DEBUG, "Exception: {}", this.toString());
	}

}
