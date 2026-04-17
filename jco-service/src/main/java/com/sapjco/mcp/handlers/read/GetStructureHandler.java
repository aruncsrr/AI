package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.util.ObjectNameEncoder;
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
 * Handler for GetStructure tool.
 * Retrieves ABAP structure definition with field components via RFC proxy.
 */
@Slf4j
@Component
public class GetStructureHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("structure_name", "Name of the ABAP structure")
                .optionalEnum("version", "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"), "active")
                .optionalBoolean("with_fields",
                        "Include detailed field components (default: true). Set to false for metadata only.")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetStructure",
                        "Get ABAP structure definition with field components. Returns XML with structure " +
                        "metadata and detailed field information including data types, lengths, and labels. " +
                        "Does not require a session. " +
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
        String version = optionalString(args, "version", "active");
        boolean withFields = optionalBoolean(args, "with_fields", true);

        log.info("GetStructure called: {} (version: {}, withFields: {}, session: {})",
                structureName, version, withFields,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, structureName, (sessionId, resolved) -> {
            // Build path for structure definition
            String encodedName = ObjectNameEncoder.encode(structureName);
            String path = "/sap/bc/adt/ddic/structures/" + encodedName;

            // Build query params
            Map<String, String> queryParams = new LinkedHashMap<>();
            if (!"active".equals(version)) {
                queryParams.put("version", version);
            }
            if (withFields) {
                queryParams.put("withComponents", "true");
            }

            // Execute via RFC
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessGetViaRfc(dest, session, path,
                            queryParams.isEmpty() ? null : queryParams,
                            "application/vnd.sap.adt.structures.v2+xml"));

            // Write results to file
            String systemFileId = getSystemFileId(resolved);
            String sanitizedName = fileStorageService.sanitizeFilename(structureName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_DICT_STRUCTURE, sanitizedName, response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote structure definition to file: {} ({} bytes)", filePath, byteSize);

            // Build summary response (custom due to withFields parameter)
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Structure: %s\n", structureName.toUpperCase()));
            sb.append(String.format("Version: %s\n", version));
            sb.append(String.format("With Fields: %s\n", withFields));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return success(resolved, sb.toString());
        });
    }
}
