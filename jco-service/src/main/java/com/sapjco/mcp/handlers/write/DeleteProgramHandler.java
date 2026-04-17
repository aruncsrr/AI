package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.DeleteResponse;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.JcoSessionManager;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler for DeleteProgram tool.
 * Deletes an ABAP program (report) via ADT REST API.
 *
 * Deletion follows the ADT pattern:
 * 1. GET the object to obtain ETag header (optimistic locking)
 * 2. DELETE with If-Match: <ETag> header
 * 3. Transport request via query param ?corrNr={transport} for non-local objects
 *
 * This handler is only enabled when SAP_ADT_DANGEROUS_OPERATIONS=true.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sap.adt.dangerous-operations.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DeleteProgramHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED - write operations only work via JCo)")
                .requiredString("program_name",
                        "Name of the ABAP program to delete")
                .optionalString("transport_number",
                        "Transport request number. Required for non-$TMP objects. " +
                        "Use GetTransportRequests to find available transports, then ALWAYS ask user for " +
                        "confirmation before selecting a transport.")
                .buildTool(
                        "DeleteProgram",
                        "Delete an ABAP program (report). WARNING: This operation is irreversible. " +
                        "The program will be permanently deleted. " +
                        "For non-$TMP objects, a transport request is required."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        String programName = (String) args.get("program_name");
        String transportNumber = (String) args.get("transport_number");

        // Validate required parameters
        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }
        if (programName == null || programName.isEmpty()) {
            return McpResponseFormatter.error("program_name is required");
        }

        log.info("DeleteProgram called: {} (session: {}, transport: {})",
                programName, sessionId, transportNumber != null ? transportNumber : "(none)");

        try {
            // Get session info for system header
            JcoSession session = sessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            // Execute deletion within JCo context
            DeleteResponse deleteResponse = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                return adtClient.deleteObject(
                        destination,
                        programName,
                        "program",
                        transportNumber,
                        sess.getHttpClient(),
                        sess.getCsrfTokenCache(),
                        sess
                );
            });

            // Build response
            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    systemId,
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            StringBuilder result = new StringBuilder();
            result.append(systemHeader).append("\n\n");
            result.append("Program deleted successfully!\n\n");
            result.append("Name: ").append(deleteResponse.getObjectName()).append("\n");
            result.append("Type: ").append(deleteResponse.getObjectType()).append("\n");
            result.append("HTTP Status: ").append(deleteResponse.getHttpStatus()).append("\n");

            return McpResponseFormatter.success(result.toString());

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", sessionId);
            return McpResponseFormatter.error("Session not found: " + sessionId);

        } catch (Exception e) {
            log.error("DeleteProgram failed for {}", programName, e);
            return McpResponseFormatter.error(e);
        }
    }
}
