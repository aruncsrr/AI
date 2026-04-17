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
 * Handler for GetClassInclude tool.
 * Retrieves ABAP class include source code (definitions, implementations, macros, testClasses) via RFC proxy.
 */
@Slf4j
@Component
public class GetClassIncludeHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("class_name", "Name of the ABAP class (e.g., \"ZCL_MY_CLASS\")")
                .requiredEnum("include_type", "Type of include to retrieve: definitions (LOCAL class definitions), " +
                        "implementations (LOCAL class implementations), macros (macros), testClasses (unit tests). " +
                        "WARNING: These are LOCAL/PRIVATE includes, NOT the main class source. " +
                        "To get the main class source, use GetClass instead.",
                        List.of("definitions", "implementations", "macros", "testClasses"))
                .optionalEnum("version", "Version to retrieve: \"active\" or \"inactive\" (default: active)",
                        List.of("active", "inactive"), "active")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "GetClassInclude",
                        "Get ABAP class LOCAL include source code. Returns the source of a specific LOCAL/PRIVATE class include " +
                        "(definitions, implementations, macros, or testClasses). These are the CCDEF/CCIMP/CCMAC/CCAU includes, " +
                        "NOT the main class source. To get the main class source, use GetClass instead. " +
                        "Does not require a session. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "class_name");
        if (validation != null) return validation;
        validation = validateRequired(args, "include_type");
        if (validation != null) return validation;

        String className = requireString(args, "class_name");
        String includeType = requireString(args, "include_type");
        String version = optionalString(args, "version", "active");

        log.info("GetClassInclude called: {} include {} (version: {}, system: {}, session: {})",
                className, includeType, version,
                optionalString(args, "system_id") != null ? optionalString(args, "system_id") : "(default)",
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, className + "/" + includeType, (sessionId, resolved) -> {
            // Build path for class include source
            String path = AdtUrlBuilder.buildClassIncludeUrl(className, mapIncludeType(includeType));

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
            Path filePath = fileStorageService.writeClassIncludeSource(systemFileId, className, includeType, sourceCode);

            log.info("Wrote class include source to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Format response with file metadata
            return formatClassIncludeResponse(resolved, className, includeType, version, filePath, sourceCode);
        });
    }

    private String mapIncludeType(String includeType) {
        return switch (includeType.toLowerCase()) {
            case "definitions" -> "definitions";
            case "implementations" -> "implementations";
            case "macros" -> "macros";
            case "testclasses" -> "testclasses";
            default -> includeType.toLowerCase();
        };
    }
}
