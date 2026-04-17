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
 * Handler for GetStatementCoverage tool.
 * Retrieves line-level coverage data for specific ABAP objects using the ADT bulk statements API.
 * Writes results to file and returns metadata summary.
 *
 * Uses the two-phase approach aligned with Eclipse ADT:
 * 1. GetCoverageResult returns bulk_statements_uri and per-node statement_uris
 * 2. This tool POSTs to bulk_statements_uri with statement URIs to get line-by-line coverage
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetStatementCoverageHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("bulk_statements_uri",
                        "Bulk statements URI from GetCoverageResult response " +
                        "(e.g., \"/sap/bc/adt/runtime/traces/coverage/results/bulkstatements?measurementId={guid}\")")
                .requiredArray("statement_uris",
                        "Array of statement URIs from GetCoverageResult node entries " +
                        "(e.g., [\"/sap/bc/adt/runtime/traces/coverage/results/statements?...\"])")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "GetStatementCoverage",
                        "Retrieve line-level coverage data for specific ABAP objects. Shows which lines " +
                        "were executed (green) vs not executed (red) during test runs. Use the bulk_statements_uri " +
                        "and statement_uris from GetCoverageResult to fetch detailed coverage. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String bulkStatementsUri = (String) args.get("bulk_statements_uri");
        @SuppressWarnings("unchecked")
        List<String> statementUris = (List<String>) args.get("statement_uris");
        String sessionId = (String) args.get("session_id");

        if (bulkStatementsUri == null || bulkStatementsUri.isEmpty()) {
            return McpResponseFormatter.error("bulk_statements_uri is required. Get it from GetCoverageResult output.");
        }
        if (statementUris == null || statementUris.isEmpty()) {
            return McpResponseFormatter.error("statement_uris is required and must contain at least one URI. " +
                    "Get statement URIs from GetCoverageResult per-object entries.");
        }

        log.info("GetStatementCoverage called: {} (uris: {}, session: {})",
                bulkStatementsUri,
                statementUris.size(),
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

            // Build request body in statementsBulkRequest format
            String requestBody = buildStatementsBulkRequest(statementUris);

            // POST to bulk statements endpoint
            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessPostViaRfc(dest, session, bulkStatementsUri, null, requestBody,
                        "application/xml",
                        "application/vnd.sap.adt.coverage.statements.v1+xml, application/xml").getBody();
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Extract measurement ID from URI for filename
            String measurementId = "";
            if (bulkStatementsUri.contains("measurementId=")) {
                int start = bulkStatementsUri.indexOf("measurementId=") + 14;
                int end = bulkStatementsUri.indexOf("&", start);
                measurementId = end > 0 ? bulkStatementsUri.substring(start, end) : bulkStatementsUri.substring(start);
            } else {
                measurementId = Integer.toHexString(bulkStatementsUri.hashCode());
            }

            // Write results to file
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_COVERAGE, "statements_" + measurementId, response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote statement coverage to file: {} ({} bytes)", filePath, byteSize);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append("Statement Coverage\n");
            sb.append(String.format("Statements: %d URIs processed\n", statementUris.size()));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("GetStatementCoverage failed for {}", bulkStatementsUri, e);
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

    /**
     * Build the statementsBulkRequest XML body.
     * Format: <cov:statementsBulkRequest><statementsRequest get="{uri}"/></cov:statementsBulkRequest>
     */
    private String buildStatementsBulkRequest(List<String> statementUris) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<cov:statementsBulkRequest xmlns:cov=\"http://www.sap.com/adt/cov\">\n");

        for (String uri : statementUris) {
            xml.append("  <statementsRequest get=\"").append(escapeXml(uri)).append("\"/>\n");
        }

        xml.append("</cov:statementsBulkRequest>");
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
