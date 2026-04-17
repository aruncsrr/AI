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
 * Handler for GetStructureFields tool.
 * Retrieves component-level information for ABAP structures via element info API.
 */
@Slf4j
@Component
public class GetStructureFieldsHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("structure_name",
                        "Name of the ABAP structure (e.g., \"BAPI0002_1\", \"S_TOR_ROOT\")")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetStructureFields",
                        "Get ABAP structure component definitions via element info API. Returns component names, " +
                        "data types, lengths, and descriptions. Use this after GetStructure " +
                        "to retrieve component-level information. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "structure_name");
        if (validation != null) return validation;

        String structureName = requireString(args, "structure_name");

        log.info("GetStructureFields called: {} (session: {}, system: {})",
                structureName,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)",
                optionalString(args, "system_id") != null ? optionalString(args, "system_id") : "(default)");

        return executeWithErrorHandling(args, structureName, (sessionId, resolved) -> {
            // Build element info API query
            String path = "/sap/bc/adt/ddic/elementinfo";
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("path", structureName);
            queryParams.put("type", "TABL%2FDS");  // URL-encoded "TABL/DS"

            // Execute via RFC
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessGetViaRfc(dest, session, path, queryParams,
                            "application/vnd.sap.adt.elementinfo+xml"));

            // Write results to file
            String systemFileId = getSystemFileId(resolved);
            String sanitizedName = fileStorageService.sanitizeFilename(structureName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_DICT_STRUCTURE_FIELDS, sanitizedName + "_fields", response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote structure fields to file: {} ({} bytes)", filePath, byteSize);

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Structure Fields: %s\n", structureName.toUpperCase()));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return success(resolved, sb.toString());
        });
    }
}
