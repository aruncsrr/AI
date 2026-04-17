package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.handlers.AbstractWriteHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler for SaveServiceDefinition tool.
 * Saves RAP Service Definition (SRVD) source code via atomic lock/save/unlock operation.
 */
@Slf4j
@Component
public class SaveServiceDefinitionHandler extends AbstractWriteHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED - write operations only work via JCo)")
                .requiredString("srvd_name", "Name of the service definition")
                .optionalString("source_code",
                        "Complete service definition source code (provide this OR source_file)")
                .optionalString("source_file",
                        "Path to file containing source code (provide this OR source_code). " +
                        "Use file paths returned by GetServiceDefinition for easy edit-save workflows.")
                .optionalString("transport_number",
                        "Optional transport request number. Required for non-local objects. " +
                        "Use GetTransportRequests to find available transports, then ALWAYS ask user for " +
                        "confirmation before selecting a transport. NEVER automatically choose a transport " +
                        "without explicit user approval. If not provided, uses auto-detected transport from lock response.")
                .buildTool(
                        "SaveServiceDefinition",
                        "Save RAP Service Definition (SRVD) source code via JCo. REQUIRED: Must provide " +
                        "session_id from CreateSession. Automatically handles atomic lock/save/unlock " +
                        "operations. Provide source via source_code (inline) OR source_file (file path)."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "session_id");
        if (validation != null) return validation;
        validation = validateRequired(args, "srvd_name");
        if (validation != null) return validation;

        String sessionId = requireString(args, "session_id");
        String srvdName = requireString(args, "srvd_name");
        String sourceCode = optionalString(args, "source_code");
        String sourceFile = optionalString(args, "source_file");
        String transportNumber = optionalString(args, "transport_number");

        // Validate source input: exactly one required
        validation = validateExactlyOne(args, "source_code", "source_file");
        if (validation != null) return validation;

        log.info("SaveServiceDefinition called: {} (session: {}, transport: {}, source: {})",
                srvdName, sessionId, transportNumber != null ? transportNumber : "(auto)",
                hasString(args, "source_file") ? "file:" + sourceFile : "inline");

        try {
            // Resolve source code from file if needed
            String effectiveSourceCode = resolveSourceCode(sourceCode, sourceFile);

            // Get session info for system header
            JcoSession session = requireSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            // Execute atomic save operation
            String rawResponse = atomicSaveObject(sessionId, srvdName, "SERVICE_DEFINITION",
                    effectiveSourceCode, transportNumber);

            return formatSaveResponse(resolved, rawResponse);

        } catch (Exception e) {
            return handleSaveError(srvdName, e);
        }
    }
}
