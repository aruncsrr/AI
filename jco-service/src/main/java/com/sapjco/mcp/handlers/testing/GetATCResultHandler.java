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
import java.util.Map;

/**
 * Handler for GetATCResult tool.
 * Drills into a specific persistent ATC result to get its findings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetATCResultHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("display_id",
                        "32-character GUID from ListATCResults response (the displayId field)")
                .optionalBoolean("include_exempted",
                        "Include exempted findings in results. Default: false")
                .optionalString("contact_person",
                        "Filter findings by responsible person")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID (e.g., \"dev\", \"prod\")")
                .buildTool(
                        "GetATCResult",
                        "Drill into a specific persistent ATC result to get its findings. Use ListATCResults " +
                        "first to browse available results and get the display_id. Returns findings with " +
                        "priority, check title, message, location, and documentation links. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String displayId = (String) args.get("display_id");
        boolean includeExempted = args.get("include_exempted") != null
                ? (Boolean) args.get("include_exempted")
                : false;
        String contactPerson = (String) args.get("contact_person");
        String sessionId = (String) args.get("session_id");
        String systemId = (String) args.get("system_id");

        if (displayId == null || displayId.isEmpty()) {
            return McpResponseFormatter.error("display_id is required");
        }

        log.info("GetATCResult called: {} (includeExempted: {}, session: {})",
                displayId, includeExempted, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;
            String path = "/sap/bc/adt/atc/results/" + displayId;

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("includeExemptedFindings", String.valueOf(includeExempted));
            if (contactPerson != null && !contactPerson.isEmpty()) {
                queryParams.put("contactPerson", contactPerson);
            }

            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, path, queryParams, "application/xml");
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_ATC, displayId + "_result", response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote ATC result to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata using the ATC finding extractor (same format)
            Map<String, Object> metadata = metadataExtractorService.extractAtcMetadata(response);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ATC Result: %s\n", displayId));
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("GetATCResult failed for {}", displayId, e);
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
