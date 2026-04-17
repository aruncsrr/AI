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
 * Handler for ListRuntimeErrors tool.
 * Lists recent ABAP runtime errors (ST22 dumps) via ADT REST API.
 * Writes Atom feed XML to file and returns metadata summary.
 */
@Slf4j
@Component
public class ListRuntimeErrorsHandler extends AbstractReadHandler {

    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final int MAX_RESULTS_LIMIT = 100;
    private static final String ACCEPT_ATOM_FEED = "application/atom+xml;type=feed";

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .optionalString("user",
                        "SAP username to filter dumps by (uppercased automatically). " +
                        "If not provided, returns all accessible dumps.")
                .optionalString("from",
                        "Start timestamp filter (ISO format, e.g., \"2024-01-15T00:00:00\")")
                .optionalString("to",
                        "End timestamp filter (ISO format, e.g., \"2024-01-16T23:59:59\")")
                .optionalNumber("max_results",
                        "Maximum number of entries to return (default: 50, max: 100)")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "ListRuntimeErrors",
                        "List recent ABAP runtime errors (ST22 dumps) as Atom feed. " +
                        "Filters by user, time range, and result count. " +
                        "Each entry contains: error type (atom:category term), program, user (atom:author), timestamp (atom:published), " +
                        "and a dump_id for use with GetRuntimeError. " +
                        "The dump_id is in the atom:link[@rel='self'][@type='text/plain'] href attribute — " +
                        "extract the path after '/sap/bc/adt/runtime/dump/' and pass it URL-encoded (with %20 for spaces) to GetRuntimeError. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        String user = optionalString(args, "user");
        String from = optionalString(args, "from");
        String to = optionalString(args, "to");
        int maxResults = optionalInt(args, "max_results", DEFAULT_MAX_RESULTS, MAX_RESULTS_LIMIT);

        log.info("ListRuntimeErrors called: user={}, from={}, to={}, max={}", user, from, to, maxResults);

        return executeWithErrorHandling(args, "runtime_errors", (sessionId, resolved) -> {
            // Build path and query params
            String path = "/sap/bc/adt/runtime/dumps";
            Map<String, String> queryParams = new LinkedHashMap<>();

            if (user != null && !user.isEmpty()) {
                // FQL (Feed Query Language) format required - plain "user" param is ignored by backend
                // Format from ADT client: and( equals( attribute, value ) )
                queryParams.put("$query", "and( equals( user, " + user.toUpperCase() + " ) )");
            }
            if (from != null && !from.isEmpty()) {
                queryParams.put("from", toAbapTimestamp(from));
            }
            if (to != null && !to.isEmpty()) {
                queryParams.put("to", toAbapTimestamp(to));
            }
            queryParams.put("$top", String.valueOf(maxResults));

            // Execute via RFC - Atom feed (requires specific Accept header)
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.getSourceCodeViaRfc(dest, session, path, queryParams, ACCEPT_ATOM_FEED));

            // Write results to file
            String systemFileId = getSystemFileId(resolved);
            String filename = "dumps_list";
            if (user != null && !user.isEmpty()) {
                filename = "dumps_" + fileStorageService.sanitizeFilename(user.toUpperCase());
            }
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_RUNTIME_ERRORS, filename, response);

            log.info("Wrote runtime error list to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Build header
            StringBuilder headerLine = new StringBuilder("Runtime Errors (ST22)");
            if (user != null && !user.isEmpty()) {
                headerLine.append(String.format(" | User: %s", user.toUpperCase()));
            }

            return formatXmlResponse(resolved, filePath, response, null, headerLine.toString());
        });
    }

    /**
     * Convert ISO timestamp (e.g., "2026-03-07T00:00:00") to ABAP timestamp format ("20260307000000").
     * Strips all non-digit characters so both ISO and raw numeric formats are accepted.
     */
    private String toAbapTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return null;
        return timestamp.replaceAll("[^0-9]", "");
    }
}
