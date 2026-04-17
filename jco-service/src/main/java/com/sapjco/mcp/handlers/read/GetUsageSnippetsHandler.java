package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetUsageSnippets tool.
 * Fetches source code snippets for where-used results (lazy loading phase 2) via RFC proxy.
 */
@Slf4j
@Component
public class GetUsageSnippetsHandler extends AbstractReadHandler {

    private static final int MAX_IDENTIFIERS = 50;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredArray("object_identifiers",
                        "Array of objectIdentifier strings from GetWhereUsed results. Maximum 50 identifiers per request.")
                .optionalBoolean("definitions",
                        "Include definitions in snippets (default: false)")
                .optionalBoolean("elements",
                        "Include elements in snippets (default: true)")
                .optionalBoolean("indirect_references",
                        "Include indirect references (default: false)")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetUsageSnippets",
                        "Get source code snippets for where-used results. This is the second phase of lazy loading: " +
                        "(1) GetWhereUsed returns lightweight object references with objectIdentifiers, " +
                        "(2) GetUsageSnippets fetches detailed source locations on-demand. " +
                        "Use this to get actual code context for specific usage references. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    @SuppressWarnings("unchecked")
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        List<String> objectIdentifiers = (List<String>) args.get("object_identifiers");
        boolean definitions = optionalBoolean(args, "definitions", false);
        boolean elements = optionalBoolean(args, "elements", true);
        boolean indirectReferences = optionalBoolean(args, "indirect_references", false);

        if (objectIdentifiers == null || objectIdentifiers.isEmpty()) {
            return error("object_identifiers is required and must not be empty");
        }
        if (objectIdentifiers.size() > MAX_IDENTIFIERS) {
            return error(String.format("Maximum %d object identifiers allowed per request, got %d",
                    MAX_IDENTIFIERS, objectIdentifiers.size()));
        }

        log.info("GetUsageSnippets called: {} identifiers (session: {})",
                objectIdentifiers.size(),
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, "snippets", (sessionId, resolved) -> {
            // Build snippets request body
            String requestBody = buildSnippetsRequest(objectIdentifiers, definitions, elements, indirectReferences);

            String path = "/sap/bc/adt/repository/informationsystem/usageSnippets";
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessPostViaRfc(dest, session, path, null, requestBody,
                            "application/vnd.sap.adt.repository.usagesnippets.request.v1+xml",
                            "application/vnd.sap.adt.repository.usagesnippets.result.v1+xml").getBody());

            // Write results to file
            String systemFileId = getSystemFileId(resolved);
            String identifiersHash = Integer.toHexString(objectIdentifiers.hashCode());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_USAGE_SNIPPETS, "snippets_" + identifiersHash, response);

            log.info("Wrote usage snippets to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Build summary response
            return formatFileResponse(resolved, filePath, response, FileStorageService.EXT_XML,
                    "Usage Snippets",
                    String.format("Identifiers: %d processed", objectIdentifiers.size()));
        });
    }

    private String buildSnippetsRequest(List<String> objectIdentifiers,
                                         boolean definitions, boolean elements, boolean indirectReferences) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"ASCII\"?>\n");
        xml.append("<usagereferences:usageSnippetRequest xmlns:usagereferences=\"http://www.sap.com/adt/ris/usageReferences\">\n");

        // Object identifiers WRAPPED in container element
        xml.append("  <usagereferences:objectIdentifiers>\n");
        for (String identifier : objectIdentifiers) {
            xml.append("    <usagereferences:objectIdentifier>")
                    .append(escapeXml(identifier))
                    .append("</usagereferences:objectIdentifier>\n");
        }
        xml.append("  </usagereferences:objectIdentifiers>\n");

        // Use "grade" instead of "options"
        xml.append("  <usagereferences:grade");
        xml.append(" definitions=\"").append(definitions).append("\"");
        xml.append(" elements=\"").append(elements).append("\"");
        xml.append(" indirectReferences=\"").append(indirectReferences).append("\"");
        xml.append("/>\n");

        xml.append("</usagereferences:usageSnippetRequest>");
        return xml.toString();
    }

    private String escapeXml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
