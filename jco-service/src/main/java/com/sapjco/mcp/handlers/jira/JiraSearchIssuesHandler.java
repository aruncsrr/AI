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
 * MCP handler for jira_search_issues — search Jira using JQL.
 */
@Slf4j
@Component
public class JiraSearchIssuesHandler extends AbstractJiraHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("jql", "JQL query string (e.g. 'project = APC AND status = Open ORDER BY created DESC')")
                .optionalString("max_results", "Maximum number of results to return (default: 50, max: 100)")
                .optionalString("fields", "Comma-separated list of fields to return (default: summary,status,assignee,priority,created,updated)")
                .buildTool("jira_search_issues",
                        "Search Jira issues using JQL (Jira Query Language). Returns matching issues " +
                        "with their key, summary, status, assignee, and priority.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        CallToolResult validation = validateRequired(args, "jql");
        if (validation != null) return validation;

        String jql = requireString(args, "jql");
        int maxResults = Math.min(optionalInt(args, "max_results", 50), 100);
        String fields = optionalString(args, "fields", "summary,status,assignee,priority,issuetype,created,updated,description");

        log.info("jira_search_issues: jql={} maxResults={}", jql, maxResults);

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("jql", jql);
            params.put("maxResults", String.valueOf(maxResults));
            params.put("fields", fields);

            String response = jiraClient.get("/rest/api/2/search", params);
            return success("JQL: " + jql + "\nMax: " + maxResults + "\n" + response);
        } catch (Exception e) {
            log.error("jira_search_issues failed for JQL: {}", jql, e);
            return error(e);
        }
    }
}
