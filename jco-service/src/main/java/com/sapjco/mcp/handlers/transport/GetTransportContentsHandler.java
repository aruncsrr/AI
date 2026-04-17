package com.sapjco.mcp.handlers.transport;

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
import java.util.Map;

/**
 * Handler for GetTransportContents tool.
 * Retrieves all objects contained in a transport request or task via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetTransportContentsHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("transport_id",
                        "Transport request or task ID (e.g., \"NPLK900001\" or \"NPLK900002\")")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID (e.g., \"dev\", \"prod\"). If not specified, uses the default system.")
                .buildTool(
                        "GetTransportContents",
                        "Get all objects contained in a transport request or task. Returns complete object " +
                        "list with PGMID, type, name, and descriptions. For requests, also shows nested tasks " +
                        "with their objects and provides a combined all_objects list. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String transportId = (String) args.get("transport_id");
        String systemId = (String) args.get("system_id");
        String sessionId = (String) args.get("session_id");

        if (transportId == null || transportId.isEmpty()) {
            return McpResponseFormatter.error("transport_id is required");
        }

        log.info("GetTransportContents called: {} (session: {})",
                transportId, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;
            String path = "/sap/bc/adt/cts/transportrequests/" + transportId.toUpperCase();

            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, path, null,
                        "application/vnd.sap.adt.transportorganizer.v1+xml");
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_TRANSPORT, transportId.toUpperCase(), response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote transport contents to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractTransportMetadata(response);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Transport: %s\n", transportId.toUpperCase()));
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("GetTransportContents failed for {}", transportId, e);
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
