package com.sapjco.mcp.handlers.jira;

import com.sapjco.mcp.handlers.AbstractJiraHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP handler for jira_get_issue — fetch full details of a Jira issue by key.
 */
@Slf4j
@Component
public class JiraGetIssueHandler extends AbstractJiraHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("issue_key", "Jira issue key (e.g. APC-1213, BEN-456)")
                .buildTool("jira_get_issue",
                        "Get full details of a Jira issue by its key. Returns summary, status, " +
                        "assignee, reporter, priority, description, comments, and all fields.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        CallToolResult validation = validateRequired(args, "issue_key");
        if (validation != null) return validation;

        String issueKey = requireString(args, "issue_key").toUpperCase();
        log.info("jira_get_issue: key={}", issueKey);

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("expand", "renderedFields,names,changelog");

            String response = jiraClient.get("/rest/api/2/issue/" + issueKey, params);
            return success("Issue: " + issueKey + "\n" + response);
        } catch (Exception e) {
            log.error("jira_get_issue failed for: {}", issueKey, e);
            return error(e);
        }
    }
}
