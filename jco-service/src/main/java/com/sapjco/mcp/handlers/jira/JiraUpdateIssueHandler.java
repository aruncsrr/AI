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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP handler for jira_update_issue — update fields of an existing Jira issue.
 */
@Slf4j
@Component
public class JiraUpdateIssueHandler extends AbstractJiraHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("issue_key", "Jira issue key to update (e.g. APC-1213)")
                .optionalString("summary", "New summary/title")
                .optionalString("description", "New description")
                .optionalString("priority", "New priority name (e.g. High, Medium, Low, Critical)")
                .optionalString("assignee", "New assignee username or account ID")
                .buildTool("jira_update_issue",
                        "Update fields of an existing Jira issue. Provide only the fields to change; " +
                        "omitted fields are left unchanged. Returns HTTP 204 on success.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        CallToolResult validation = validateRequired(args, "issue_key");
        if (validation != null) return validation;

        String issueKey = requireString(args, "issue_key").toUpperCase();
        String summary = optionalString(args, "summary");
        String description = optionalString(args, "description");
        String priority = optionalString(args, "priority");
        String assignee = optionalString(args, "assignee");

        if (summary == null && description == null && priority == null && assignee == null) {
            return error("At least one field (summary, description, priority, assignee) must be provided.");
        }

        log.info("jira_update_issue: key={}", issueKey);

        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            if (summary != null) fields.put("summary", summary);
            if (description != null) fields.put("description", description);
            if (priority != null) fields.put("priority", Map.of("name", priority));
            if (assignee != null) fields.put("assignee", Map.of("name", assignee));

            String body = mapper.writeValueAsString(Map.of("fields", fields));
            jiraClient.put("/rest/api/2/issue/" + issueKey, body);
            return success("Updated: " + issueKey + " — fields changed: " + String.join(", ", fields.keySet()));
        } catch (Exception e) {
            log.error("jira_update_issue failed for: {}", issueKey, e);
            return error(e);
        }
    }
}
