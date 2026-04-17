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
 * Handler for GetServiceBinding tool.
 * Retrieves RAP Service Binding (SRVB) structured XML via RFC proxy.
 * Service bindings are read-only structured objects (not source-based).
 * Writes XML to file and returns metadata summary.
 */
@Slf4j
@Component
public class GetServiceBindingHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("srvb_name",
                        "Name of the service binding (e.g., \"ZTEST_SRVB\", \"UI_TRAVEL_O2\")")
                .optionalEnum("version", "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"), "active")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetServiceBinding",
                        "Get RAP Service Binding (SRVB) metadata. Returns the structured XML definition " +
                        "including binding type, service version, and exposed entities. " +
                        "Service bindings are read-only (not source-based). Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "srvb_name");
        if (validation != null) return validation;

        String srvbName = requireString(args, "srvb_name");
        String version = optionalString(args, "version", "active");

        log.info("GetServiceBinding called: {} (version: {}, session: {})",
                srvbName, version,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, srvbName, (sessionId, resolved) -> {
            // Build path - NO /source/main suffix, this is a structured object
            String path = AdtUrlBuilder.buildBaseUrl("service_binding", srvbName);

            // Add version parameter
            Map<String, String> queryParams = new HashMap<>();
            if (version != null && (version.equals("active") || version.equals("inactive"))) {
                queryParams.put("version", version);
            }

            // Make GET request with service binding Accept header via RFC
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.getSourceCodeViaRfc(dest, session, path,
                            queryParams.isEmpty() ? null : queryParams,
                            "application/vnd.sap.adt.businessservices.servicebinding.v2+xml,application/xml"));

            // Write results to file
            String systemFileId = getSystemFileId(resolved);
            String sanitizedName = fileStorageService.sanitizeFilename(srvbName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId,
                    FileStorageService.CAT_SERVICE_BINDING, sanitizedName, response);

            log.info("Wrote service binding to file: {} ({} bytes)",
                    filePath, fileStorageService.getByteSize(filePath));

            // Build header line
            String headerLine = String.format("Service Binding: %s (version: %s)", srvbName.toUpperCase(), version);

            return formatXmlResponse(resolved, filePath, response, Map.of(), headerLine);
        });
    }
}
