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
import com.sapjco.mcp.service.MetadataExtractorService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetATCFindings tool.
 * Retrieves ATC findings by worklist ID via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetATCFindingsHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("worklist_id", "Worklist ID (32-character GUID) from RunATC response")
                .optionalEnum("format",
                        "Output format: \"xml\" or \"checkstyle\" (default: xml)",
                        List.of("xml", "checkstyle"), "xml")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "GetATCFindings",
                        "Retrieve ATC findings by worklist ID. Use this to re-fetch results from a previous " +
                        "ATC run without running checks again. The worklist ID is returned by RunATC. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String worklistId = (String) args.get("worklist_id");
        String format = args.get("format") != null ? (String) args.get("format") : "xml";
        String sessionId = (String) args.get("session_id");

        if (worklistId == null || worklistId.isEmpty()) {
            return McpResponseFormatter.error("worklist_id is required");
        }

        log.info("GetATCFindings called: {} (format: {}, session: {})",
                worklistId, format, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(null);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;
            String path = "/sap/bc/adt/atc/worklists/" + worklistId;

            // Set accept header based on format
            String acceptHeader = "checkstyle".equals(format)
                    ? "application/vnd.sap.atc.checkstyle+xml"
                    : "application/atc.worklist.v1+xml";

            // Add required query parameter
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("includeExemptedFindings", "false");

            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, path, queryParams, acceptHeader);
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            String extension = "checkstyle".equals(format) ? "_checkstyle.xml" : "_findings.xml";
            Path filePath = fileStorageService.writeFile(systemFileId, FileStorageService.CAT_ATC, worklistId + extension, response, "");
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote ATC findings to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractAtcMetadata(response);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ATC Findings: %s\n", worklistId));
            sb.append(String.format("Format: %s\n", format));
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("GetATCFindings failed for {}", worklistId, e);
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
}
