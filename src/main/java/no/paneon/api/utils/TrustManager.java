package no.paneon.api.utils;

import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public final class TrustManager implements X509TrustManager {
    private static ThreadLocal threadLocStorage = new ThreadLocal();
    private java.util.Properties sslConfig = null;
    private java.util.Properties props = null;

    public TrustManager()
    {
    }

    /**
     * Method called by WebSphere Application Server run time to set the target
     * host information and potentially other connection info in the future.
     * This needs to be set on ThreadLocal since the same trust manager can be
     * used by multiple connections.
     * 
     * @param java.util.Map - Contains information about the connection.
     */
    public void setExtendedInfo(java.util.Map info)
    {
        threadLocStorage.set(info);
    }

    /**
     * Method called internally to retrieve information about the connection. 
     * 
     * @return java.util.Map - Contains information about the connection.
     */
    private java.util.Map getExtendedInfo()
    {
        return (java.util.Map) threadLocStorage.get();
    }

    /**
     * Method called by WebSphere Application Server run time to set the custom
     * properties.
     * 
     * @param java.util.Properties - custom props
     */
    public void setCustomProperties(java.util.Properties customProps)
    {
        props = customProps;
    }

    /**
     * Method called internally to the custom properties set in the Trust Manager
     * configuration.
     * 
     * @return java.util.Properties - information set in the configuration.
     */
    private java.util.Properties getCustomProperties()
    {
        return props;
    }

    /**

     * Method called by WebSphere Application Server runtime to set the SSL
     * configuration properties being used for this connection.
     * 
     * @param java.util.Properties - contains a property for the SSL configuration.
     */
    public void setSSLConfig(java.util.Properties config)
    {
        sslConfig = config;    
    }

    /**
     * Method called by TrustManager to get access to the SSL configuration for 
     * this connection.
     * 
     * @return java.util.Properties
     */
    public java.util.Properties getSSLConfig ()
    {
        return sslConfig;
    }

    /**
     * Method called on the server-side for establishing trust with a client.
     * See API documentation for javax.net.ssl.X509TrustManager.
     */
    public void checkClientTrusted(X509Certificate[] chain, String authType) 
        throws java.security.cert.CertificateException
    {
        for (int j=0; j<chain.length; j++)
        {
            System.out.println("Client certificate information:");
            System.out.println(  "Subject DN:"  + chain[j].getSubjectDN());
            System.out.println(  "Issuer DN:"  + chain[j].getIssuerDN());
            System.out.println(  "Serial number:"  + chain[j].getSerialNumber());
            System.out.println("");
        }
    }


    /**
     * Method called on the client-side for establishing trust with a server.
     * See API documentation for javax.net.ssl.X509TrustManager.
     */
    public void checkServerTrusted(X509Certificate[] chain, String authType) 
        throws java.security.cert.CertificateException
    {
        for (int j=0; j<chain.length; j++)
        {
            System.out.println("Server certificate information:");
            System.out.println(  "Subject DN:"  + chain[j].getSubjectDN());
            System.out.println(  "Issuer DN:"  + chain[j].getIssuerDN());
            System.out.println(  "Serial number:"  + chain[j].getSerialNumber());
            System.out.println("");
        }
    }

    /**
     * Return an array of certificate authority certificates which are trusted 
     * for authenticating peers. You can return null here since the IbmX509
     * or IbmPKIX will provide a default set of issuers.
     *
     * See API documentation for javax.net.ssl.X509TrustManager.
     */
    public X509Certificate[] getAcceptedIssuers()
    {
        return null;
    }

}