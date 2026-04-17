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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * MCP handler for wiki_manage_labels — add or remove labels on a Confluence page.
 */
@Slf4j
@Component
public class WikiManageLabelsHandler extends AbstractWikiHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("page_id", "Confluence page ID")
                .requiredString("action", "Action to perform: \"add\" or \"remove\"")
                .requiredString("labels", "Comma-separated list of label names (e.g. \"abap,hec,transport\")")
                .buildTool("wiki_manage_labels",
                        "Add or remove labels on a SAP Wiki (Confluence) page. " +
                        "Use action=\"add\" to add labels, action=\"remove\" to delete them. " +
                        "Multiple labels can be specified as a comma-separated list.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        for (String required : new String[]{"page_id", "action", "labels"}) {
            CallToolResult v = validateRequired(args, required);
            if (v != null) return v;
        }

        String pageId = requireString(args, "page_id");
        String action = requireString(args, "action").toLowerCase();
        String labelsRaw = requireString(args, "labels");
        List<String> labels = Arrays.stream(labelsRaw.split(","))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();

        if (!action.equals("add") && !action.equals("remove")) {
            return error("action must be \"add\" or \"remove\"");
        }

        log.info("wiki_manage_labels: pageId={} action={} labels={}", pageId, action, labels);

        try {
            StringBuilder result = new StringBuilder();
            result.append("Wiki Labels ").append(action.equals("add") ? "Added" : "Removed")
                    .append(" on Page: ").append(pageId).append("\n");
            result.append("Labels: ").append(labelsRaw).append("\n");

            if (action.equals("add")) {
                StringBuilder jsonArray = new StringBuilder("[");
                for (int i = 0; i < labels.size(); i++) {
                    if (i > 0) jsonArray.append(",");
                    jsonArray.append(String.format("{\"prefix\":\"global\",\"name\":\"%s\"}", labels.get(i)));
                }
                jsonArray.append("]");

                String response = wikiClient.post("/rest/api/content/" + pageId + "/label", jsonArray.toString());
                Path filePath = fileStorageService.writeJson("wiki", FileStorageService.CAT_WIKI,
                        "labels_add_" + pageId, response);
                result.append(String.format("File: %s\n", filePath));
            } else {
                // Remove labels one by one
                for (String label : labels) {
                    try {
                        String encoded = URLEncoder.encode(label, StandardCharsets.UTF_8);
                        wikiClient.delete("/rest/api/content/" + pageId + "/label/" + encoded);
                        result.append("  Removed: ").append(label).append("\n");
                    } catch (Exception ex) {
                        result.append("  Failed to remove ").append(label).append(": ").append(ex.getMessage()).append("\n");
                    }
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            log.error("wiki_manage_labels failed for page: {}", pageId, e);
            return error(e);
        }
    }
}
