package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
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
 * Handler for GetFunctionGroup tool.
 * Retrieves ABAP function group source code via RFC proxy.
 */
@Slf4j
@Component
public class GetFunctionGroupHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("group_name", "Name of the ABAP function group (e.g., \"ZTEST_FG\")")
                .optionalEnum("version", "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"), "active")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "GetFunctionGroup",
                        "Get ABAP function group source code. Returns the main source of the specified " +
                        "function group. Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "group_name");
        if (validation != null) return validation;

        String groupName = requireString(args, "group_name");
        String version = optionalString(args, "version", "active");

        log.info("GetFunctionGroup called: {} (version: {}, system: {}, session: {})",
                groupName, version,
                optionalString(args, "system_id") != null ? optionalString(args, "system_id") : "(default)",
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, groupName, (sessionId, resolved) -> {
            // Build path for function group source
            String path = AdtUrlBuilder.buildSourceUrl("function_group", groupName);

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
            Path filePath = fileStorageService.writeSource(systemFileId, "function_group", groupName, sourceCode);

            log.info("Wrote function group source to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Format response with file metadata
            return formatSourceResponse(resolved, "Function Group", groupName, version, filePath, sourceCode);
        });
    }
}
