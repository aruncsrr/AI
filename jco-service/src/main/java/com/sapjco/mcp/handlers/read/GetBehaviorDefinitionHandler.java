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
 * Handler for GetBehaviorDefinition tool.
 * Retrieves RAP Behavior Definition (BDEF) source code via RFC proxy.
 * Writes source to file and returns metadata summary.
 */
@Slf4j
@Component
public class GetBehaviorDefinitionHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("bdef_name",
                        "Name of the behavior definition (e.g., \"R_TRAVELPROCTP\", \"R_BOOKINGTP\")")
                .optionalEnum("version", "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"), "active")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetBehaviorDefinition",
                        "Get RAP Behavior Definition (BDEF) source code. Returns the plain text behavior " +
                        "definition source. Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "bdef_name");
        if (validation != null) return validation;

        String bdefName = requireString(args, "bdef_name");
        String version = optionalString(args, "version", "active");

        log.info("GetBehaviorDefinition called: {} (version: {}, session: {})",
                bdefName, version,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, bdefName, (sessionId, resolved) -> {
            // Build path for behavior definition source
            String path = AdtUrlBuilder.buildSourceUrl("behavior_definition", bdefName);

            // Always pass version explicitly - BDEF endpoint defaults to inactive
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("version", version);

            // Execute via RFC
            String sourceCode = executeInContext(sessionId, (dest, session) ->
                    adtClient.getSourceCodeViaRfc(dest, session, path,
                            queryParams, "text/plain"));

            // Write source to file
            String systemFileId = getSystemFileId(resolved);
            Path filePath = fileStorageService.writeSource(systemFileId,
                    FileStorageService.CAT_BEHAVIOR_DEFINITION, bdefName, sourceCode);

            log.info("Wrote behavior definition source to file: {} ({} bytes)",
                    filePath, fileStorageService.getByteSize(filePath));

            // Format response with file metadata
            return formatSourceResponse(resolved, "Behavior Definition", bdefName, version, filePath, sourceCode);
        });
    }
}
