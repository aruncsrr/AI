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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP handler for wiki_get_page — retrieve full page content by ID or URL.
 */
@Slf4j
@Component
public class WikiGetPageHandler extends AbstractWikiHandler {

    private static final Pattern PAGE_ID_FROM_URL = Pattern.compile("[?&/](?:pageId=|(pages/))([0-9]+)");

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("page_id", "Confluence page ID (numeric) or full page URL")
                .buildTool("wiki_get_page",
                        "Retrieve full content and metadata of a SAP Wiki (Confluence) page. " +
                        "Accepts a page ID (e.g. \"5888305446\") or a page URL. " +
                        "Returns body (storage format), version, space, and ancestor info.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        CallToolResult validation = validateRequired(args, "page_id");
        if (validation != null) return validation;

        String rawInput = requireString(args, "page_id");
        String pageId = extractPageId(rawInput);

        log.info("wiki_get_page: pageId={}", pageId);

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("expand", "body.storage,version,space,ancestors");

            String response = wikiClient.get("/rest/api/content/" + pageId, params);
            Path filePath = fileStorageService.writeJson("wiki", FileStorageService.CAT_WIKI, "page_" + pageId, response);
            long size = fileStorageService.getByteSize(filePath);

            return fileResponse(filePath, size, "Wiki Page: " + pageId);
        } catch (Exception e) {
            log.error("wiki_get_page failed for: {}", pageId, e);
            return error(e);
        }
    }

    private String extractPageId(String input) {
        // If purely numeric, use as-is
        if (input.matches("\\d+")) return input;
        // Try to extract from URL
        Matcher m = PAGE_ID_FROM_URL.matcher(input);
        if (m.find()) return m.group(2) != null ? m.group(2) : m.group(0);
        return input;
    }
}
