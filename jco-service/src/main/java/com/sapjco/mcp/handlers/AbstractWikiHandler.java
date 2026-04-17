package com.sapjco.mcp.handlers;

import com.sapjco.mcp.config.WikiConfigLoader;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.WikiApiClient;
import com.sapjco.mcp.util.ParameterExtractor;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Abstract base class for Wiki (Confluence) MCP tool handlers.
 *
 * <p>Wiki handlers are stateless REST operations — no JCo or SAP session required.
 * This class provides the Wiki client, file storage, and common helper methods.
 */
@Slf4j
public abstract class AbstractWikiHandler implements ToolHandler {

    @Autowired
    protected WikiApiClient wikiClient;

    @Autowired
    protected WikiConfigLoader wikiConfig;

    @Autowired
    protected FileStorageService fileStorageService;

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

    protected boolean optionalBoolean(Map<String, Object> args, String name, boolean defaultValue) {
        return ParameterExtractor.optionalBoolean(args, name, defaultValue);
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

    /**
     * Format a response for a file written to disk.
     */
    protected CallToolResult fileResponse(Path filePath, long byteSize, String... headerLines) {
        StringBuilder sb = new StringBuilder();
        for (String line : headerLines) {
            sb.append(line).append("\n");
        }
        sb.append(String.format(Locale.ROOT, "Size: %,d bytes\n", byteSize));
        sb.append(String.format("File: %s\n", filePath));
        return success(sb.toString());
    }

    /**
     * Check that the Wiki is configured before proceeding.
     * Returns an error result if token is missing.
     */
    protected CallToolResult checkConfigured() {
        if (!wikiConfig.isConfigured()) {
            return error("SAP_WIKI_TOKEN environment variable is not set. " +
                    "Add it to the sap-adt MCP server env in ~/.claude/settings.json.");
        }
        return null;
    }
}
