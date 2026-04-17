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
import java.util.Map;

/**
 * MCP handler for wiki_create_page — create a new Confluence page.
 */
@Slf4j
@Component
public class WikiCreatePageHandler extends AbstractWikiHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("space_key", "Confluence space key (e.g. \"HEC\", \"TMS\")")
                .requiredString("title", "Page title")
                .requiredString("content", "Page body in Confluence Storage Format (HTML-like markup)")
                .optionalString("parent_id", "Parent page ID to create this page as a child")
                .buildTool("wiki_create_page",
                        "Create a new page in SAP Wiki (Confluence). " +
                        "Content must be in Confluence Storage Format. " +
                        "Returns the new page ID, title, and URL.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        for (String required : new String[]{"space_key", "title", "content"}) {
            CallToolResult v = validateRequired(args, required);
            if (v != null) return v;
        }

        String spaceKey = requireString(args, "space_key");
        String title = requireString(args, "title");
        String content = requireString(args, "content");
        String parentId = optionalString(args, "parent_id");

        log.info("wiki_create_page: space={} title={}", spaceKey, title);

        try {
            String ancestors = parentId != null ? String.format(",\"ancestors\":[{\"id\":\"%s\"}]", parentId) : "";
            String body = String.format(
                    "{\"type\":\"page\",\"title\":\"%s\",\"space\":{\"key\":\"%s\"}," +
                    "\"body\":{\"storage\":{\"value\":%s,\"representation\":\"storage\"}}%s}",
                    escapeJson(title), escapeJson(spaceKey), jsonString(content), ancestors);

            String response = wikiClient.post("/rest/api/content", body);
            Path filePath = fileStorageService.writeJson("wiki", FileStorageService.CAT_WIKI,
                    "created_" + fileStorageService.sanitizeFilename(title), response);
            long size = fileStorageService.getByteSize(filePath);

            return fileResponse(filePath, size,
                    "Wiki Page Created: " + title,
                    "Space: " + spaceKey);
        } catch (Exception e) {
            log.error("wiki_create_page failed: {}", title, e);
            return error(e);
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String jsonString(String s) {
        return "\"" + escapeJson(s) + "\"";
    }
}
