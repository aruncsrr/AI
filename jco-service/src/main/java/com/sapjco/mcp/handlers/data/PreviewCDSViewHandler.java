package com.sapjco.mcp.handlers.data;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Handler for PreviewCDSView tool.
 * Preview data from CDS views with optional parameters via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreviewCDSViewHandler implements ToolHandler {

    private static final int DEFAULT_ROW_COUNT = 100;
    private static final int MAX_ROW_COUNT = 100000;

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("cds_view_name",
                        "Name of the CDS view / DDL source (e.g., \"I_FLIGHT\", \"I_COUNTRY\", \"I_CUSTOMER\")")
                .optionalNumber("row_count",
                        "Maximum number of rows to return (default: 100, max: 100000)")
                .optionalObject("parameters",
                        "Optional CDS view parameters as key-value pairs (e.g., {\"P_DATE\": \"20240101\"})")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "PreviewCDSView",
                        "Preview data from CDS views with optional parameters. Returns data in XML columnar " +
                        "format. Supports parameterized CDS views. Limited to 100,000 rows maximum. " +
                        "Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    @SuppressWarnings("unchecked")
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String cdsViewName = (String) args.get("cds_view_name");
        int rowCount = args.get("row_count") != null
                ? Math.min(((Number) args.get("row_count")).intValue(), MAX_ROW_COUNT)
                : DEFAULT_ROW_COUNT;
        Map<String, Object> parameters = args.get("parameters") != null
                ? (Map<String, Object>) args.get("parameters")
                : null;
        String systemId = (String) args.get("system_id");
        String sessionId = (String) args.get("session_id");

        if (cdsViewName == null || cdsViewName.isEmpty()) {
            return McpResponseFormatter.error("cds_view_name is required");
        }

        log.info("PreviewCDSView called: {} (rows: {}, params: {}, session: {})",
                cdsViewName, rowCount, parameters != null ? parameters.size() : 0,
                sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // Build query params
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("ddlSourceName", cdsViewName.toUpperCase());
            queryParams.put("rowNumber", String.valueOf(rowCount));

            // Build request body for CDS parameters if provided
            String requestBody = "";
            if (parameters != null && !parameters.isEmpty()) {
                StringBuilder paramXml = new StringBuilder();
                paramXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                paramXml.append("<dataPreview:parameters xmlns:dataPreview=\"http://www.sap.com/adt/dataPreview\">\n");
                for (Map.Entry<String, Object> param : parameters.entrySet()) {
                    paramXml.append(String.format("  <parameter name=\"%s\" value=\"%s\"/>\n",
                            escapeXml(param.getKey()), escapeXml(String.valueOf(param.getValue()))));
                }
                paramXml.append("</dataPreview:parameters>");
                requestBody = paramXml.toString();
            }

            String path = "/sap/bc/adt/datapreview/cds";
            final String body = requestBody;
            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessPostViaRfc(dest, session, path, queryParams, body,
                        "application/xml", "application/vnd.sap.adt.datapreview.table.v1+xml").getBody();
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_DATA, cdsViewName.toUpperCase() + "_cds_preview", response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote CDS view preview to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractDataMetadata(response);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("CDS View Data: %s\n", cdsViewName.toUpperCase()));
            sb.append(String.format(java.util.Locale.ROOT, "Max Rows: %,d\n", rowCount));
            if (parameters != null && !parameters.isEmpty()) {
                sb.append("Parameters: ").append(parameters).append("\n");
            }
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("PreviewCDSView failed for {}", cdsViewName, e);
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
     * Escape special XML characters in a string.
     */
    private String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
