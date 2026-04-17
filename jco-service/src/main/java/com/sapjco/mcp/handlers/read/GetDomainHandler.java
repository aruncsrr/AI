package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.util.ObjectNameEncoder;
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
 * Handler for GetDomain tool.
 * Retrieves ABAP domain definition via RFC proxy.
 */
@Slf4j
@Component
public class GetDomainHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("domain_name", "Name of the ABAP domain (e.g., \"S_CONN_ID\", \"MANDT\")")
                .optionalEnum("version", "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"), "active")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetDomain",
                        "Get ABAP domain definition. Returns XML with domain properties including " +
                        "data type, length, and fixed values. Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "domain_name");
        if (validation != null) return validation;

        String domainName = requireString(args, "domain_name");
        String version = optionalString(args, "version", "active");

        log.info("GetDomain called: {} (version: {}, session: {})",
                domainName, version,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, domainName, (sessionId, resolved) -> {
            // Build path for domain definition
            String encodedName = ObjectNameEncoder.encode(domainName);
            String path = "/sap/bc/adt/ddic/domains/" + encodedName;

            // Add version query param if not active
            Map<String, String> queryParams = new HashMap<>();
            if (!"active".equals(version)) {
                queryParams.put("version", version);
            }

            // Execute via RFC
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessGetViaRfc(dest, session, path,
                            queryParams.isEmpty() ? null : queryParams,
                            "application/vnd.sap.adt.domains.v2+xml"));

            // Write results to file
            String systemFileId = getSystemFileId(resolved);
            String sanitizedName = fileStorageService.sanitizeFilename(domainName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_DICT_DOMAIN, sanitizedName, response);

            log.info("Wrote domain definition to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Format response with file metadata
            return formatDictionaryResponse(resolved, "Domain", domainName, version, filePath, response);
        });
    }
}
