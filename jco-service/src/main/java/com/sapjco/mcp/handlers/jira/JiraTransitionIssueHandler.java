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
 * MCP handler for jira_transition_issue — change the status of a Jira issue.
 */
@Slf4j
@Component
public class JiraTransitionIssueHandler extends AbstractJiraHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("issue_key", "Jira issue key (e.g. APC-1213)")
                .requiredString("transition_id", "Transition ID from jira_get_transitions (e.g. '21')")
                .optionalString("comment", "Optional comment to add when transitioning")
                .buildTool("jira_transition_issue",
                        "Change the status of a Jira issue by executing a workflow transition. " +
                        "Use jira_get_transitions first to find valid transition IDs. Returns success on HTTP 204.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        for (String req : new String[]{"issue_key", "transition_id"}) {
            CallToolResult v = validateRequired(args, req);
            if (v != null) return v;
        }

        String issueKey = requireString(args, "issue_key").toUpperCase();
        String transitionId = requireString(args, "transition_id");
        String comment = optionalString(args, "comment");

        log.info("jira_transition_issue: key={} transition={}", issueKey, transitionId);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("transition", Map.of("id", transitionId));

            if (comment != null && !comment.isBlank()) {
                body.put("update", Map.of(
                        "comment", java.util.List.of(
                                Map.of("add", Map.of("body", comment))
                        )
                ));
            }

            jiraClient.post("/rest/api/2/issue/" + issueKey + "/transitions", mapper.writeValueAsString(body));
            return success("Transitioned " + issueKey + " using transition ID: " + transitionId);
        } catch (Exception e) {
            log.error("jira_transition_issue failed for: {}", issueKey, e);
            return error(e);
        }
    }
}
