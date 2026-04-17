package com.sapjco.mcp.handlers.testing;

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
import java.util.Map;

/**
 * Handler for GetATCFindingDocumentation tool.
 * Fetches the human-readable HTML explanation for a specific ATC finding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetATCFindingDocumentationHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("documentation_link",
                        "Documentation URI from finding's atom:link element " +
                        "(e.g., \"/sap/bc/adt/documentation/atc/documents/{runId}/verdict/{object}/{type}/{checkId}/{messageId}/{hash}\")")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID (e.g., \"dev\", \"prod\")")
                .buildTool(
                        "GetATCFindingDocumentation",
                        "Fetch the human-readable HTML documentation for a specific ATC finding. " +
                        "Explains what the check detects, why it matters, and how to fix it. " +
                        "The documentation_link is obtained from the atom:link element in ATC finding XML " +
                        "(from GetATCResult or GetATCFindings). Returns the HTML file path."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String documentationLink = (String) args.get("documentation_link");
        String sessionId = (String) args.get("session_id");
        String systemId = (String) args.get("system_id");

        if (documentationLink == null || documentationLink.isEmpty()) {
            return McpResponseFormatter.error("documentation_link is required");
        }

        log.info("GetATCFindingDocumentation called: {} (session: {})",
                documentationLink, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.getSourceCodeViaRfc(dest, session, documentationLink, null,
                        "application/vnd.sap.adt.docu.v1+html, text/html");
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Extract a meaningful filename from the documentation link
            String filename = extractFilename(documentationLink);
            Path filePath = fileStorageService.writeFile(systemFileId, FileStorageService.CAT_ATC,
                    filename + "_doc", response, FileStorageService.EXT_HTML);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote ATC finding documentation to file: {} ({} bytes)", filePath, byteSize);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response (no excerpt for HTML - not useful in raw form)
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ATC Finding Documentation\n"));
            sb.append(String.format("Link: %s\n", documentationLink));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("GetATCFindingDocumentation failed for {}", documentationLink, e);
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

    /**
     * Extract a meaningful filename from the documentation link.
     * Tries to pull checkId and messageId from the path segments.
     */
    private String extractFilename(String link) {
        if (link == null || link.isEmpty()) {
            return "atc_doc";
        }

        // Link format: /sap/bc/adt/documentation/atc/documents/{runId}/verdict/{object}/{type}/{checkId}/{messageId}/{hash}
        String[] segments = link.split("/");
        if (segments.length >= 2) {
            // Use last two meaningful segments for filename
            String lastSegment = segments[segments.length - 1];
            String secondLast = segments[segments.length - 2];
            return fileStorageService.sanitizeFilename(secondLast + "_" + lastSegment);
        }

        return fileStorageService.sanitizeFilename(link.substring(link.lastIndexOf('/') + 1));
    }
}
