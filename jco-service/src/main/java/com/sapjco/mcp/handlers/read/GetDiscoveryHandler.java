package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler for GetDiscovery tool.
 * Retrieves ADT endpoints and capabilities from discovery document via RFC proxy.
 */
@Slf4j
@Component
public class GetDiscoveryHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .optionalString("discovery_uri",
                        "Discovery URI (default: /sap/bc/adt/discovery). Use /sap/bc/adt/core/discovery " +
                        "for core ADT services.")
                .optionalString("category_scheme",
                        "Filter by category scheme (e.g., \"http://www.sap.com/adt/categories/cts\")")
                .optionalString("category_term",
                        "Filter by category term (e.g., \"transportchecks\", \"transports\")")
                .optionalString("session_id", "Optional session ID for connection affinity")
                .buildTool(
                        "GetDiscovery",
                        "Get available ADT endpoints and capabilities from SAP system discovery document. " +
                        "Use to find correct URIs for transport, activation, search, and other ADT services."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        String discoveryUri = optionalString(args, "discovery_uri", "/sap/bc/adt/discovery");
        String categoryScheme = optionalString(args, "category_scheme");
        String categoryTerm = optionalString(args, "category_term");

        log.info("GetDiscovery called: {} (scheme: {}, term: {}, session: {})",
                discoveryUri, categoryScheme, categoryTerm,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, discoveryUri, (sessionId, resolved) -> {
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessGetViaRfc(dest, session, discoveryUri, null,
                            "application/atomsvc+xml, application/xml"));

            return success(resolved, response);
        });
    }
}
