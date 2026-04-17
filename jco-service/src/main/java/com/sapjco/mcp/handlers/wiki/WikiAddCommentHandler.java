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
 * MCP handler for wiki_add_comment — add a comment to a Confluence page.
 */
@Slf4j
@Component
public class WikiAddCommentHandler extends AbstractWikiHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("page_id", "Confluence page ID to comment on")
                .requiredString("content", "Comment body in Confluence Storage Format")
                .buildTool("wiki_add_comment",
                        "Add a comment to a SAP Wiki (Confluence) page. " +
                        "Content must be in Confluence Storage Format. " +
                        "Returns the new comment ID and creation timestamp.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        for (String required : new String[]{"page_id", "content"}) {
            CallToolResult v = validateRequired(args, required);
            if (v != null) return v;
        }

        String pageId = requireString(args, "page_id");
        String content = requireString(args, "content");

        log.info("wiki_add_comment: pageId={}", pageId);

        try {
            String body = String.format(
                    "{\"type\":\"comment\",\"container\":{\"id\":\"%s\",\"type\":\"page\"}," +
                    "\"body\":{\"storage\":{\"value\":%s,\"representation\":\"storage\"}}}",
                    pageId, jsonString(content));

            String response = wikiClient.post("/rest/api/content", body);
            Path filePath = fileStorageService.writeJson("wiki", FileStorageService.CAT_WIKI,
                    "comment_" + pageId + "_" + System.currentTimeMillis(), response);
            long size = fileStorageService.getByteSize(filePath);

            return fileResponse(filePath, size, "Wiki Comment Added to Page: " + pageId);
        } catch (Exception e) {
            log.error("wiki_add_comment failed for page: {}", pageId, e);
            return error(e);
        }
    }

    private String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
