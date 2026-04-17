package com.sapjco.mcp.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads and manages SAP system configuration from .sap-systems.json.
 * Ported from TypeScript systemConfig.ts.
 */
@Slf4j
@Component
public class SystemConfigLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SystemsConfig systemsConfig;
    private Path configPath;

    /**
     * Authentication type for SAP connections.
     */
    public enum AuthType {
        basic,  // Username/password (deprecated)
        x509,   // X.509 client certificate
        snc     // SNC/Kerberos (recommended)
    }

    /**
     * SNC configuration for JCo/RFC connections.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SncConfig {
        private String partnername;  // SNC partner name (e.g., "p:CN=SID, O=Company")
        private Integer qop;         // Quality of protection (1-9, default: 9)
        private String lib;          // Path to SNC library (platform-specific)
    }

    /**
     * Configuration for a single SAP system.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemConfig {
        private String url;          // Full SAP URL with port
        private String client;       // SAP client number
        private String description;  // Human-friendly description
        private String host;         // Override RFC host if different from URL
        private String sysnr;        // System number (default: "00")
        private String saprouter;    // SAP Router string

        // Authentication type (default: 'basic')
        private AuthType authType;

        // Basic Auth credentials (deprecated)
        private String username;
        private String password;

        // X.509 Certificate Auth
        private String certificate;
        private String privateKey;
        private String ca;
        private String passphrase;

        // SNC/Kerberos Auth
        private SncConfig snc;

        public AuthType getAuthType() {
            return authType != null ? authType : AuthType.basic;
        }

        public String getSysnr() {
            return sysnr != null ? sysnr : "00";
        }

        /**
         * Extract host from URL if not explicitly set.
         */
        public String getEffectiveHost() {
            if (host != null && !host.isEmpty()) {
                return host;
            }
            try {
                java.net.URL urlObj = new java.net.URL(url);
                return urlObj.getHost();
            } catch (Exception e) {
                return url;
            }
        }
    }

    /**
     * Configuration file format for .sap-systems.json.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemsConfig {
        private Map<String, SystemConfig> systems = new HashMap<>();
        private String defaultSystem;  // Maps to "default" in JSON

        // Jackson needs this for "default" field name mapping
        public void setDefault(String defaultSystem) {
            this.defaultSystem = defaultSystem;
        }

        public String getDefault() {
            return defaultSystem;
        }
    }

    /**
     * Resolved system with ID and config.
     */
    @Data
    public static class ResolvedSystem {
        private final String systemId;
        private final SystemConfig config;
    }

    /**
     * System info for listing (without credentials).
     */
    @Data
    public static class SystemInfo {
        private final String systemId;
        private final String url;
        private final String client;
        private final String sysnr;
        private final String description;
        private final boolean isDefault;
        private final String host;
        private final AuthType authType;
    }

    @PostConstruct
    public void init() {
        // Check for SAP_SYSTEMS_CONFIG environment variable first
        String configEnv = System.getenv("SAP_SYSTEMS_CONFIG");
        if (configEnv != null && !configEnv.isEmpty()) {
            configPath = Paths.get(configEnv);
            log.info("Using SAP_SYSTEMS_CONFIG: {}", configPath);
        } else {
            // Fall back to current working directory
            String cwd = System.getProperty("user.dir");
            configPath = Paths.get(cwd, ".sap-systems.json");
        }

        loadConfiguration();
    }

    /**
     * Load systems configuration from .sap-systems.json or environment variables.
     */
    public void loadConfiguration() {
        File configFile = configPath.toFile();

        if (configFile.exists()) {
            try {
                systemsConfig = objectMapper.readValue(configFile, SystemsConfig.class);

                // Validate the config
                validateConfig();

                log.info("Loaded multi-system configuration from {} - {} systems, default: {}",
                        configPath, systemsConfig.getSystems().size(), systemsConfig.getDefaultSystem());
            } catch (IOException e) {
                log.error("Failed to parse .sap-systems.json: {}", e.getMessage());
                throw new RuntimeException("Failed to load .sap-systems.json: " + e.getMessage(), e);
            }
        } else {
            log.warn("SAP systems configuration not found at: {}", configPath);
            log.warn("Set SAP_SYSTEMS_CONFIG environment variable to specify the config file path, " +
                     "or create .sap-systems.json in the current working directory");
            log.info("Falling back to environment variables");
            systemsConfig = createFromEnvironment();
        }
    }

    /**
     * Create configuration from environment variables (legacy fallback).
     */
    private SystemsConfig createFromEnvironment() {
        String url = System.getenv("SAP_URL");
        String username = System.getenv("SAP_USERNAME");
        String password = System.getenv("SAP_PASSWORD");
        String client = System.getenv("SAP_CLIENT");

        if (url == null || client == null) {
            log.warn("No SAP configuration found - either create .sap-systems.json or set environment variables");
            SystemsConfig emptyConfig = new SystemsConfig();
            emptyConfig.setDefault("");
            return emptyConfig;
        }

        SystemConfig defaultSystem = new SystemConfig();
        defaultSystem.setUrl(url);
        defaultSystem.setClient(client);
        defaultSystem.setUsername(username);
        defaultSystem.setPassword(password);
        defaultSystem.setAuthType(AuthType.basic);
        defaultSystem.setDescription("Default system (from environment)");

        String host = System.getenv("SAP_HOST");
        if (host == null) {
            try {
                java.net.URL urlObj = new java.net.URL(url);
                host = urlObj.getHost();
            } catch (Exception e) {
                // ignore
            }
        }
        defaultSystem.setHost(host);
        defaultSystem.setSysnr(System.getenv().getOrDefault("SAP_SYSNR", "00"));

        SystemsConfig config = new SystemsConfig();
        config.getSystems().put("default", defaultSystem);
        config.setDefault("default");

        return config;
    }

    /**
     * Validate the loaded configuration.
     */
    private void validateConfig() {
        if (systemsConfig.getSystems() == null || systemsConfig.getSystems().isEmpty()) {
            throw new IllegalStateException("Invalid config: no systems defined");
        }
        if (systemsConfig.getDefaultSystem() == null || systemsConfig.getDefaultSystem().isEmpty()) {
            throw new IllegalStateException("Invalid config: missing 'default' system ID");
        }
        if (!systemsConfig.getSystems().containsKey(systemsConfig.getDefaultSystem())) {
            throw new IllegalStateException(
                    "Invalid config: default system '" + systemsConfig.getDefaultSystem() + "' not found");
        }

        // Validate each system
        for (Map.Entry<String, SystemConfig> entry : systemsConfig.getSystems().entrySet()) {
            validateSystemConfig(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Validate a single system configuration.
     */
    private void validateSystemConfig(String systemId, SystemConfig config) {
        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            throw new IllegalStateException("System '" + systemId + "': missing required field 'url'");
        }
        if (config.getClient() == null || config.getClient().isEmpty()) {
            throw new IllegalStateException("System '" + systemId + "': missing required field 'client'");
        }

        AuthType authType = config.getAuthType();
        switch (authType) {
            case basic:
                if (config.getUsername() == null || config.getUsername().isEmpty()) {
                    throw new IllegalStateException(
                            "System '" + systemId + "': missing 'username' for basic auth");
                }
                if (config.getPassword() == null || config.getPassword().isEmpty()) {
                    throw new IllegalStateException(
                            "System '" + systemId + "': missing 'password' for basic auth");
                }
                break;
            case x509:
                if (config.getCertificate() == null || config.getCertificate().isEmpty()) {
                    throw new IllegalStateException(
                            "System '" + systemId + "': missing 'certificate' for x509 auth");
                }
                if (config.getPrivateKey() == null || config.getPrivateKey().isEmpty()) {
                    throw new IllegalStateException(
                            "System '" + systemId + "': missing 'privateKey' for x509 auth");
                }
                break;
            case snc:
                if (config.getSnc() == null) {
                    throw new IllegalStateException(
                            "System '" + systemId + "': missing 'snc' config for snc auth type");
                }
                if (config.getSnc().getPartnername() == null || config.getSnc().getPartnername().isEmpty()) {
                    throw new IllegalStateException(
                            "System '" + systemId + "': missing 'snc.partnername' for snc auth");
                }
                break;
        }

        // Validate URL format
        try {
            new java.net.URL(config.getUrl());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "System '" + systemId + "': invalid URL format '" + config.getUrl() + "'");
        }
    }

    /**
     * Get a specific system configuration by ID.
     *
     * @param systemId System ID (optional, uses default if not provided)
     * @return Resolved system with ID and config
     */
    public ResolvedSystem getSystem(String systemId) {
        if (systemsConfig == null) {
            throw new IllegalStateException("System configuration not loaded");
        }

        String resolvedId = (systemId != null && !systemId.isEmpty())
                ? systemId
                : systemsConfig.getDefaultSystem();

        SystemConfig config = systemsConfig.getSystems().get(resolvedId);
        if (config == null) {
            String available = String.join(", ", systemsConfig.getSystems().keySet());
            throw new IllegalArgumentException(
                    "System '" + resolvedId + "' not found. Available systems: " + available);
        }

        return new ResolvedSystem(resolvedId, config);
    }

    /**
     * List all configured systems (without credentials).
     */
    public List<SystemInfo> listSystems() {
        if (systemsConfig == null || systemsConfig.getSystems().isEmpty()) {
            return List.of();
        }

        return systemsConfig.getSystems().entrySet().stream()
                .map(entry -> new SystemInfo(
                        entry.getKey(),
                        entry.getValue().getUrl(),
                        entry.getValue().getClient(),
                        entry.getValue().getSysnr(),
                        entry.getValue().getDescription(),
                        entry.getKey().equals(systemsConfig.getDefaultSystem()),
                        entry.getValue().getEffectiveHost(),
                        entry.getValue().getAuthType()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get the default system ID.
     */
    public String getDefaultSystemId() {
        return systemsConfig != null ? systemsConfig.getDefaultSystem() : null;
    }

    /**
     * Check if configuration is loaded.
     */
    public boolean isConfigured() {
        return systemsConfig != null && !systemsConfig.getSystems().isEmpty();
    }

    /**
     * Add a new system to configuration.
     * Only SNC authentication is supported.
     */
    public void addSystem(String systemId, SystemConfig config) {
        if (config.getAuthType() != AuthType.snc) {
            throw new IllegalArgumentException("Only SNC authentication is supported. Basic auth is deprecated.");
        }

        if (systemsConfig.getSystems().containsKey(systemId)) {
            throw new IllegalArgumentException("System '" + systemId + "' already exists");
        }

        validateSystemConfig(systemId, config);
        systemsConfig.getSystems().put(systemId, config);

        // Set as default if first system
        if (systemsConfig.getDefaultSystem() == null || systemsConfig.getDefaultSystem().isEmpty()) {
            systemsConfig.setDefault(systemId);
        }

        saveConfiguration();
        log.info("Added new SNC system: {}", systemId);
    }

    /**
     * Remove a system from configuration.
     */
    public void removeSystem(String systemId) {
        if (!systemsConfig.getSystems().containsKey(systemId)) {
            throw new IllegalArgumentException("System '" + systemId + "' not found");
        }

        int systemCount = systemsConfig.getSystems().size();
        if (systemsConfig.getDefaultSystem().equals(systemId) && systemCount > 1) {
            throw new IllegalArgumentException(
                    "Cannot remove default system '" + systemId + "'. Set a different default first.");
        }

        systemsConfig.getSystems().remove(systemId);

        // Update default if needed
        if (systemsConfig.getSystems().isEmpty()) {
            systemsConfig.setDefault("");
        } else if (!systemsConfig.getSystems().containsKey(systemsConfig.getDefaultSystem())) {
            systemsConfig.setDefault(systemsConfig.getSystems().keySet().iterator().next());
        }

        saveConfiguration();
        log.info("Removed system: {}", systemId);
    }

    /**
     * Update a system's configuration.
     */
    public void updateSystem(String systemId, SystemConfig partialConfig, Boolean setAsDefault) {
        if (!systemsConfig.getSystems().containsKey(systemId)) {
            throw new IllegalArgumentException("System '" + systemId + "' not found");
        }

        SystemConfig existing = systemsConfig.getSystems().get(systemId);

        // Merge configurations
        if (partialConfig.getUrl() != null) existing.setUrl(partialConfig.getUrl());
        if (partialConfig.getClient() != null) existing.setClient(partialConfig.getClient());
        if (partialConfig.getDescription() != null) existing.setDescription(partialConfig.getDescription());
        if (partialConfig.getHost() != null) existing.setHost(partialConfig.getHost());
        if (partialConfig.getSysnr() != null) existing.setSysnr(partialConfig.getSysnr());
        if (partialConfig.getSaprouter() != null) existing.setSaprouter(partialConfig.getSaprouter());
        if (partialConfig.getSnc() != null) {
            if (existing.getSnc() == null) {
                existing.setSnc(partialConfig.getSnc());
            } else {
                if (partialConfig.getSnc().getPartnername() != null) {
                    existing.getSnc().setPartnername(partialConfig.getSnc().getPartnername());
                }
                if (partialConfig.getSnc().getQop() != null) {
                    existing.getSnc().setQop(partialConfig.getSnc().getQop());
                }
                if (partialConfig.getSnc().getLib() != null) {
                    existing.getSnc().setLib(partialConfig.getSnc().getLib());
                }
            }
        }

        // Ensure SNC auth
        if (existing.getAuthType() != AuthType.snc) {
            throw new IllegalArgumentException("Only SNC authentication is supported.");
        }

        validateSystemConfig(systemId, existing);

        if (Boolean.TRUE.equals(setAsDefault)) {
            systemsConfig.setDefault(systemId);
        }

        saveConfiguration();
        log.info("Updated system: {} (setAsDefault: {})", systemId, setAsDefault);
    }

    /**
     * Save configuration to file.
     */
    private void saveConfiguration() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(configPath.toFile(), systemsConfig);
            log.info("Saved configuration to {}", configPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Get config path for display.
     */
    public String getConfigPath() {
        return configPath.toString();
    }
}
