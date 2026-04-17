package com.sapjco.mcp.handlers.read;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetVersionHistory tool.
 * Retrieves version history for an ABAP object via RFC proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetVersionHistoryHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name", "Name of the ABAP object (e.g., \"ZCL_MY_CLASS\", \"ZTEST_PROGRAM\")")
                .requiredEnum("object_type", "Type of object: class, interface, program, include, function_group",
                        List.of("class", "interface", "program", "include", "function_group"))
                .optionalEnum("include_type",
                        "For classes only: main, definitions, implementations, testClasses (default: main)",
                        List.of("main", "definitions", "implementations", "testClasses"), "main")
                .optionalBoolean("add_delivery_info",
                        "Include SAP delivery information (Note numbers, Service Pack) in results (default: false)")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetVersionHistory",
                        "Get version history for an ABAP object. Returns a list of all versions with author, " +
                        "date, transport request, and description. Supports classes (with include types), " +
                        "interfaces, programs, includes, and function groups. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        String includeType = args.get("include_type") != null ? (String) args.get("include_type") : "main";
        boolean addDeliveryInfo = args.get("add_delivery_info") != null ? (Boolean) args.get("add_delivery_info") : false;
        String systemId = (String) args.get("system_id");
        String sessionId = (String) args.get("session_id");

        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }

        log.info("GetVersionHistory called: {} (type: {}, include: {}, session: {})",
                objectName, objectType, includeType, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;
            String sourceUri = buildSourceUri(objectType, objectName, includeType);
            // URL pattern: {sourceUri}/versions (not /sap/bc/adt/vit/docu{sourceUri})
            String path = sourceUri + "/versions";

            Map<String, String> queryParams = new HashMap<>();
            if (addDeliveryInfo) {
                queryParams.put("addDeliveryInformation", "true");
            }

            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, path,
                        queryParams.isEmpty() ? null : queryParams,
                        "application/atom+xml;type=feed");
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            String sanitizedName = fileStorageService.sanitizeFilename(objectName.toUpperCase());
            String suffix = !"main".equals(includeType) ? "_" + includeType : "";
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_VERSION_HISTORY, sanitizedName + suffix + "_history", response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote version history to file: {} ({} bytes)", filePath, byteSize);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Version History: %s (%s)\n", objectName.toUpperCase(), objectType));
            if (!"main".equals(includeType)) {
                sb.append(String.format("Include: %s\n", includeType));
            }
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("GetVersionHistory failed for {}", objectName, e);
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

    private String buildSourceUri(String objectType, String objectName, String includeType) {
        // Note: SAP ADT endpoints are case-insensitive for class names
        String encodedName = encodeObjectName(objectName.toLowerCase());
        switch (objectType.toLowerCase()) {
            case "class":
                // Classes use includes path for versions (even for "main")
                String includeFolder = "main".equals(includeType) ? "main" : mapIncludeType(includeType);
                return String.format("/sap/bc/adt/oo/classes/%s/includes/%s", encodedName, includeFolder);
            case "interface":
                return String.format("/sap/bc/adt/oo/interfaces/%s/source/main", encodedName);
            case "program":
                return String.format("/sap/bc/adt/programs/programs/%s/source/main", encodedName);
            case "include":
                return String.format("/sap/bc/adt/programs/includes/%s/source/main", encodedName);
            case "function_group":
                return String.format("/sap/bc/adt/functions/groups/%s/source/main", encodedName);
            default:
                throw new IllegalArgumentException("Unsupported object type: " + objectType);
        }
    }

    private String mapIncludeType(String includeType) {
        switch (includeType.toLowerCase()) {
            case "definitions": return "definitions";
            case "implementations": return "implementations";
            case "testclasses": return "testClasses";
            default: return includeType;
        }
    }

    private String encodeObjectName(String name) {
        if (name.startsWith("/")) {
            return name.replace("/", "%2F");
        }
        return name;
    }
}
