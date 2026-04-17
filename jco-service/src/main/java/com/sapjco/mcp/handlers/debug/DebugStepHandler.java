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

import java.util.List;
import java.util.Map;

/**
 * Handler for DebugStep tool.
 * Executes a step operation in a debug session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebugStepHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED)")
                .optionalEnum("operation",
                        "Step operation type (default: stepOver)",
                        List.of("stepOver", "stepInto", "stepReturn"), "stepOver")
                .buildTool(
                        "DebugStep",
                        "Execute a step operation in a debug session. Operations: stepOver (next line), " +
                        "stepInto (enter method), stepReturn (exit method)."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        String operation = args.get("operation") != null ? (String) args.get("operation") : "stepOver";

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }

        log.info("DebugStep called - session: {}, operation: {}", sessionId, operation);

        try {
            JcoSession session = sessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            String responseXml = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                return adtClient.debugStep(
                        destination,
                        operation,
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

            return McpResponseFormatter.success(systemHeader, responseXml);

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("DebugStep failed", e);
            return McpResponseFormatter.error(e);
        }
    }
}
