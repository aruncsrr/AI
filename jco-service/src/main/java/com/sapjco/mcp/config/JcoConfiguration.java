package com.sapjco.mcp.config;

import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.ext.DestinationDataEventListener;
import com.sap.conn.jco.ext.DestinationDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JCo configuration for SAP connection.
 * Supports multiple SAP systems with dynamic destination creation.
 * Sets up stateful JCo destinations with connection pooling.
 */
@Slf4j
@Configuration
public class JcoConfiguration {

    /**
     * Default destination name for backward compatibility.
     * Used when no system_id is specified in the request.
     */
    public static final String DEFAULT_DESTINATION_NAME = "SAP_MCP_ADT";

    /**
     * Prefix for dynamic destination names.
     * Format: SAP_MCP_ADT_{systemId}
     */
    private static final String DESTINATION_PREFIX = "SAP_MCP_ADT_";

    @Value("${sap.host}")
    private String sapHost;

    @Value("${sap.sysnr}")
    private String sapSysnr;

    @Value("${sap.client}")
    private String sapClient;

    @Value("${sap.username}")
    private String sapUsername;

    @Value("${sap.password}")
    private String sapPassword;

    /**
     * Custom destination data provider that supports multiple systems.
     */
    private MultiSystemDestinationDataProvider destinationProvider;

    @PostConstruct
    public void initializeDestination() {
        log.info("Initializing JCo configuration with multi-system support");

        // Create default system properties from environment (may be empty for SNC-only configs)
        Properties defaultProperties = createConnectionProperties(
            sapHost, sapSysnr, sapClient, sapUsername, sapPassword, null
        );

        // Register custom destination data provider
        destinationProvider = new MultiSystemDestinationDataProvider();

        // Only add default destination if host is configured
        if (sapHost != null && !sapHost.isEmpty()) {
            destinationProvider.addDestination(DEFAULT_DESTINATION_NAME, defaultProperties);
            log.info("Default destination configured for host: {}", sapHost);
        } else {
            log.info("No default SAP host configured - systems will be connected on-demand");
        }

        try {
            com.sap.conn.jco.ext.Environment.registerDestinationDataProvider(destinationProvider);
            log.info("✓ Multi-system destination data provider registered");
        } catch (IllegalStateException e) {
            // Provider already registered (e.g., during tests)
            log.warn("Destination data provider already registered: {}", e.getMessage());
        }

        // NOTE: Connection is validated on-demand when sessions are created,
        // not at startup. This allows the service to start even if systems are temporarily down.
        log.info("✓ JCo configuration initialized (connections validated on-demand)");
    }

    /**
     * Get or create a JCo destination for a specific system.
     *
     * @param systemId System identifier (e.g., "dev", "prod")
     * @param host SAP host
     * @param sysnr System number
     * @param client SAP client
     * @param username SAP username
     * @param password SAP password
     * @return Destination name to use with JCoDestinationManager
     * @throws JCoException if destination cannot be created or tested
     */
    public String getOrCreateDestination(
            String systemId,
            String host,
            String sysnr,
            String client,
            String username,
            String password
    ) throws JCoException {
        // Delegate to full method with null SNC and SAP Router parameters (basic auth)
        return getOrCreateDestination(systemId, host, sysnr, client, username, password, null, null, null, null);
    }

    /**
     * Get or create a JCo destination for a specific system with full auth options.
     * Supports both basic auth (username/password) and SNC/Kerberos authentication.
     *
     * @param systemId System identifier (e.g., "dev", "prod")
     * @param host SAP host
     * @param sysnr System number
     * @param client SAP client
     * @param username SAP username (optional for SNC)
     * @param password SAP password (optional for SNC)
     * @param sncPartnername SNC partner name (e.g., "p:CN=SID, O=Company, C=US")
     * @param sncQop SNC Quality of Protection (1-9, default: 8)
     * @param sncLib Path to SNC library
     * @return Destination name to use with JCoDestinationManager
     * @throws JCoException if destination cannot be created or tested
     */
    public String getOrCreateDestination(
            String systemId,
            String host,
            String sysnr,
            String client,
            String username,
            String password,
            String sncPartnername,
            Integer sncQop,
            String sncLib
    ) throws JCoException {
        // Delegate to full method with null SAP Router parameter
        return getOrCreateDestination(systemId, host, sysnr, client, username, password, sncPartnername, sncQop, sncLib, null);
    }

