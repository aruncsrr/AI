package com.sapjco.mcp.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Configuration loader for SAP Wiki (Confluence) connection.
 * Reads credentials from environment variables:
 * <ul>
 *   <li>{@code SAP_WIKI_URL} - Confluence base URL (default: https://wiki.one.int.sap/wiki)</li>
 *   <li>{@code SAP_WIKI_TOKEN} - Personal access token for Bearer auth</li>
 * </ul>
 */
@Slf4j
@Getter
@Component
public class WikiConfigLoader {

    private static final String DEFAULT_WIKI_URL = "https://wiki.one.int.sap/wiki";

    private String apiUrl;
    private String apiToken;

    @PostConstruct
    public void init() {
        String urlEnv = System.getenv("SAP_WIKI_URL");
        this.apiUrl = (urlEnv != null && !urlEnv.isBlank()) ? urlEnv : DEFAULT_WIKI_URL;

        this.apiToken = System.getenv("SAP_WIKI_TOKEN");

        if (apiToken == null || apiToken.isBlank()) {
            log.warn("SAP_WIKI_TOKEN not set — Wiki tools will return authentication errors");
        } else {
            log.info("Wiki config loaded: url={}", apiUrl);
        }
    }

    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank();
    }
}
