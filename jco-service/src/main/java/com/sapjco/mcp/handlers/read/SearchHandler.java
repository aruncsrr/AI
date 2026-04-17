package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handler for Search tool.
 * Searches for ABAP objects in the repository via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
public class SearchHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("query", "Search query with wildcards (e.g., \"ZCL_*\", \"Z*TEST*\", \"*UTIL*\")")
                .optionalString("object_type", "Filter by object type (e.g., \"CLAS\", \"PROG\", \"INTF\", \"FUGR\")")
                .optionalNumber("max_results", "Maximum number of results to return (default: 50)")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "Search",
                        "Search for ABAP objects in the repository. Supports wildcards (* for multiple " +
                        "characters). Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "query");
        if (validation != null) return validation;

        String query = requireString(args, "query");
        String objectType = optionalString(args, "object_type");
        int maxResults = optionalInt(args, "max_results", 50);

        log.info("Search called: {} (type: {}, max: {}, session: {})",
                query, objectType, maxResults,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, query, (sessionId, resolved) -> {
            // Build path and query params
            String path = "/sap/bc/adt/repository/informationsystem/search";
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("operation", "quickSearch");
            queryParams.put("query", query);
            queryParams.put("maxResults", String.valueOf(maxResults));

            if (objectType != null && !objectType.isEmpty()) {
                queryParams.put("objectType", objectType);
            }

            // Execute via RFC
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.getXmlViaRfc(dest, session, path, queryParams));

            // Write results to file - sanitize query for filename
            String systemFileId = getSystemFileId(resolved);
            String sanitizedQuery = fileStorageService.sanitizeFilename(query).replaceAll("\\*", "_star_");
            String filename = "search_" + sanitizedQuery;
            if (filename.length() > 100) {
                filename = filename.substring(0, 100);
            }
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_SEARCH, filename, response);

            log.info("Wrote search results to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractSearchMetadata(response);

            // Build header lines
            String headerLine1 = String.format("Search: \"%s\"", query);
            if (objectType != null && !objectType.isEmpty()) {
                return formatXmlResponse(resolved, filePath, response, metadata,
                        headerLine1, String.format("Filter: type=%s", objectType));
            }

            return formatXmlResponse(resolved, filePath, response, metadata, headerLine1);
        });
    }
}