    /**
     * Get or create a JCo destination for a specific system with full auth options.
     * Supports both basic auth (username/password) and SNC/Kerberos authentication.
     * Supports SAP Router for connections through firewalls.
     *
     * @param systemId System identifier (e.g., "dev", "prod")
     * @param host SAP host
     * @param sysnr System number
     * @param client SAP client
     * @param username SAP username (optional for SNC)
     * @param password SAP password (optional for SNC)
     * @param sncPartnername SNC partner name (e.g., "p:CN=SID, O=Company, C=US")
     * @param sncQop SNC Quality of Protection (1-9, default: 8)
     * @param sncLib Path to SNC library
     * @param saprouter SAP Router string (e.g., "/H/router.example.com/S/3299")
     * @return Destination name to use with JCoDestinationManager
     * @throws JCoException if destination cannot be created or tested
     */
    public String getOrCreateDestination(
            String systemId,
            String host,
            String sysnr,
            String client,
            String username,
            String password,
            String sncPartnername,
            Integer sncQop,
            String sncLib,
            String saprouter
    ) throws JCoException {
        // Use default destination if no system specified
        if (systemId == null || systemId.isEmpty() || "default".equals(systemId)) {
            return DEFAULT_DESTINATION_NAME;
        }

        // Include SNC info in destination name for uniqueness
        String destinationName = DESTINATION_PREFIX + systemId.toUpperCase();
        if (sncPartnername != null && !sncPartnername.isEmpty()) {
            destinationName += "_SNC";
        }

        // Check if destination already exists
        if (destinationProvider.hasDestination(destinationName)) {
            log.debug("Reusing existing destination: {}", destinationName);
            return destinationName;
        }

        // Create new destination
        log.info("Creating new destination for system: {} ({})", systemId, destinationName);

        Properties properties;
        if (sncPartnername != null && !sncPartnername.isEmpty()) {
            // SNC/Kerberos authentication
            properties = createSncConnectionProperties(
                host, sysnr != null ? sysnr : "00", client,
                sncPartnername, sncQop, sncLib, saprouter
            );
            log.info("Using SNC authentication for system: {}", systemId);
        } else {
            // Basic auth
            properties = createConnectionProperties(
                host, sysnr != null ? sysnr : "00", client, username, password, saprouter
            );
        }

        destinationProvider.addDestination(destinationName, properties);

        // Test connection
        JCoDestination destination = JCoDestinationManager.getDestination(destinationName);
        destination.ping();

        if (sncPartnername != null && !sncPartnername.isEmpty()) {
            log.info("✓ JCo SNC connection successful for system {} - Host: {}, Client: {}, SNC Partner: {}{}",
                     systemId, host, client, sncPartnername,
                     saprouter != null ? ", Router: " + saprouter : "");
        } else {
            log.info("✓ JCo connection successful for system {} - Host: {}, Client: {}, User: {}{}",
                     systemId, host, client, username,
                     saprouter != null ? ", Router: " + saprouter : "");
        }

        return destinationName;
    }

    /**
     * Create JCo connection properties for basic authentication.
     */
    private Properties createConnectionProperties(
            String host, String sysnr, String client, String username, String password, String saprouter
    ) {
        Properties properties = new Properties();

        // Connection parameters
        properties.setProperty(DestinationDataProvider.JCO_ASHOST, host);
        properties.setProperty(DestinationDataProvider.JCO_SYSNR, sysnr);
        properties.setProperty(DestinationDataProvider.JCO_CLIENT, client);
        properties.setProperty(DestinationDataProvider.JCO_USER, username);
        properties.setProperty(DestinationDataProvider.JCO_PASSWD, password);

        // SAP Router (for connections through firewalls)
        if (saprouter != null && !saprouter.isEmpty()) {
            properties.setProperty(DestinationDataProvider.JCO_SAPROUTER, saprouter);
            log.info("SAP Router configured: {}", saprouter);
        }

        // CRITICAL: Enable stateful connection awareness
        // This allows JCoContext to manage stateful sessions
        properties.setProperty("jco.client.stateful", "1");

        // Disable repository roundtrip - avoids RFC_METADATA_GET call which requires S_RFC authorization
        // Same setting as Eclipse ADT (jco.destination.repository_roundtrip_optimization=0)
        properties.setProperty("jco.destination.repository_roundtrip_optimization", "0");

        // Connection pool configuration
        properties.setProperty(DestinationDataProvider.JCO_POOL_CAPACITY, "10");
        properties.setProperty(DestinationDataProvider.JCO_PEAK_LIMIT, "20");

        // Language
        properties.setProperty(DestinationDataProvider.JCO_LANG, "EN");

        return properties;
    }

