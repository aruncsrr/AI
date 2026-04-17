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
 * Handler for GetCDSView tool.
 * Retrieves CDS view (DDL source) source code via RFC proxy.
 * Writes DDL source to file and returns metadata summary.
 */
@Slf4j
@Component
public class GetCDSViewHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("cds_view_name", "Name of the CDS view / DDL source (e.g., \"I_FLIGHT\", \"I_CUSTOMER\")")
                .optionalEnum("version", "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"), "active")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetCDSView",
                        "Get CDS view (DDL source) source code. Returns the plain text CDS definition. " +
                        "Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "cds_view_name");
        if (validation != null) return validation;

        String cdsViewName = requireString(args, "cds_view_name");
        String version = optionalString(args, "version", "active");

        log.info("GetCDSView called: {} (version: {}, session: {})",
                cdsViewName, version,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, cdsViewName, (sessionId, resolved) -> {
            // Build path for CDS view source
            String path = AdtUrlBuilder.buildSourceUrl("cds_view", cdsViewName);

            // Add version query param if not active
            Map<String, String> queryParams = new HashMap<>();
            if (!"active".equals(version)) {
                queryParams.put("version", version);
            }

            // Execute via RFC
            String sourceCode = executeInContext(sessionId, (dest, session) ->
                    adtClient.getSourceCodeViaRfc(dest, session, path,
                            queryParams.isEmpty() ? null : queryParams, "text/plain"));

            // Write DDL source to file
            String systemFileId = getSystemFileId(resolved);
            Path filePath = fileStorageService.writeDdl(systemFileId, cdsViewName, sourceCode);

            log.info("Wrote CDS view source to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Format response with file metadata
            return formatCdsViewResponse(resolved, cdsViewName, version, filePath, sourceCode);
        });
    }
}
