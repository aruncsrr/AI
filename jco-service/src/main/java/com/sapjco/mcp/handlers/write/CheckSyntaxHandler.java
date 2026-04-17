package com.sapjco.mcp.handlers.write;

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
import java.util.List;
import java.util.Map;

/**
 * Handler for CheckSyntax tool.
 * Check syntax of an ABAP object without activating it.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckSyntaxHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name", "Name of the ABAP object")
                .requiredEnum("object_type",
                        "Type of object: class, interface, program, function_group, function_module, include, behavior_definition, service_definition, service_binding",
                        List.of("class", "interface", "program", "function_group", "function_module", "include", "behavior_definition", "service_definition", "service_binding"))
                .optionalString("group_name",
                        "Function group name. Required when object_type is function_module.")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session will be " +
                        "created automatically.")
                .buildTool(
                        "CheckSyntax",
                        "Check syntax of an ABAP object without activating it. Performs a \"dry run\" of " +
                        "activation to validate syntax errors. Returns whether syntax is valid along with " +
                        "any error/warning messages. For function modules, provide group_name and set " +
                        "object_type to \"function_module\". " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        String groupName = (String) args.get("group_name");
        String sessionId = (String) args.get("session_id");

        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }

        // Validate group_name is provided for function_module
        boolean isFunctionModule = "function_module".equals(objectType);
        if (isFunctionModule && (groupName == null || groupName.isEmpty())) {
            return McpResponseFormatter.error("group_name is required when object_type is function_module");
        }

        log.info("CheckSyntax called: {} (type: {}, group: {}, session: {})",
                objectName, objectType, groupName != null ? groupName : "(n/a)",
                sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            // Resolve system configuration (use session's system if session provided, otherwise default)
            String systemId = null;
            if (sessionId != null && !sessionId.isEmpty()) {
                systemId = jcoSessionManager.getSession(sessionId).getSystemId();
            }
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);
            String resolvedSystemId = resolved.getSystemId();

            // Create temp session if not provided
            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
                log.debug("Created temp session: {}", tempSessionId);
            }

            final String finalSessionId = sessionId;
            final boolean isFM = isFunctionModule;
            final String fmGroupName = groupName;

            // Execute syntax check via RFC (routes through AdtClient)
            String responseData = jcoSessionManager.executeInContext(finalSessionId, (destination, session) -> {
                if (isFM) {
                    return adtClient.checkSyntaxFunctionModule(destination, fmGroupName, objectName, session);
                }
                return adtClient.checkSyntax(destination, objectName, objectType, session);
            });

            // Build system identifier for file path
            String systemFileId = resolvedSystemId + "_" + resolved.getConfig().getClient();

            // Write results to file
            String sanitizedName = fileStorageService.sanitizeFilename(objectName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_SYNTAX, sanitizedName + "_syntax", responseData);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote syntax check results to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractSyntaxCheckMetadata(responseData);

            // Build system header
            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolvedSystemId,
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Syntax Check: %s (%s)\n", objectName.toUpperCase(), objectType));

            // Format metadata (status, errors, warnings, etc.)
            Object status = metadata.get("status");
            if (status != null) {
                sb.append(String.format("Status: %s\n", status));
            }
            Object errors = metadata.get("errors");
            if (errors != null) {
                sb.append(String.format("Errors: %s\n", errors));
            }
            Object warnings = metadata.get("warnings");
            if (warnings != null) {
                sb.append(String.format("Warnings: %s\n", warnings));
            }
            Object checkExecuted = metadata.get("checkExecuted");
            if (checkExecuted != null) {
                sb.append(String.format("Check Executed: %s\n", checkExecuted));
            }
            Object activationExecuted = metadata.get("activationExecuted");
            if (activationExecuted != null) {
                sb.append(String.format("Activation Executed: %s\n", activationExecuted));
            }

            // Show top errors if any
            @SuppressWarnings("unchecked")
            java.util.List<String> topErrors = (java.util.List<String>) metadata.get("topErrors");
            if (topErrors != null && !topErrors.isEmpty()) {
                sb.append("\nTop Errors:\n");
                for (String error : topErrors) {
                    sb.append(String.format("  - %s\n", error));
                }
            }

            sb.append(String.format(java.util.Locale.ROOT, "\nSize: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(responseData, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("CheckSyntax failed for {}", objectName, e);
            return McpResponseFormatter.error(e);
        } finally {
            // Clean up temp session
            if (tempSessionId != null) {
                try {
                    jcoSessionManager.destroySession(tempSessionId);
                    log.debug("Destroyed temp session: {}", tempSessionId);
                } catch (Exception e) {
                    log.warn("Failed to destroy temp session {}: {}", tempSessionId, e.getMessage());
                }
            }
        }
    }
}
