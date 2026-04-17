package com.sapjco.mcp.handlers.debug;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.JcoSession;
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
import java.util.Map;

/**
 * Handler for DebugGetVariables tool.
 * Gets variables in a debug session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebugGetVariablesHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED)")
                .optionalString("parent_id",
                        "Parent ID: @ROOT (hierarchy), @LOCALS (local variables), or variable ID " +
                        "(default: @ROOT)")
                .buildTool(
                        "DebugGetVariables",
                        "Get variables in a debug session. Supports hierarchy: @ROOT for top-level, " +
                        "@LOCALS for local variables, or specific variable ID to expand structures. " +
                        "IMPORTANT: Use @LOCALS (with S) to get local variables. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        String parentId = args.get("parent_id") != null ? (String) args.get("parent_id") : "@ROOT";

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }

        log.info("DebugGetVariables called - session: {}, parentId: {}", sessionId, parentId);

        try {
            JcoSession session = sessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            String responseXml = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                return adtClient.debugGetChildVariables(
                        destination,
                        parentId,
                        sess.getHttpClient(),
                        sess.getCsrfTokenCache(),
                        sess
                );
            });

            // Build system identifier for file path
            String systemFileId = systemId + "_" + resolved.getConfig().getClient();

            // Write results to file with parent and timestamp for uniqueness
            String sanitizedParent = fileStorageService.sanitizeFilename(parentId);
            String timestamp = String.valueOf(System.currentTimeMillis());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_DEBUG, "vars_" + sanitizedParent + "_" + timestamp, responseXml);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote debug variables to file: {} ({} bytes)", filePath, byteSize);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    systemId,
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append("Debug Variables\n");
            sb.append(String.format("Session: %s\n", sessionId));
            sb.append(String.format("Parent: %s\n", parentId));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(responseXml, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("DebugGetVariables failed", e);
            return McpResponseFormatter.error(e);
        }
    }
}
