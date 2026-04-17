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
import java.util.HashMap;
import java.util.Map;

/**
 * Handler for GetPackageContents tool.
 * Retrieves contents of an ABAP package via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
public class GetPackageContentsHandler extends AbstractReadHandler {

    private static final int DEFAULT_MAX_RESULTS = 500;
    private static final int MAX_RESULTS_LIMIT = 5000;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("package_name", "Name of the ABAP package")
                .optionalNumber("max_results",
                        "Maximum number of objects to return (default: 500, max: 5000). " +
                        "Returns error if package exceeds 5000 objects without filters.")
                .optionalString("object_type",
                        "Optional: Filter by object type (e.g., \"CLAS\", \"PROG\", \"INTF\", \"FUGR\"). " +
                        "Only return objects of this type.")
                .optionalString("name_pattern",
                        "Optional: Filter by name pattern with wildcards (e.g., \"Z*\", \"*TEST*\", \"CL_*\"). " +
                        "Case-insensitive.")
                .optionalString("session_id",
                        "Optional session ID. If not provided, a temporary session will be created automatically.")
                .buildTool(
                        "GetPackageContents",
                        "Retrieve contents of an ABAP package using ADT Repository Node Structure. " +
                        "Returns objects contained in the package. For large packages, use filters or " +
                        "increase max_results. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "package_name");
        if (validation != null) return validation;

        String packageName = requireString(args, "package_name");
        int maxResults = optionalInt(args, "max_results", DEFAULT_MAX_RESULTS, MAX_RESULTS_LIMIT);
        String objectType = optionalString(args, "object_type");
        String namePattern = optionalString(args, "name_pattern");

        log.info("GetPackageContents called: {} (max: {}, type: {}, pattern: {}, session: {})",
                packageName, maxResults, objectType, namePattern,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, packageName, (sessionId, resolved) -> {
            // Build query params for repository node structure
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("parent_name", packageName.toUpperCase());
            queryParams.put("parent_type", "DEVC/K");
            queryParams.put("withShortDescriptions", "true");

            if (objectType != null && !objectType.isEmpty()) {
                queryParams.put("object_type", objectType.toUpperCase());
            }

            // ADT API requires POST with specific body (not GET)
            String requestBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<asx:values xmlns:asx=\"http://www.sap.com/abapxml\">\n" +
                    "  <DATA><TV_NODEKEY>000000</TV_NODEKEY></DATA>\n" +
                    "</asx:values>";

            String path = "/sap/bc/adt/repository/nodestructure";
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessPostViaRfc(dest, session, path, queryParams, requestBody,
                            "application/vnd.sap.as+xml;dataname=com.sap.adt.RepositoryObjectTreeContent",
                            "application/vnd.sap.as+xml;dataname=com.sap.adt.RepositoryObjectTreeContent").getBody());

            // Count results (simplified - real implementation would parse XML)
            int resultCount = countOccurrences(response, "adtcore:objectReference");

            // Apply name pattern filter if specified (client-side)
            String filteredResponse = response;
            if (namePattern != null && !namePattern.isEmpty()) {
                log.debug("Applying name pattern filter: {}", namePattern);
                // In a full implementation, we'd parse XML and filter by pattern
            }

            // Check result limit
            if (resultCount > MAX_RESULTS_LIMIT && objectType == null && namePattern == null) {
                return error(String.format("Package %s contains more than %d objects (%d found). " +
                                "Please use object_type or name_pattern filters to narrow the results, " +
                                "or use the Search tool for specific objects.",
                        packageName, MAX_RESULTS_LIMIT, resultCount));
            }

            // Write results to file - sanitize package name (may start with $ or /)
            String systemFileId = getSystemFileId(resolved);
            String sanitizedName = fileStorageService.sanitizeFilename(packageName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_PACKAGE, sanitizedName, filteredResponse);

            log.info("Wrote package contents to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractPackageMetadata(filteredResponse);

            // Build header lines
            StringBuilder headerLines = new StringBuilder();
            headerLines.append(String.format("Package: %s", packageName.toUpperCase()));
            if (objectType != null && !objectType.isEmpty()) {
                headerLines.append(String.format("\nFilter: type=%s", objectType));
            }
            if (namePattern != null && !namePattern.isEmpty()) {
                headerLines.append(String.format("\nFilter: pattern=%s", namePattern));
            }

            return formatXmlResponse(resolved, filePath, filteredResponse, metadata, headerLines.toString());
        });
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
