package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.util.AdtUrlBuilder;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetInclude tool.
 * Retrieves ABAP include program source code via RFC proxy.
 */
@Slf4j
@Component
public class GetIncludeHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("include_name", "Name of the ABAP include program (e.g., \"ZTEST_INCLUDE\")")
                .optionalString("function_group_name",
                        "Optional: Function group name if this include belongs to a function group. " +
                        "When provided, uses the function group include URI " +
                        "(/sap/bc/adt/functions/groups/{group}/includes/{include}) instead of the standalone " +
                        "include URI (/sap/bc/adt/programs/includes/{include}). Required for reading " +
                        "function group includes (e.g., LZFG_TESTF01, LZFG_TESTTOP).")
                .optionalEnum("version", "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"), "active")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "GetInclude",
                        "Get ABAP include program source code. Returns the main source of the specified " +
                        "include. Does not require a session. " +
                        "For function group includes, provide function_group_name to use the correct ADT URI. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "include_name");
        if (validation != null) return validation;

        String includeName = requireString(args, "include_name");
        String functionGroupName = optionalString(args, "function_group_name");
        String version = optionalString(args, "version", "active");

        boolean isFunctionGroupInclude = functionGroupName != null && !functionGroupName.isEmpty();
        String displayName = isFunctionGroupInclude ? functionGroupName + "." + includeName : includeName;

        log.info("GetInclude called: {} (version: {}, system: {}, session: {}, fg: {})",
                displayName, version,
                optionalString(args, "system_id") != null ? optionalString(args, "system_id") : "(default)",
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)",
                isFunctionGroupInclude ? functionGroupName : "(standalone)");

        return executeWithErrorHandling(args, displayName, (sessionId, resolved) -> {
            // Build path for include source
            String path;
            if (isFunctionGroupInclude) {
                path = AdtUrlBuilder.buildFunctionGroupIncludeSourceUrl(functionGroupName, includeName);
            } else {
                path = AdtUrlBuilder.buildSourceUrl("include", includeName);
            }

            // Add version query param if not active
            Map<String, String> queryParams = new HashMap<>();
            if (!"active".equals(version)) {
                queryParams.put("version", version);
            }

            // Execute via RFC
            String sourceCode = executeInContext(sessionId, (dest, session) ->
                    adtClient.getSourceCodeViaRfc(dest, session, path,
                            queryParams.isEmpty() ? null : queryParams, "text/plain"));

            // Write source to file
            String systemFileId = getSystemFileId(resolved);
            String filename = isFunctionGroupInclude ? functionGroupName + "." + includeName : includeName;
            Path filePath = fileStorageService.writeSource(systemFileId, "include", filename, sourceCode);

            log.info("Wrote include source to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Format response with file metadata
            return formatSourceResponse(resolved, "Include", displayName, version, filePath, sourceCode);
        });
    }
}
