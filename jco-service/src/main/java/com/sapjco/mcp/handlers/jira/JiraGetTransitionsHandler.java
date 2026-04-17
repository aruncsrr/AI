package com.sapjco.mcp.handlers.jira;

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
 * MCP handler for jira_get_transitions — list available status transitions for an issue.
 */
@Slf4j
@Component
public class JiraGetTransitionsHandler extends AbstractJiraHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("issue_key", "Jira issue key (e.g. APC-1213)")
                .buildTool("jira_get_transitions",
                        "Get all available status transitions for a Jira issue. " +
                        "Use this before jira_transition_issue to find valid transition IDs and names.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        CallToolResult validation = validateRequired(args, "issue_key");
        if (validation != null) return validation;

        String issueKey = requireString(args, "issue_key").toUpperCase();
        log.info("jira_get_transitions: key={}", issueKey);

        try {
            String response = jiraClient.get("/rest/api/2/issue/" + issueKey + "/transitions", null);
            return success("Transitions for " + issueKey + ":\n" + response);
        } catch (Exception e) {
            log.error("jira_get_transitions failed for: {}", issueKey, e);
            return error(e);
        }
    }
}
