package org.geoserver.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.springframework.security.ConfigAttributeDefinition;
import org.springframework.security.SecurityConfig;
import org.springframework.security.intercept.web.FilterInvocation;
import org.springframework.security.intercept.web.FilterInvocationDefinitionSource;
import org.springframework.security.util.StringSplitUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geoserver.security.impl.RESTAccessRuleDAO;
import org.springframework.util.StringUtils;





/**
 * 
 * @author Chris Berry
 * http://opensource.atlassian.com/projects/spring/browse/SEC-531
 *
 */
public class RESTfulDefinitionSource implements FilterInvocationDefinitionSource {
       
    static private Log log = LogFactory.getLog(RESTfulDefinitionSource.class);

    static private final String[] validMethodNames = { "GET", "PUT", "DELETE", "POST" };

    /**
     * Underlying objectDefinitionSource object
     */
    private RESTfulPathBasedFilterInvocationDefinitionMap delegate = null;   
    /**
     * rest access rules dao
     */
    private RESTAccessRuleDAO dao;
    
    /** 
     * Override the method in AbstractFilterInvocationDefinitionSource
     */
    public ConfigAttributeDefinition getAttributes( Object object )
        throws IllegalArgumentException {
        
        if ((object == null) || !this.supports(object.getClass())) {
            throw new IllegalArgumentException("Object must be a FilterInvocation");
        }

        String url = ((FilterInvocation) object).getRequestUrl();
        String method = ((FilterInvocation) object).getHttpRequest().getMethod();

        return delegate().lookupAttributes( cleanURL(url), method );
    }

    /**
     * this form is invalid for this implementation
     */
    public ConfigAttributeDefinition lookupAttributes( String url ) { 
        throw new IllegalArgumentException( "lookupAttributes(String url) is INVALID for RESTfulDefinitionSource" );
    }

    public ConfigAttributeDefinition lookupAttributes( String url, String method ) { 
        return delegate().lookupAttributes(cleanURL(url), method );
    }

    public Collection getConfigAttributeDefinitions() {
        return delegate().getConfigAttributeDefinitions();        
    }

    /**
     * Duplicated from AbstractFilterInvocationDefinitionSource
     */
    public boolean supports(Class clazz) {
        return FilterInvocation.class.isAssignableFrom(clazz);
    }
    
    /**
     * Creates a proxy to a PathBasedFilterInvocationDefinitionMap to delegate calls.
     * Initializations of the delegate is done here.
     * 
     * @deprecated This is not longer used (JD)
     */
    public RESTfulDefinitionSource( String pathToRoleList ) throws IllegalArgumentException {    
        delegate = new RESTfulPathBasedFilterInvocationDefinitionMap();
        processPathList( pathToRoleList );
    }

    public RESTfulDefinitionSource(RESTAccessRuleDAO dao) {
        this.dao = dao;
        
        //force a read of the property file at startup
        dao.reload();
    }
    
    RESTfulPathBasedFilterInvocationDefinitionMap delegate() {
        if (delegate == null || dao.isModified()) {
            synchronized(this) {
                delegate = new RESTfulPathBasedFilterInvocationDefinitionMap();
                for (String rule : dao.getRules()) {
                    processPathList(rule);
                }    
            }
        }
        return delegate;
    }
    
