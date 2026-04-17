package com.sapjco.mcp.handlers.wiki;

import com.sapjco.mcp.handlers.AbstractWikiHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.WikiSearchExportService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP handler for wiki_search — search Confluence pages via keyword or CQL.
 * Auto-generates a PDF summary and PPTX executive presentation for each search.
 */
@Slf4j
@Component
public class WikiSearchHandler extends AbstractWikiHandler {

    @Autowired
    private WikiSearchExportService exportService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("query", "Search keyword or CQL query (e.g. \"CDD2.0\", \"space=HEC AND text~\\\"pipeline\\\"\")")
                .optionalNumber("limit", "Maximum results to return (default: 25, max: 50)")
                .optionalNumber("start", "Pagination offset (default: 0)")
                .buildTool("wiki_search",
                        "Search SAP Wiki (Confluence) pages by keyword or CQL. Returns page IDs, titles, spaces, " +
                        "URLs, and last-modified dates. Use wiki_get_page to retrieve full page content.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        CallToolResult validation = validateRequired(args, "query");
        if (validation != null) return validation;

        String query = requireString(args, "query");
        int limit = Math.min(optionalInt(args, "limit", 25), 50);
        int start = optionalInt(args, "start", 0);

        log.info("wiki_search: query={} limit={} start={}", query, limit, start);

        try {
            String cql = query.contains("=") || query.contains("~") ? query
                    : "text~\"" + query + "\"";

            Map<String, String> params = new LinkedHashMap<>();
            params.put("cql", cql);
            params.put("limit", String.valueOf(limit));
            params.put("start", String.valueOf(start));
            params.put("expand", "space,version");

            String response = wikiClient.get("/rest/api/content/search", params);
            String filename = "search_" + fileStorageService.sanitizeFilename(query);
            Path filePath = fileStorageService.writeJson("wiki", FileStorageService.CAT_WIKI, filename, response);
            long size = fileStorageService.getByteSize(filePath);

            // Generate PPTX export
            WikiSearchExportService.ExportResult exports = exportService.generate(query, response);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Wiki Search: \"%s\"\nCQL: %s\nResults file: %s (%d bytes)",
                    query, cql, filePath, size));
            if (exports.pptxPath() != null) {
                sb.append("\nPPTX: ").append(exports.pptxPath());
            }

            // Offer the company template for download if it exists
            java.io.File tplFile = new java.io.File(
                    System.getProperty("user.home") + "/Desktop/CDD.pptx");
            if (tplFile.exists()) {
                sb.append("\nTemplate: ").append(tplFile.getAbsolutePath());
            }

            return success(sb.toString());

        } catch (Exception e) {
            log.error("wiki_search failed for: {}", query, e);
            return error(e);
        }
    }
}
