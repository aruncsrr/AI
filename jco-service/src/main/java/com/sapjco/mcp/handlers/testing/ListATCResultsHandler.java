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
 * Handler for ListATCResults tool.
 * Browses persistent ATC results with aggregated counts (the ATC Result Browser).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListATCResultsHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .optionalString("created_by",
                        "SAP user who triggered the run. Default: \"*\" (all users)")
                .optionalString("contact_person",
                        "Responsible developer filter")
                .optionalNumber("age_min",
                        "Minimum age in days (0=today). Default: 0")
                .optionalNumber("age_max",
                        "Maximum age in days. Default: 14")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID (e.g., \"dev\", \"prod\")")
                .buildTool(
                        "ListATCResults",
                        "Browse persistent ATC results stored on the system (the ATC Result Browser). " +
                        "Returns historical results from scheduled and manual ATC runs with aggregated finding " +
                        "counts by priority. Use GetATCResult to drill into a specific result's findings. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String createdBy = args.get("created_by") != null ? (String) args.get("created_by") : "*";
        String contactPerson = (String) args.get("contact_person");
        int ageMin = args.get("age_min") != null ? ((Number) args.get("age_min")).intValue() : 0;
        int ageMax = args.get("age_max") != null ? ((Number) args.get("age_max")).intValue() : 14;
        String sessionId = (String) args.get("session_id");
        String systemId = (String) args.get("system_id");

        log.info("ListATCResults called (createdBy: {}, ageMin: {}, ageMax: {}, session: {})",
                createdBy, ageMin, ageMax, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;
            String path = "/sap/bc/adt/atc/results";

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("createdBy", createdBy);
            queryParams.put("ageMin", String.valueOf(ageMin));
            queryParams.put("ageMax", String.valueOf(ageMax));
            if (contactPerson != null && !contactPerson.isEmpty()) {
                queryParams.put("contactPerson", contactPerson);
            }

            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, path, queryParams, "application/xml");
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            String sanitizedUser = fileStorageService.sanitizeFilename(createdBy);
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_ATC, sanitizedUser + "_results", response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote ATC results list to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extract("ListATCResults", response);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ATC Results (createdBy: %s, age: %d-%d days)\n", createdBy, ageMin, ageMax));
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("ListATCResults failed", e);
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
