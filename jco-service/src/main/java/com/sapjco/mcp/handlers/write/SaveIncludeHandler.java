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
 * Handler for SaveInclude tool.
 * Saves ABAP include program source code via atomic lock/save/unlock operation.
 */
@Slf4j
@Component
public class SaveIncludeHandler extends AbstractWriteHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED - write operations only work via JCo)")
                .requiredString("include_name", "Name of the ABAP include program")
                .optionalString("source_code", "Complete ABAP include source code (provide this OR source_file)")
                .optionalString("source_file",
                        "Path to file containing ABAP source code (provide this OR source_code). " +
                        "Use file paths returned by GetInclude for easy edit-save workflows.")
                .optionalString("function_group_name",
                        "Optional: Function group name if this include belongs to a function group. " +
                        "When provided, uses the function group include URI " +
                        "(/sap/bc/adt/functions/groups/{group}/includes/{include}) instead of the standalone " +
                        "include URI (/sap/bc/adt/programs/includes/{include}). Required for saving " +
                        "function group includes (e.g., LZFG_TESTF01, LZFG_TESTTOP).")
                .optionalString("transport_number",
                        "Optional transport request number. Required for non-local objects. " +
                        "Use GetTransportRequests to find available transports, then ALWAYS ask user for " +
                        "confirmation before selecting a transport. NEVER automatically choose a transport " +
                        "without explicit user approval. If not provided, uses auto-detected transport from lock response.")
                .buildTool(
                        "SaveInclude",
                        "Save ABAP include program source code via JCo. REQUIRED: Must provide session_id from CreateSession. " +
                        "JCo is required for proper SAP session state management. Automatically handles atomic " +
                        "lock/save/unlock operations. Provide source via source_code (inline) OR source_file (file path). " +
                        "For function group includes, provide function_group_name to use the correct ADT URI."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "session_id");
        if (validation != null) return validation;
        validation = validateRequired(args, "include_name");
        if (validation != null) return validation;

        String sessionId = requireString(args, "session_id");
        String includeName = requireString(args, "include_name");
        String sourceCode = optionalString(args, "source_code");
        String sourceFile = optionalString(args, "source_file");
        String functionGroupName = optionalString(args, "function_group_name");
        String transportNumber = optionalString(args, "transport_number");

        // Validate source input: exactly one required
        validation = validateExactlyOne(args, "source_code", "source_file");
        if (validation != null) return validation;

        boolean isFunctionGroupInclude = functionGroupName != null && !functionGroupName.isEmpty();
        String displayName = isFunctionGroupInclude ? functionGroupName + "." + includeName : includeName;

        log.info("SaveInclude called: {} (session: {}, transport: {}, source: {}, fg: {})",
                displayName, sessionId, transportNumber != null ? transportNumber : "(auto)",
                hasString(args, "source_file") ? "file:" + sourceFile : "inline",
                isFunctionGroupInclude ? functionGroupName : "(standalone)");

        try {
            // Resolve source code from file if needed
            String effectiveSourceCode = resolveSourceCode(sourceCode, sourceFile);

            // Get session info for system header
            JcoSession session = requireSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            // Execute atomic save operation
            String rawResponse;
            if (isFunctionGroupInclude) {
                // Function group include: lock/save/unlock by URI
                rawResponse = atomicSaveFunctionGroupInclude(sessionId, functionGroupName, includeName,
                        effectiveSourceCode, transportNumber);
            } else {
                // Standalone include: standard path
                rawResponse = atomicSaveObject(sessionId, includeName, "include",
                        effectiveSourceCode, transportNumber);
            }

            return formatSaveResponse(resolved, rawResponse);

        } catch (Exception e) {
            return handleSaveError(displayName, e);
        }
    }
}
