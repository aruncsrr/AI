package com.sapjco.mcp.handlers.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapjco.mcp.handlers.AbstractJiraHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP handler for jira_add_comment — add a comment to a Jira issue.
 */
@Slf4j
@Component
public class JiraAddCommentHandler extends AbstractJiraHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("issue_key", "Jira issue key (e.g. APC-1213)")
                .requiredString("body", "Comment text to add")
                .buildTool("jira_add_comment",
                        "Add a comment to an existing Jira issue. Returns the created comment ID and timestamp.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        for (String req : new String[]{"issue_key", "body"}) {
            CallToolResult v = validateRequired(args, req);
            if (v != null) return v;
        }

        String issueKey = requireString(args, "issue_key").toUpperCase();
        String body = requireString(args, "body");

        log.info("jira_add_comment: key={}", issueKey);

        try {
            String json = mapper.writeValueAsString(Map.of("body", body));
            String response = jiraClient.post("/rest/api/2/issue/" + issueKey + "/comment", json);
            return success("Comment added to " + issueKey + "\n" + response);
        } catch (Exception e) {
            log.error("jira_add_comment failed for: {}", issueKey, e);
            return error(e);
        }
    }
}
