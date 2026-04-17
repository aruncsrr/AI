package com.sapjco.mcp.handlers.testing;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.JcoSessionManager;
import com.sapjco.mcp.service.MetadataExtractorService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetCoverageResult tool.
 * Retrieves coverage summary (statement/branch/procedure percentages) from a coverage measurement URI.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetCoverageResultHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("measurement_uri",
                        "Coverage measurement URI from RunAbapUnit response " +
                        "(e.g., \"/sap/bc/adt/runtime/traces/coverage/measurements/{guid}\")")
                .optionalArray("object_uris",
                        "Array of object URIs to scope coverage results " +
                        "(e.g., [\"/sap/bc/adt/oo/classes/zcl_test\"]). " +
                        "IMPORTANT: Without object_uris, returns empty/N/A coverage. " +
                        "You MUST provide the class URI to get actual coverage percentages and statement_uris.")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "GetCoverageResult",
                        "Retrieve coverage summary (statement/branch/procedure percentages) from a coverage " +
                        "measurement URI. Use the measurement_uri returned by RunAbapUnit with with_coverage=true. " +
                        "Returns overall summary, per-object coverage breakdown, and **bulk_statements_uri** and " +
                        "**statement_uris** for use with GetStatementCoverage. " +
                        "IMPORTANT: You MUST provide object_uris to get meaningful results - without it, " +
                        "coverage percentages will be N/A and statement_uris will be empty. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String measurementUri = (String) args.get("measurement_uri");
        @SuppressWarnings("unchecked")
        List<String> objectUris = (List<String>) args.get("object_uris");
        String sessionId = (String) args.get("session_id");

        if (measurementUri == null || measurementUri.isEmpty()) {
            return McpResponseFormatter.error("measurement_uri is required");
        }

        log.info("GetCoverageResult called: {} (objects: {}, session: {})",
                measurementUri,
                objectUris != null ? objectUris.size() : "all",
                sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(null);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // Build request body
            String requestBody = buildCoverageRequest(objectUris);

            // POST to the measurement URI with query parameter
            String path = measurementUri + "?withAdditionalTypeInfo=true";

            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessPostViaRfc(dest, session, path, null, requestBody,
                        "application/xml",
                        "application/vnd.sap.adt.coverage.measurements.v1+xml, application/xml").getBody();
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Extract measurement ID from URI for filename
            String measurementId = measurementUri.substring(measurementUri.lastIndexOf("/") + 1);
            if (measurementId.contains("?")) {
                measurementId = measurementId.substring(0, measurementId.indexOf("?"));
            }

            // Write results to file
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_COVERAGE, "coverage_" + measurementId, response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote coverage results to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractCoverageMetadata(response);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append("Coverage Results\n");
            sb.append(String.format("Measurement: %s\n", measurementId));
            if (objectUris != null && !objectUris.isEmpty()) {
                sb.append(String.format("Objects: %d\n", objectUris.size()));
            }
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("GetCoverageResult failed for {}", measurementUri, e);
            return McpResponseFormatter.error(e);
        } finally {
            if (tempSessionId != null) {
                try {
                    jcoSessionManager.destroySession(tempSessionId);
                } catch (Exception e) {
                    log.warn("Failed to destroy temp session: {}", e.getMessage());
                }
            }
        }
    }

    private String buildCoverageRequest(List<String> objectUris) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<cov:query xmlns:cov=\"http://www.sap.com/adt/cov\" xmlns:adtcore=\"http://www.sap.com/adt/core\">");
        xml.append("<adtcore:objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">");
        xml.append("<objectSet kind=\"inclusive\">");
        xml.append("<adtcore:objectReferences>");

        if (objectUris != null && !objectUris.isEmpty()) {
            for (String uri : objectUris) {
                xml.append("<adtcore:objectReference adtcore:uri=\"").append(escapeXml(uri)).append("\"/>");
            }
        }

        xml.append("</adtcore:objectReferences>");
        xml.append("</objectSet>");
        xml.append("</adtcore:objectSets>");
        xml.append("</cov:query>");
        return xml.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
