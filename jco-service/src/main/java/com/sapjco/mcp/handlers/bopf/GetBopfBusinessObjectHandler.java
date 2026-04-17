package com.sapjco.mcp.handlers.bopf;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetBopfBusinessObject tool.
 * Retrieves BOPF Business Object metadata via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
public class GetBopfBusinessObjectHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("bo_name",
                        "Name of the BOPF Business Object (e.g., \"/SCMTMS/TOR\", \"/BOBF/DEMO_SALES_ORDER\")")
                .optionalEnum("version",
                        "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"),
                        "active")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). If not specified, uses the default system.")
                .buildTool(
                        "GetBopfBusinessObject",
                        "Get BOPF Business Object metadata. Returns the full BO definition including nodes, actions, " +
                        "associations, determinations, validations, and queries. Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "bo_name");
        if (validation != null) return validation;

        String boName = requireString(args, "bo_name");
        String version = optionalString(args, "version", "active");

        log.info("GetBopfBusinessObject called: {} (version: {}, session: {})",
                boName, version,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, boName, (sessionId, resolved) -> {
            // Build path - BO names often contain slashes, URL-encode and lowercase
            String encodedBoName = URLEncoder.encode(boName, StandardCharsets.UTF_8).toLowerCase();
            String path = "/sap/bc/adt/bopf/businessobjects/" + encodedBoName;

            // Add version parameter
            Map<String, String> queryParams = new HashMap<>();
            if (version != null && (version.equals("active") || version.equals("inactive"))) {
                queryParams.put("version", version);
            }

            // Make GET request with BOPF v4 Accept header via RFC
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.getSourceCodeViaRfc(dest, session, path,
                            queryParams.isEmpty() ? null : queryParams,
                            "application/vnd.sap.ap.adt.bopf.businessobjects.v4+xml,application/xml"));

            // Write results to file - sanitize BO name for filename
            String systemFileId = getSystemFileId(resolved);
            String sanitizedName = fileStorageService.sanitizeFilename(boName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_BOPF, sanitizedName, response);

            log.info("Wrote BOPF business object to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractBopfMetadata(response);

            // Build header line
            String headerLine = String.format("BOPF Business Object: %s (version: %s)", boName, version);

            return formatXmlResponse(resolved, filePath, response, metadata, headerLine);
        });
    }
}
