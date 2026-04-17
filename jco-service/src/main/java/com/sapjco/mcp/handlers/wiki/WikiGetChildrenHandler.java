package com.sapjco.mcp.handlers.wiki;

import com.sapjco.mcp.handlers.AbstractWikiHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP handler for wiki_get_children — list child pages of a Confluence page.
 */
@Slf4j
@Component
public class WikiGetChildrenHandler extends AbstractWikiHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("page_id", "Parent page ID")
                .optionalNumber("limit", "Maximum results (default: 25, max: 50)")
                .optionalNumber("start", "Pagination offset (default: 0)")
                .buildTool("wiki_get_children",
                        "List direct child pages of a SAP Wiki (Confluence) page. " +
                        "Returns IDs, titles, versions, and URLs of child pages.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        CallToolResult validation = validateRequired(args, "page_id");
        if (validation != null) return validation;

        String pageId = requireString(args, "page_id");
        int limit = Math.min(optionalInt(args, "limit", 25), 50);
        int start = optionalInt(args, "start", 0);

        log.info("wiki_get_children: pageId={} limit={}", pageId, limit);

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("limit", String.valueOf(limit));
            params.put("start", String.valueOf(start));
            params.put("expand", "version");

            String response = wikiClient.get("/rest/api/content/" + pageId + "/child/page", params);
            Path filePath = fileStorageService.writeJson("wiki", FileStorageService.CAT_WIKI,
                    "children_" + pageId, response);
            long size = fileStorageService.getByteSize(filePath);

            return fileResponse(filePath, size, "Wiki Children of Page: " + pageId);
        } catch (Exception e) {
            log.error("wiki_get_children failed for: {}", pageId, e);
            return error(e);
        }
    }
}
