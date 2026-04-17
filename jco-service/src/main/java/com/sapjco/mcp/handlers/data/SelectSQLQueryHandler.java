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
import java.util.regex.Pattern;

/**
 * Handler for SelectSQLQuery tool.
 * Executes read-only Open SQL SELECT queries via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectSQLQueryHandler implements ToolHandler {

    private static final int DEFAULT_ROW_COUNT = 100;
    private static final int MAX_ROW_COUNT = 100000;

    // Pattern to validate SQL is read-only
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^\\s*(SELECT|WITH)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("query",
                        "SQL SELECT statement (e.g., \"SELECT * FROM SFLIGHT WHERE CARRID = 'LH'\"). " +
                        "Must start with SELECT or WITH (for CTEs).")
                .optionalNumber("row_count",
                        "Maximum number of rows to return (default: 100, max: 100000)")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "SelectSQLQuery",
                        "Execute read-only Open SQL SELECT queries. Client-side validation ensures only " +
                        "SELECT/WITH queries are allowed (blocks INSERT, UPDATE, DELETE, etc.). Returns " +
                        "results in XML columnar format. SAP backend also enforces keyword allowlist and " +
                        "performs authorization checks. Limited to 100,000 rows maximum. Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String query = (String) args.get("query");
        int rowCount = args.get("row_count") != null
                ? Math.min(((Number) args.get("row_count")).intValue(), MAX_ROW_COUNT)
                : DEFAULT_ROW_COUNT;
        String systemId = (String) args.get("system_id");
        String sessionId = (String) args.get("session_id");

        if (query == null || query.isEmpty()) {
            return McpResponseFormatter.error("query is required");
        }

        // Client-side validation: must be SELECT or WITH
        if (!SELECT_PATTERN.matcher(query).find()) {
            return McpResponseFormatter.error(
                    "Only SELECT or WITH (CTE) queries are allowed. Query must start with SELECT or WITH.");
        }

        // Client-side validation: no dangerous keywords
        if (DANGEROUS_KEYWORDS.matcher(query).find()) {
            return McpResponseFormatter.error(
                    "Query contains prohibited keywords. Only read-only SELECT queries are allowed.");
        }

        log.info("SelectSQLQuery called: {} characters, max rows: {}, session: {}",
                query.length(), rowCount, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // ADT API expects plain text SQL query in body (not XML wrapped)
            // with query params for row count
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("rowNumber", String.valueOf(rowCount));

            String path = "/sap/bc/adt/datapreview/freestyle";
            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessPostViaRfc(dest, session, path, queryParams, query,
                        "text/plain", "application/vnd.sap.adt.datapreview.table.v1+xml").getBody();
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file - create a hash-based filename for unique queries
            String queryHash = Integer.toHexString(query.hashCode());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_DATA, "sql_" + queryHash, response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote SQL query results to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractDataMetadata(response);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append("SQL Query Results\n");
            sb.append(String.format("Query: %s\n", query.length() > 80 ? query.substring(0, 80) + "..." : query));
            sb.append(String.format(java.util.Locale.ROOT, "Max Rows: %,d\n", rowCount));
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("SelectSQLQuery failed", e);
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
