package com.sapjco.mcp.handlers.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.service.JcoSessionManager;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for ListSessions tool.
 * Lists all active sessions with their metadata and lock information.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListSessionsHandler implements ToolHandler {

    private final JcoSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .buildTool(
                        "ListSessions",
                        "List all active sessions with their metadata and lock information."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        log.info("ListSessions called");

        try {
            List<JcoSession> sessions = sessionManager.listSessions();

            String message;
            if (sessions.isEmpty()) {
                message = "No active sessions";
            } else {
                message = String.format("Found %d active session%s",
                        sessions.size(), sessions.size() != 1 ? "s" : "");
            }

            // Format response as JSON
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", message);
            response.put("count", sessions.size());

            List<Map<String, Object>> sessionList = new ArrayList<>();
            for (JcoSession session : sessions) {
                Map<String, Object> sessionInfo = new LinkedHashMap<>();
                sessionInfo.put("sessionId", session.getSessionId());
                sessionInfo.put("systemId", session.getSystemId());
                sessionInfo.put("systemHeader", formatSystemHeader(session));
                sessionInfo.put("connectionId", session.getConnectionId());
                sessionInfo.put("type", session.getType());
                sessionInfo.put("createdAt", session.getCreatedAt() != null
                        ? session.getCreatedAt().toString() : null);
                sessionInfo.put("lastUsedAt", session.getLastUsedAt() != null
                        ? session.getLastUsedAt().toString() : null);
                sessionInfo.put("lockCount", session.getLockCount());
                sessionInfo.put("locks", session.getLocks());
                sessionList.add(sessionInfo);
            }
            response.put("sessions", sessionList);

            String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(response);

            return McpResponseFormatter.success(jsonOutput);

        } catch (Exception e) {
            log.error("ListSessions failed", e);
            return McpResponseFormatter.error(e);
        }
    }

    /**
     * Format system header for display.
     */
    private String formatSystemHeader(JcoSession session) {
        return String.format("[%s | %s | %s]",
                session.getSystemId(),
                session.getHost(),
                session.getClient());
    }
}