    /**
     * this is completely bogus. I am duplicating code in FilterInvocationDefinitionSourceEditor 
     */
    private void processPathList( String pathToRoleList ) throws IllegalArgumentException {

        /*
        FilterInvocationDefinitionDecorator source = new FilterInvocationDefinitionDecorator();
        source.setDecorated( delegate );
        source.setConvertUrlToLowercaseBeforeComparison( true );
        */
        delegate.setConvertUrlToLowercaseBeforeComparison( true );

        BufferedReader br = new BufferedReader( new StringReader( pathToRoleList ) );
        int counter = 0;
        String line;
        
        List mappings = new ArrayList();
        
        while (true) {
            counter++;           
            try {
                line = br.readLine();
            } catch (IOException ioe) {
                throw new IllegalArgumentException(ioe.getMessage());
            }
            
            if (line == null) {
                break;
            }
            
            line = line.trim();
            
            if (log.isDebugEnabled()) {
                log.debug("Line " + counter + ": " + line);
            }
            
            if (line.startsWith("//")) {
                continue;
            }
            
            // Skip lines that are not directives
            if (line.lastIndexOf('=') == -1) {
                continue;
            }
            
            if (line.lastIndexOf("==") != -1) {
                throw new IllegalArgumentException("Only single equals should be used in line " + line);
            }
            
            // Tokenize the line into its name/value tokens
            // As per SEC-219, use the LAST equals as the delimiter between LHS and RHS
            String name = StringSplitUtils.substringBeforeLast(line, "=");
            String value = StringSplitUtils.substringAfterLast(line, "=");
            
            if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                throw new IllegalArgumentException("Failed to parse a valid name/value pair from " + line);
            }

            String antPath = name;
            String methods = null;

            int firstColonIndex = name.indexOf( ":" );
            if (log.isDebugEnabled()) 
                log.debug( "~~~~~~~~~~ name= " + name + " firstColonIndex= " + firstColonIndex );

            if (firstColonIndex != -1) {
                antPath = name.substring( 0, firstColonIndex );
                methods = name.substring( (firstColonIndex+1), name.length() );
            }
            if (log.isDebugEnabled()) 
                log.debug( "~~~~~~~~~~ name= " + name + " antPath= " + antPath + " methods= " + methods );

            String[] methodList = null; 
            if ( methods != null ) {
                methodList = methods.split( "," );

                // Verify methodList is valid
                for( int ii=0; ii < methodList.length; ii++ ) {
                    boolean matched = false;
                    for ( int jj=0; jj < validMethodNames.length; jj++ ) { 
                        if ( methodList[ii].equals( validMethodNames[jj] ) ) {
                            matched = true;
                            break;
                        }
                    }
                    if ( !matched ) {
                        throw new IllegalArgumentException( "The HTTP Method Name (" + methodList[ii] +
                                                            " does NOT equal a valid name (GET,PUT,POST,DELETE)" );
                    }
                } 
            }
            if (log.isDebugEnabled()) {
                log.debug("methodList = " + methodList );
                if ( methodList != null ) {
                    for( int ii=0; ii <  methodList.length; ii++ ) 
                        log.debug("method[" + ii + "]: " +  methodList[ii] );   
                }
            }

            // Should all be lowercase; check each character
            // We only do this for Ant (regexp have control chars)
            for (int i = 0; i < antPath.length(); i++) {
                String character = antPath.substring(i, i + 1);             
                if (!character.toLowerCase().equals(character)) {
                    throw new IllegalArgumentException("You are using Ant Paths, yet you have specified an uppercase character in line: " 
                                                       + line + " (character '" + character + "')");
                }
            }

            RESTfulDefinitionSourceMapping mapping = new RESTfulDefinitionSourceMapping();
            mapping.setUrl( antPath );
            mapping.setHttpMethods( methodList );
            
            String[] tokens = StringUtils.commaDelimitedListToStringArray(value);

            for (int i = 0; i < tokens.length; i++) {
                mapping.addConfigAttribute( tokens[i].trim() );
            }
            mappings.add(mapping);
        }   

        // This will call the addSecureUrl in RESTfulPathBasedFilterInvocationDefinitionMap
        //   which is how this whole convoluted beast gets wired together
        //source.setMappings(mappings);
        setMappings( mappings );
    }

    public void setMappings( List mappings ) {

        Iterator it = mappings.iterator();
        while (it.hasNext()) {
            RESTfulDefinitionSourceMapping mapping = (RESTfulDefinitionSourceMapping)it.next();
            String[] stringArray = new String[mapping.getConfigAttributes().size()];
            mapping.configAttributes.toArray(stringArray);
            ConfigAttributeDefinition configDefinition = new ConfigAttributeDefinition(stringArray);
            delegate.addSecureUrl(mapping.getUrl(), mapping.getHttpMethods(), configDefinition);
        }

        
//        Iterator it = mappings.iterator();
//        while (it.hasNext()) {
//            RESTfulDefinitionSourceMapping mapping = (RESTfulDefinitionSourceMapping)it.next();
//            ConfigAttributeDefinition configDefinition = new ConfigAttributeDefinition();
//
//            Iterator configAttributesIt = mapping.getConfigAttributes().iterator();
//            while (configAttributesIt.hasNext()) {
//                String s = (String) configAttributesIt.next();
//                configDefinition.addConfigAttribute( new SecurityConfig(s) );
//            }
//            delegate.addSecureUrl(mapping.getUrl(), mapping.getHttpMethods(), configDefinition);
//        }
    }

    /**
     * Hacks an incoming url to help with matching. 
     */
    String cleanURL(String url) {
        //remove any trailing slashes
        url = url.replaceAll("/+$", "");
        return url;
    }
    
    //++++++++++++++++++++++++
    static public class RESTfulDefinitionSourceMapping {        
        private String url = null;      
        private List configAttributes = new ArrayList();
        private String[] httpMethods = null; 

        public void setHttpMethods( String[] httpMethods ) 
        { this.httpMethods = httpMethods; }     
 
        /** 
         * A null httpMethods implies that ALL methods are accepted
         */
        public String[] getHttpMethods() 
        { return httpMethods; }
        
        public void setUrl( String url ) 
        { this.url = url; }     
 
        public String getUrl() 
        { return url; }
        
        public void setConfigAttributes( List roles ) 
        { this.configAttributes = roles; }

        public List getConfigAttributes() 
        { return configAttributes; }

        public void addConfigAttribute( String configAttribute )
        { configAttributes.add(configAttribute); }       
    }


}
