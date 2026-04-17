package com.sapjco.mcp.mcp;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Interface for MCP tool handlers.
 * Each handler implements the logic for a single MCP tool.
 */
public interface ToolHandler {

    /**
     * Get the tool definition with name, description, and input schema.
     *
     * @return Tool definition
     */
    Tool getToolDefinition();

    /**
     * Handle a tool call.
     *
     * @param exchange MCP exchange for client interactions
     * @param request Tool call request with arguments
     * @return Tool call result
     */
    CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request);
}
