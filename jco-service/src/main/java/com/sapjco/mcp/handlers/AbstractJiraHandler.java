package com.sapjco.mcp.handlers;

import com.sapjco.mcp.config.JiraConfigLoader;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.service.JiraApiClient;
import com.sapjco.mcp.util.ParameterExtractor;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * Abstract base class for Jira MCP tool handlers.
 * Provides the Jira REST API client, config, and common helper methods.
 */
@Slf4j
public abstract class AbstractJiraHandler implements ToolHandler {

    @Autowired
    protected JiraApiClient jiraClient;

    @Autowired
    protected JiraConfigLoader jiraConfig;

    // ==================== Parameter helpers ====================

    protected CallToolResult validateRequired(Map<String, Object> args, String name) {
        return ParameterExtractor.validateRequiredString(args, name);
    }

    protected String requireString(Map<String, Object> args, String name) {
        return ParameterExtractor.requireString(args, name);
    }

    protected String optionalString(Map<String, Object> args, String name) {
        return ParameterExtractor.optionalString(args, name);
    }

    protected String optionalString(Map<String, Object> args, String name, String defaultValue) {
        return ParameterExtractor.optionalString(args, name, defaultValue);
    }

    protected int optionalInt(Map<String, Object> args, String name, int defaultValue) {
        return ParameterExtractor.optionalInt(args, name, defaultValue);
    }

    // ==================== Response helpers ====================

    protected CallToolResult success(String content) {
        return McpResponseFormatter.success(content);
    }

    protected CallToolResult error(String message) {
        return McpResponseFormatter.error(message);
    }

    protected CallToolResult error(Exception e) {
        return McpResponseFormatter.error(e);
    }

    protected CallToolResult checkConfigured() {
        if (!jiraConfig.isConfigured()) {
            return error("Jira is not configured. Set JIRA_URL and JIRA_TOKEN environment variables.");
        }
        return null;
    }
}
