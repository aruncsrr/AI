package com.sapjco.mcp.handlers.debug;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.JcoSessionManager;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler for DebugSetVariable tool.
 * Sets a variable value during debugging.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebugSetVariableHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED)")
                .requiredString("variable_name",
                        "Variable ID from DebugGetVariables (e.g., \"LV_VALUE\")")
                .requiredString("new_value",
                        "New value to set")
                .buildTool(
                        "DebugSetVariable",
                        "Set a variable value during debugging. Use variable ID from DebugGetVariables. " +
                        "Changes take effect immediately."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        String variableName = (String) args.get("variable_name");
        String newValue = (String) args.get("new_value");

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }
        if (variableName == null || variableName.isEmpty()) {
            return McpResponseFormatter.error("variable_name is required");
        }
        if (newValue == null) {
            return McpResponseFormatter.error("new_value is required");
        }

        log.info("DebugSetVariable called - session: {}, var: {}", sessionId, variableName);

        try {
            JcoSession session = sessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            String responseXml = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                return adtClient.debugSetVariableValue(
                        destination,
                        variableName,
                        newValue,
                        sess.getHttpClient(),
                        sess.getCsrfTokenCache(),
                        sess
                );
            });

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    systemId,
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Debug Set Variable: %s = %s\n", variableName, newValue));
            sb.append("-".repeat(60)).append("\n\n");
            sb.append(responseXml);

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("DebugSetVariable failed", e);
            return McpResponseFormatter.error(e);
        }
    }
}
