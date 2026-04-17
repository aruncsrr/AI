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
import java.util.List;
import java.util.Map;

/**
 * Handler for GetRuntimeError tool.
 * Retrieves a specific ABAP runtime error dump (ST22) via ADT REST API.
 * Supports metadata (XML), formatted text, and summary (HTML) modes.
 */
@Slf4j
@Component
public class GetRuntimeErrorHandler extends AbstractReadHandler {

    private static final String EXT_TXT = ".txt";

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("dump_id",
                        "The dump identifier from ListRuntimeErrors. Extract from atom:link[@rel='self'][@type='text/plain'] href, " +
                        "taking the path after '/sap/bc/adt/runtime/dump/'. " +
                        "Pass it URL-encoded (keep %20 for spaces, e.g., " +
                        "\"20260307150108ldcierx_ERX_00%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20HERNANDEZPL%20001%20%20%20%20%20%20114\").")
                .optionalEnum("content",
                        "What to retrieve: \"formatted\" (default, text/plain dump), " +
                        "\"metadata\" (XML reference with links), \"summary\" (HTML summary)",
                        List.of("formatted", "metadata", "summary"), "formatted")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetRuntimeError",
                        "Retrieve a specific ABAP runtime error dump (ST22). " +
                        "Supports three modes via `content` parameter: " +
                        "\"formatted\" (default, full text dump with error analysis, source code extract, and call stack), " +
                        "\"metadata\" (XML with dump properties and links), " +
                        "\"summary\" (HTML summary). " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "dump_id");
        if (validation != null) return validation;

        String dumpId = requireString(args, "dump_id");
        String contentMode = optionalString(args, "content", "formatted");

        log.info("GetRuntimeError called: dumpId={}, content={}", dumpId, contentMode);

        return executeWithErrorHandling(args, dumpId, (sessionId, resolved) -> {
            String encodedDumpId = encodeObjectName(dumpId);
            String systemFileId = getSystemFileId(resolved);
            String sanitizedDumpId = fileStorageService.sanitizeFilename(dumpId);

            String response;
            Path filePath;
            String extension;

            switch (contentMode) {
                case "metadata": {
                    String path = "/sap/bc/adt/runtime/dump/" + encodedDumpId;
                    response = executeInContext(sessionId, (dest, session) ->
                            adtClient.getSourceCodeViaRfc(dest, session, path, new LinkedHashMap<>(),
                                    "application/vnd.sap.adt.runtime.dump.v1+xml"));
                    extension = FileStorageService.EXT_XML;
                    filePath = fileStorageService.writeFile(systemFileId, FileStorageService.CAT_RUNTIME_ERRORS,
                            sanitizedDumpId + "_metadata", response, extension);
                    break;
                }
                case "summary": {
                    String path = "/sap/bc/adt/runtime/dump/" + encodedDumpId + "/summary";
                    response = executeInContext(sessionId, (dest, session) ->
                            adtClient.getSourceCodeViaRfc(dest, session, path, new LinkedHashMap<>(),
                                    "text/html"));
                    extension = FileStorageService.EXT_HTML;
                    filePath = fileStorageService.writeFile(systemFileId, FileStorageService.CAT_RUNTIME_ERRORS,
                            sanitizedDumpId + "_summary", response, extension);
                    break;
                }
                default: { // "formatted"
                    String path = "/sap/bc/adt/runtime/dump/" + encodedDumpId + "/formatted";
                    response = executeInContext(sessionId, (dest, session) ->
                            adtClient.getSourceCodeViaRfc(dest, session, path, new LinkedHashMap<>(),
                                    "text/plain"));
                    extension = EXT_TXT;
                    filePath = fileStorageService.writeFile(systemFileId, FileStorageService.CAT_RUNTIME_ERRORS,
                            sanitizedDumpId, response, extension);
                    break;
                }
            }

            log.info("Wrote runtime error dump to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            String headerLine = String.format("Runtime Error: %s (mode: %s)", dumpId, contentMode);
            return formatFileResponse(resolved, filePath, response, extension, headerLine);
        });
    }
}
