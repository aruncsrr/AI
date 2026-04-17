package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.handlers.AbstractWriteHandler;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handler for SaveClassInclude tool.
 * Saves ABAP class include source code via atomic lock/save/unlock operation.
 */
@Slf4j
@Component
public class SaveClassIncludeHandler extends AbstractWriteHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED - write operations only work via JCo)")
                .requiredString("class_name", "Name of the ABAP class")
                .requiredEnum("include_type",
                        "Type of include to save: definitions (LOCAL class definitions), implementations (LOCAL class implementations), " +
                        "macros (macros), testClasses (unit tests). " +
                        "WARNING: These are LOCAL/PRIVATE includes, NOT the main class source. " +
                        "To save the main class source (public definitions + method implementations), use SaveClass instead.",
                        List.of("definitions", "implementations", "macros", "testClasses"))
                .optionalString("source_code", "Complete ABAP include source code (provide this OR source_file)")
                .optionalString("source_file",
                        "Path to file containing ABAP source code (provide this OR source_code). " +
                        "Use file paths returned by GetClassInclude for easy edit-save workflows.")
                .optionalString("transport_number",
                        "Optional transport request number. Required for non-local objects (standard SAP classes). " +
                        "Use GetTransportRequests to find available transports, then ALWAYS ask user for " +
                        "confirmation before selecting a transport. NEVER automatically choose a transport " +
                        "without explicit user approval. If not provided, uses auto-detected transport from lock response.")
                .buildTool(
                        "SaveClassInclude",
                        "Save ABAP class LOCAL include source code via JCo. REQUIRED: Must provide session_id from CreateSession. " +
                        "This saves LOCAL/PRIVATE class includes (CCDEF, CCIMP, CCMAC, CCAU), NOT the main class source. " +
                        "To save the main class source, use SaveClass instead. " +
                        "Automatically handles atomic lock/save/unlock operations. Provide source via source_code (inline) OR source_file (file path)."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "session_id");
        if (validation != null) return validation;
        validation = validateRequired(args, "class_name");
        if (validation != null) return validation;
        validation = validateRequired(args, "include_type");
        if (validation != null) return validation;

        String sessionId = requireString(args, "session_id");
        String className = requireString(args, "class_name");
        String includeType = requireString(args, "include_type");
        String sourceCode = optionalString(args, "source_code");
        String sourceFile = optionalString(args, "source_file");
        String transportNumber = optionalString(args, "transport_number");

        // Validate include type
        List<String> validTypes = List.of("definitions", "implementations", "macros", "testClasses");
        if (!validTypes.contains(includeType)) {
            return McpResponseFormatter.error(
                    "include_type must be one of: " + String.join(", ", validTypes) + ". Received: " + includeType);
        }

        // Validate source input: exactly one required
        validation = validateExactlyOne(args, "source_code", "source_file");
        if (validation != null) return validation;

        log.info("SaveClassInclude called: {} / {} (session: {}, transport: {}, source: {})",
                className, includeType, sessionId, transportNumber != null ? transportNumber : "(auto)",
                hasString(args, "source_file") ? "file:" + sourceFile : "inline");

        try {
            // Resolve source code from file if needed
            String effectiveSourceCode = resolveSourceCode(sourceCode, sourceFile);

            // Get session info for system header
            JcoSession session = requireSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            // Execute atomic save operation
            String rawResponse = atomicSaveClassInclude(sessionId, className, includeType,
                    effectiveSourceCode, transportNumber);

            return formatSaveResponse(resolved, rawResponse);

        } catch (Exception e) {
            log.error("SaveClassInclude failed for {} / {}", className, includeType, e);
            return handleSaveError(className, e);
        }
    }
}
