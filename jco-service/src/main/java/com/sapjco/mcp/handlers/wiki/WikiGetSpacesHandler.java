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
 * MCP handler for wiki_get_spaces — list available Confluence spaces.
 */
@Slf4j
@Component
public class WikiGetSpacesHandler extends AbstractWikiHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .optionalString("type", "Filter by space type: \"global\" or \"personal\" (default: all)")
                .optionalNumber("limit", "Maximum results (default: 25, max: 50)")
                .optionalNumber("start", "Pagination offset (default: 0)")
                .buildTool("wiki_get_spaces",
                        "List available SAP Wiki (Confluence) spaces. " +
                        "Returns space keys, names, types, and descriptions. " +
                        "Use the space key in wiki_create_page or wiki_search CQL queries.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        String type = optionalString(args, "type");
        int limit = Math.min(optionalInt(args, "limit", 25), 50);
        int start = optionalInt(args, "start", 0);

        log.info("wiki_get_spaces: type={} limit={}", type, limit);

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("limit", String.valueOf(limit));
            params.put("start", String.valueOf(start));
            params.put("expand", "description.plain");
            if (type != null && !type.isBlank()) {
                params.put("type", type);
            }

            String response = wikiClient.get("/rest/api/space", params);
            Path filePath = fileStorageService.writeJson("wiki", FileStorageService.CAT_WIKI,
                    "spaces_" + System.currentTimeMillis(), response);
            long size = fileStorageService.getByteSize(filePath);

            return fileResponse(filePath, size, "Wiki Spaces" + (type != null ? " (type: " + type + ")" : ""));
        } catch (Exception e) {
            log.error("wiki_get_spaces failed", e);
            return error(e);
        }
    }
}
