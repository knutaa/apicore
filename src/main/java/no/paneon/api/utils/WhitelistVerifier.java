package no.paneon.api.utils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class WhitelistVerifier implements HostnameVerifier {
	
    static final Logger LOG = LogManager.getLogger(WhitelistVerifier.class);
	
    private Set<String> whitelist = new HashSet<>();
    private HostnameVerifier defaultHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();

    
    public Set<String> getValues() {
        return whitelist;
    }

    public void setValues(Collection<String> values) {
    	LOG.debug("WhitelistVerifier::setValues values={}", values);
        whitelist.addAll(values);
    }
    
    // @Override
	public boolean verify(String host, SSLSession session) {
    	Out.debug("WhitelistVerifier::verify host={}", host);
        if (whitelist.contains(host)) {
            return true;
        }
        return true || defaultHostnameVerifier.verify(host, session);
    }
}