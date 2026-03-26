/* Copyright Applied Industrial Logic Limited 2003. All rights Reserved */
/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later 
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51 
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package com.ail.coretest;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.security.Principal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

import junit.framework.TestCase;

import com.ail.core.Core;
import com.ail.core.CoreUser;
import com.ail.core.CoreUserImpl;
import com.ail.core.VersionEffectiveDate;
import com.ail.core.XMLString;
import com.ail.core.configure.AbstractConfigurationLoader;
import com.ail.core.configure.Configuration;
import com.ail.core.configure.ConfigurationOwner;
import com.ail.core.configure.ConfigurationResetError;

/**
 * This class should be extended by any test that needs to act like a core user. I
 * implements the CoreUser interface on behalf of test itself - making the test class
 * cleaner. It also handles common tasks like cleaning up test data from the database
 * when tests close.
 * @version $Revision: 1.8 $
 * @state $State: Exp $
 * @date $Date: 2007/06/04 11:56:23 $
 * @source $Source: /home/bob/CVSRepository/projects/core/test.jar/com/ail/coretest/CoreUserTestCase.java,v $
 */
public class CoreUserTestCase extends TestCase implements CoreUser, ConfigurationOwner {
    private AbstractConfigurationLoader loader=null;
    private Core core=null;
    CoreUserImpl coreUser=null;
    private String NAMESPACE="TestNamespace";

    public CoreUserTestCase(String s) {
        super(s);
        Authenticator.setDefault(new TestAuthenticator());
        coreUser=new CoreUserImpl(CoreUserImpl.SelectConsistentConfigurations, NAMESPACE, null);
    }

    public VersionEffectiveDate getVersionEffectiveDate() {
        return coreUser.getVersionEffectiveDate();
    }

    public void setVersionEffectiveDate(VersionEffectiveDate versionEffectiveDate) {
        coreUser.setVersionEffectiveDate(versionEffectiveDate);
    }

    /**
     * Tidy up (delete) the config database records, this means deleting the
     * config records for the core namespace!!
     */
    protected void tidyUpTestData() {
        // load the loader
        if (loader==null) {
            loader=AbstractConfigurationLoader.loadLoader();
        }

        // fetch the loader's properties
        Properties props=loader.getLoaderParams();

        // load the JDBC driver
        try {
            Class.forName(props.getProperty("driver"));
            Connection con=DriverManager.getConnection(props.getProperty("url")+props.getProperty("databaseName"), props);
            Statement st=con.createStatement();
            st.execute("DELETE FROM "+props.getProperty("table")+" WHERE NAMESPACE='"+NAMESPACE+"'");
            st.close();
            con.close();
        }
        catch(Exception e) {
            if (e.toString().indexOf("doesn't exist")!=-1) {
                // this is ok to ignore - the table isn't always there
            }
            else {
                System.err.println("ignored: "+e);
            }
        }
    }

    public Core getCore() {
        return core;
    }

    public void setCore(Core core) {
        this.core = core;
    }

    /**
     * Method demanded by the ConfigurationOwner interface.
     * @param config Configuration to use from now on.
     */
    public void setConfiguration(Configuration config) {
        core.setConfiguration(config);
    }

    /**
     * Method demanded by the ConfigurationOwner interface.
     * @return The current configuration (at versionEffectiveDate).
     */
    public Configuration getConfiguration() {
        return core.getConfiguration();
    }

    /**
     * Method demanded by the ConfigurationOwner interface.
     * @return The configuration namespace we're using
     */
    public String getConfigurationNamespace() {
        return NAMESPACE;
    }

    /**
     * Method demanded by the ConfigurationOwner interface.
     */
    public void resetConfiguration() {
        if (getCore()==null) {
            return;
        }

        // Derive the configuration's name
        String cname=this.getClass().getName();
        String name=cname.substring(cname.lastIndexOf('.')+1);

        try {
            // load the <name>DefaultConfig resource into an XMLString
            URL inputUrl=this.getClass().getResource(name+"DefaultConfig.xml");
            inputUrl.openStream();

            XMLString factoryConfigXML=new XMLString(inputUrl);

            // marshal the config XML into an instance of Configuration
            Configuration factoryConfig=getCore().fromXML(Configuration.class, factoryConfigXML);

            // write details of where we loaded the config from back into the config so that
            // anyone who uses this configuraiton in future can see where it came from.
            factoryConfig.setSource(inputUrl.toExternalForm());
            
            // reset the configuration
            setConfiguration(factoryConfig);
        }
        catch(Exception e) {
        	e.printStackTrace();
            throw new ConfigurationResetError("Failed to reset "+name+" configuration: "+e);
        }
    }

    /**
     * Setup the standard system properties that most test will need.
     */
    public void setupSystemProperties() {
        System.setProperty("java.naming.factory.initial","org.jnp.interfaces.NamingContextFactory");
        System.setProperty("java.naming.provider.url","jnp://localhost:1099");
        System.setProperty("org.xml.sax.parser", "org.apache.xerces.parsers.SAXParser"); 
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
        System.setProperty("java.protocol.handler.pkgs", "com.ail.core.urlhandler");
    }

    /**
     * Get the security principal associated with this instance.
     * @return The associated security principal - if defined, null otherwise.
     */
    public Principal getSecurityPrincipal() {
        return coreUser.getSecurityPrincipal();
    }
}

/**
 * Authenticator to use during test runs.
 */
// NOTE: These are test-only credentials, not for production use
class TestAuthenticator extends Authenticator {
    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication("admin", "admin".toCharArray());
    }
}
