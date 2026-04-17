package com.sapjco.mcp.model;

import com.sapjco.mcp.config.SystemConfigLoader;
import lombok.Data;

/**
 * Request model for creating a JCo session with optional system configuration.
 *
 * For multi-system support, the request can include system-specific credentials.
 * Supports basic auth (username/password) and SNC/Kerberos authentication.
 * If no system config is provided, falls back to default environment variables.
 */
@Data
public class CreateSessionRequest {
    /**
     * System identifier (e.g., "dev", "prod")
     */
    private String systemId;

    /**
     * SAP host for JCo connection
     */
    private String host;

    /**
     * System number (default: "00")
     */
    private String sysnr;

    /**
     * SAP client number
     */
    private String client;

    /**
     * Authentication type: "basic", "x509", or "snc"
     */
    private String authType;

    /**
     * SAP username (for basic auth)
     */
    private String username;

    /**
     * SAP password (for basic auth)
     */
    private String password;

    /**
     * ADT URL for HTTP requests
     */
    private String url;

    /**
     * SAP Router string for RFC connections (e.g., "/H/router.example.com/S/3299")
     */
    private String saprouter;

    // SNC (Secure Network Communications) configuration
    /**
     * SNC configuration object
     */
    private SncConfig snc;

    /**
     * Enable HTTP SSO using OS keystore certificates.
     * When true, HTTP requests will use X.509 client certificates from the OS keystore
     * (macOS Keychain or Windows Certificate Store) instead of Basic Auth.
     * This allows true SSO without passwords in configuration files.
     */
    private boolean enableHttpSso = false;

    /**
     * Nested SNC configuration
     */
    @Data
    public static class SncConfig {
        /**
         * SNC partner name (e.g., "p:CN=SID, O=Company, C=US")
         */
        private String partnername;

        /**
         * Quality of Protection (1-9, default: 8)
         */
        private Integer qop;

        /**
         * Path to SNC library
         */
        private String lib;
    }

    /**
     * Check if this request contains a custom system configuration.
     * Supports both basic auth (username/password) and SNC auth.
     * @return true if system config is provided
     */
    public boolean hasSystemConfig() {
        // Must have basic system identifiers
        if (systemId == null || systemId.isEmpty()
            || host == null || host.isEmpty()
            || client == null || client.isEmpty()) {
            return false;
        }

        // Check for valid authentication: either basic auth or SNC
        boolean hasBasicAuth = username != null && !username.isEmpty()
            && password != null && !password.isEmpty();

        boolean hasSncAuth = snc != null
            && snc.getPartnername() != null && !snc.getPartnername().isEmpty();

        return hasBasicAuth || hasSncAuth;
    }

    /**
     * Check if SNC authentication is configured.
     */
    public boolean hasSncConfig() {
        return snc != null && snc.getPartnername() != null && !snc.getPartnername().isEmpty();
    }

    /**
     * Create a CreateSessionRequest from a ResolvedSystem.
     * This is a convenience method for handlers to create sessions from system config.
     *
     * @param resolved The resolved system configuration
     * @return A fully configured CreateSessionRequest
     */
    public static CreateSessionRequest fromResolvedSystem(SystemConfigLoader.ResolvedSystem resolved) {
        CreateSessionRequest request = new CreateSessionRequest();
        SystemConfigLoader.SystemConfig config = resolved.getConfig();

        request.setSystemId(resolved.getSystemId());
        request.setHost(config.getEffectiveHost());
        request.setSysnr(config.getSysnr());
        request.setClient(config.getClient());
        request.setUrl(config.getUrl());
        request.setSaprouter(config.getSaprouter());

        // Set auth type
        if (config.getAuthType() != null) {
            request.setAuthType(config.getAuthType().name());
        }

        // Copy credentials for basic auth
        request.setUsername(config.getUsername());
        request.setPassword(config.getPassword());

        // Copy SNC config if present
        if (config.getSnc() != null) {
            SncConfig snc = new SncConfig();
            snc.setPartnername(config.getSnc().getPartnername());
            snc.setQop(config.getSnc().getQop());
            snc.setLib(config.getSnc().getLib());
            request.setSnc(snc);

            // SNC systems should use RFC proxy for HTTP
            request.setEnableHttpSso(true);
        }

        return request;
    }
}
