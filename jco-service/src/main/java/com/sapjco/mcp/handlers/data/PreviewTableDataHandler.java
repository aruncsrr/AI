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
 * Handler for PreviewTableData tool.
 * Preview contents of DDIC tables and views via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreviewTableDataHandler implements ToolHandler {

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
                .requiredString("table_name",
                        "Name of the DDIC table or view (e.g., \"SFLIGHT\", \"SAIRPORT\", \"MARA\")")
                .optionalNumber("row_count",
                        "Maximum number of rows to return (default: 100, max: 100000)")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "PreviewTableData",
                        "Preview contents of DDIC tables and views. Returns data in XML columnar format " +
                        "with metadata (column names, types, descriptions, key attributes) and row data. " +
                        "Limited to 100,000 rows maximum. Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String tableName = (String) args.get("table_name");
        int rowCount = args.get("row_count") != null
                ? Math.min(((Number) args.get("row_count")).intValue(), MAX_ROW_COUNT)
                : DEFAULT_ROW_COUNT;
        String systemId = (String) args.get("system_id");
        String sessionId = (String) args.get("session_id");

        if (tableName == null || tableName.isEmpty()) {
            return McpResponseFormatter.error("table_name is required");
        }

        log.info("PreviewTableData called: {} (rows: {}, session: {})",
                tableName, rowCount, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // Use correct query param name: ddicEntityName (not dataSourceName)
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("ddicEntityName", tableName.toUpperCase());
            queryParams.put("rowNumber", String.valueOf(rowCount));

            String path = "/sap/bc/adt/datapreview/ddic";
            // ADT API requires POST with empty body (not GET)
            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessPostViaRfc(dest, session, path, queryParams, "",
                        "text/plain", "application/vnd.sap.adt.datapreview.table.v1+xml").getBody();
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_DATA, tableName.toUpperCase() + "_preview", response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote table preview to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractDataMetadata(response);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Table Data: %s\n", tableName.toUpperCase()));
            sb.append(String.format(java.util.Locale.ROOT, "Max Rows: %,d\n", rowCount));
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("PreviewTableData failed for {}", tableName, e);
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
}
