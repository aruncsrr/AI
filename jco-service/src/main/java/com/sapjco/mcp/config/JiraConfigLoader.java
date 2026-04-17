package com.sapjco.mcp.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Configuration loader for Jira Server/Data Center connection.
 * Reads credentials from environment variables:
 * <ul>
 *   <li>{@code JIRA_URL} - Jira base URL (e.g. https://jira.tools.sap)</li>
 *   <li>{@code JIRA_TOKEN} - Personal access token for Bearer auth</li>
 * </ul>
 */
@Slf4j
@Getter
@Component
public class JiraConfigLoader {

    private String apiUrl;
    private String apiToken;

    @PostConstruct
    public void init() {
        String urlEnv = System.getenv("JIRA_URL");
        this.apiUrl = (urlEnv != null && !urlEnv.isBlank())
                ? urlEnv.replaceAll("/+$", "")
                : null;

        this.apiToken = System.getenv("JIRA_TOKEN");

        if (apiToken == null || apiToken.isBlank()) {
            log.warn("JIRA_TOKEN not set — Jira tools will return authentication errors");
        } else {
            log.info("Jira config loaded: url={}", apiUrl);
        }
    }

    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank() && apiUrl != null && !apiUrl.isBlank();
    }
}
