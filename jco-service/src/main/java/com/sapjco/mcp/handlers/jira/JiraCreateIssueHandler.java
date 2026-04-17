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
 * MCP handler for jira_create_issue — create a new Jira issue.
 */
@Slf4j
@Component
public class JiraCreateIssueHandler extends AbstractJiraHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("project_key", "Jira project key (e.g. APC, BEN, ECT)")
                .requiredString("summary", "Issue summary/title")
                .requiredString("issue_type", "Issue type (e.g. Bug, Story, Task, Sub-task)")
                .optionalString("description", "Issue description (plain text)")
                .optionalString("priority", "Priority name (e.g. High, Medium, Low, Critical)")
                .optionalString("assignee", "Assignee username or account ID")
                .buildTool("jira_create_issue",
                        "Create a new Jira issue in the specified project. Returns the created issue key and URL.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        for (String req : new String[]{"project_key", "summary", "issue_type"}) {
            CallToolResult v = validateRequired(args, req);
            if (v != null) return v;
        }

        String projectKey = requireString(args, "project_key").toUpperCase();
        String summary = requireString(args, "summary");
        String issueType = requireString(args, "issue_type");
        String description = optionalString(args, "description");
        String priority = optionalString(args, "priority");
        String assignee = optionalString(args, "assignee");

        log.info("jira_create_issue: project={} type={} summary={}", projectKey, issueType, summary);

        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("project", Map.of("key", projectKey));
            fields.put("summary", summary);
            fields.put("issuetype", Map.of("name", issueType));
            if (description != null) fields.put("description", description);
            if (priority != null) fields.put("priority", Map.of("name", priority));
            if (assignee != null) fields.put("assignee", Map.of("name", assignee));

            String body = mapper.writeValueAsString(Map.of("fields", fields));
            String response = jiraClient.post("/rest/api/2/issue", body);

            Map<?, ?> result = mapper.readValue(response, Map.class);
            String key = (String) result.get("key");
            String url = jiraConfig.getApiUrl() + "/browse/" + key;
            return success("Created: " + key + "\nURL: " + url + "\n" + response);
        } catch (Exception e) {
            log.error("jira_create_issue failed", e);
            return error(e);
        }
    }
}
