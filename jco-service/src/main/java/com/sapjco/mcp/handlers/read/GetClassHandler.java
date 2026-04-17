package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.util.AdtUrlBuilder;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetClass tool.
 * Retrieves ABAP class source code via RFC proxy.
 */
@Slf4j
@Component
public class GetClassHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("class_name", "Name of the ABAP class (e.g., \"ZCL_MY_CLASS\")")
                .optionalEnum("version", "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"), "active")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "GetClass",
                        "Get ABAP class source code. Returns the main source of the specified class. " +
                        "Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "class_name");
        if (validation != null) return validation;

        String className = requireString(args, "class_name");
        String version = optionalString(args, "version", "active");

        log.info("GetClass called: {} (version: {}, system: {}, session: {})",
                className, version,
                optionalString(args, "system_id") != null ? optionalString(args, "system_id") : "(default)",
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, className, (sessionId, resolved) -> {
            // Build path for class source
            String path = AdtUrlBuilder.buildSourceUrl("class", className);

            // Add version query param if not active
            Map<String, String> queryParams = new HashMap<>();
            if (!"active".equals(version)) {
                queryParams.put("version", version);
            }

            // Execute via RFC
            String sourceCode = executeInContext(sessionId, (dest, session) ->
                    adtClient.getSourceCodeViaRfc(dest, session, path,
                            queryParams.isEmpty() ? null : queryParams, "text/plain"));

            // Write source to file
            String systemFileId = getSystemFileId(resolved);
            Path filePath = fileStorageService.writeSource(systemFileId, "class", className, sourceCode);

            log.info("Wrote class source to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Format response with file metadata
            return formatSourceResponse(resolved, "Class", className, version, filePath, sourceCode);
        });
    }
}