    /**
     * Create JCo connection properties for SNC/Kerberos authentication.
     * SNC (Secure Network Communications) uses Kerberos or SAP Cryptographic Library
     * for authentication without username/password.
     *
     * @param host SAP host
     * @param sysnr System number
     * @param client SAP client
     * @param sncPartnername SNC partner name (e.g., "p:CN=SID, O=Company, C=US")
     * @param sncQop Quality of Protection (1=auth only, 2=integrity, 3=encryption, 8=default, 9=max)
     * @param sncLib Path to SNC library (platform-specific, e.g., "/usr/sap/SNC/lib/libsapcrypto.so")
     * @param saprouter SAP Router string (e.g., "/H/router.example.com/S/3299")
     * @return JCo connection properties configured for SNC
     */
    private Properties createSncConnectionProperties(
            String host, String sysnr, String client,
            String sncPartnername, Integer sncQop, String sncLib, String saprouter
    ) {
        Properties properties = new Properties();

        // Connection parameters (same as basic auth)
        properties.setProperty(DestinationDataProvider.JCO_ASHOST, host);
        properties.setProperty(DestinationDataProvider.JCO_SYSNR, sysnr);
        properties.setProperty(DestinationDataProvider.JCO_CLIENT, client);

        // SAP Router (for connections through firewalls)
        if (saprouter != null && !saprouter.isEmpty()) {
            properties.setProperty(DestinationDataProvider.JCO_SAPROUTER, saprouter);
            log.info("SAP Router configured: {}", saprouter);
        }

        // SNC configuration - NO username/password needed
        properties.setProperty("jco.client.snc_mode", "1");              // Enable SNC
        properties.setProperty("jco.client.snc_partnername", sncPartnername);
        properties.setProperty("jco.client.snc_qop", String.valueOf(sncQop != null ? sncQop : 8));
        properties.setProperty("jco.client.snc_sso", "1");               // Enable SSO from OS credentials

        // SNC library path - configured via SNC_LIB environment variable during setup
        // macOS: /Applications/Secure Login Client.app/Contents/MacOS/lib/libsapcrypto.dylib
        // Windows: C:\Program Files\SAP\FrontEnd\SecureLogin\lib\sapcrypto.dll
        String sncLibPath = System.getenv("SNC_LIB");
        if (sncLibPath == null || sncLibPath.isEmpty()) {
            sncLibPath = sncLib; // Fall back to config file value if provided
        }
        if (sncLibPath != null && !sncLibPath.isEmpty()) {
            properties.setProperty("jco.client.snc_lib", sncLibPath);
            log.debug("Using SNC library: {}", sncLibPath);
        }

        // CRITICAL: Enable stateful connection awareness
        properties.setProperty("jco.client.stateful", "1");

        // Disable repository roundtrip - avoids RFC_METADATA_GET call which requires S_RFC authorization
        // Same setting as Eclipse ADT (jco.destination.repository_roundtrip_optimization=0)
        properties.setProperty("jco.destination.repository_roundtrip_optimization", "0");

        // Connection pool configuration
        properties.setProperty(DestinationDataProvider.JCO_POOL_CAPACITY, "10");
        properties.setProperty(DestinationDataProvider.JCO_PEAK_LIMIT, "20");

        // Language
        properties.setProperty(DestinationDataProvider.JCO_LANG, "EN");

        log.info("Created SNC connection properties - Partner: {}, QoP: {}{}",
                 sncPartnername, sncQop != null ? sncQop : 8,
                 saprouter != null ? ", Router: " + saprouter : "");

        return properties;
    }

    /**
     * Custom implementation of DestinationDataProvider supporting multiple systems.
     * Stores connection properties for multiple destinations in memory.
     */
    private static class MultiSystemDestinationDataProvider implements DestinationDataProvider {

        private final Map<String, Properties> destinations = new ConcurrentHashMap<>();

        /**
         * Add a new destination configuration.
         */
        public void addDestination(String destinationName, Properties properties) {
            destinations.put(destinationName, properties);
            log.info("Added JCo destination: {}", destinationName);
        }

        /**
         * Check if a destination exists.
         */
        public boolean hasDestination(String destinationName) {
            return destinations.containsKey(destinationName);
        }

        @Override
        public Properties getDestinationProperties(String destinationName) {
            return destinations.get(destinationName);
        }

        @Override
        public void setDestinationDataEventListener(DestinationDataEventListener listener) {
            // Not needed for our use case
        }

        @Override
        public boolean supportsEvents() {
            return false;
        }
    }
}
