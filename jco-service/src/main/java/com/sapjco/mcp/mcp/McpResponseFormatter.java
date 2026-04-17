package com.sapjco.mcp.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;

/**
 * Utility class for formatting MCP tool responses.
 * Ported from TypeScript return_response/return_error utilities.
 */
public class McpResponseFormatter {

    private McpResponseFormatter() {
        // Utility class - no instantiation
    }

    /**
     * Create a successful response with text content.
     *
     * @param text Response text
     * @return CallToolResult with isError=false
     */
    public static CallToolResult success(String text) {
        return new CallToolResult(
            List.of(new TextContent(text)),
            false
        );
    }

    /**
     * Create a successful response with text content and system header.
     *
     * @param systemHeader System header (e.g., "[dev | sap.example.com | 100]")
     * @param text Response text
     * @return CallToolResult with isError=false
     */
    public static CallToolResult success(String systemHeader, String text) {
        String fullText = systemHeader + "\n\n" + text;
        return new CallToolResult(
            List.of(new TextContent(fullText)),
            false
        );
    }

    /**
     * Create an error response.
     *
     * @param errorMessage Error message
     * @return CallToolResult with isError=true
     */
    public static CallToolResult error(String errorMessage) {
        return new CallToolResult(
            List.of(new TextContent("Error: " + errorMessage)),
            true
        );
    }

    /**
     * Create an error response from an exception.
     *
     * @param e Exception
     * @return CallToolResult with isError=true
     */
    public static CallToolResult error(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return new CallToolResult(
            List.of(new TextContent("Error: " + message)),
            true
        );
    }

    /**
     * Create an error response with system header.
     *
     * @param systemHeader System header
     * @param errorMessage Error message
     * @return CallToolResult with isError=true
     */
    public static CallToolResult error(String systemHeader, String errorMessage) {
        String fullText = systemHeader + "\n\nError: " + errorMessage;
        return new CallToolResult(
            List.of(new TextContent(fullText)),
            true
        );
    }

    /**
     * Format a system header string.
     * Format: [systemId | host | client]
     *
     * @param systemId System identifier
     * @param host SAP host
     * @param client SAP client
     * @return Formatted header string
     */
    public static String formatSystemHeader(String systemId, String host, String client) {
        return String.format("[%s | %s | %s]", systemId, host, client);
    }

    /**
     * Extract hostname from URL.
     *
     * @param url Full URL
     * @return Hostname
     */
    public static String extractHostFromUrl(String url) {
        try {
            java.net.URL urlObj = new java.net.URL(url);
            return urlObj.getHost();
        } catch (java.net.MalformedURLException e) {
            return url;
        }
    }
}
