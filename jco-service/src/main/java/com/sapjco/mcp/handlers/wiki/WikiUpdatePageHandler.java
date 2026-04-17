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
 * MCP handler for wiki_update_page — update an existing Confluence page.
 * Auto-increments the version number if not provided.
 */
@Slf4j
@Component
public class WikiUpdatePageHandler extends AbstractWikiHandler {

    private static final Pattern VERSION_PATTERN = Pattern.compile("\"number\"\\s*:\\s*(\\d+)");

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("page_id", "Confluence page ID")
                .requiredString("title", "New page title")
                .requiredString("content", "New page body in Confluence Storage Format")
                .optionalNumber("version", "Version number (auto-incremented from current if omitted)")
                .buildTool("wiki_update_page",
                        "Update an existing SAP Wiki (Confluence) page. " +
                        "If version is not provided, the current version is fetched and incremented automatically. " +
                        "Content must be in Confluence Storage Format.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        for (String required : new String[]{"page_id", "title", "content"}) {
            CallToolResult v = validateRequired(args, required);
            if (v != null) return v;
        }

        String pageId = requireString(args, "page_id");
        String title = requireString(args, "title");
        String content = requireString(args, "content");
        int version = optionalInt(args, "version", 0);

        log.info("wiki_update_page: pageId={} title={}", pageId, title);

        try {
            // Auto-fetch current version if not provided
            if (version == 0) {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("expand", "version");
                String current = wikiClient.get("/rest/api/content/" + pageId, params);
                version = extractVersion(current) + 1;
                log.debug("wiki_update_page: auto-version={}", version);
            }

            String body = String.format(
                    "{\"type\":\"page\",\"title\":\"%s\"," +
                    "\"body\":{\"storage\":{\"value\":%s,\"representation\":\"storage\"}}," +
                    "\"version\":{\"number\":%d}}",
                    escapeJson(title), jsonString(content), version);

            String response = wikiClient.put("/rest/api/content/" + pageId, body);
            Path filePath = fileStorageService.writeJson("wiki", FileStorageService.CAT_WIKI,
                    "updated_" + pageId, response);
            long size = fileStorageService.getByteSize(filePath);

            return fileResponse(filePath, size,
                    "Wiki Page Updated: " + pageId,
                    "Title: " + title,
                    "Version: " + version);
        } catch (Exception e) {
            log.error("wiki_update_page failed for: {}", pageId, e);
            return error(e);
        }
    }

    private int extractVersion(String json) {
        Matcher m = VERSION_PATTERN.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String jsonString(String s) {
        return "\"" + escapeJson(s) + "\"";
    }
}
