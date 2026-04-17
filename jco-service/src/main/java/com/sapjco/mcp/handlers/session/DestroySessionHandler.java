package com.sapjco.mcp.handlers.session;

import com.sapjco.mcp.handlers.debug.DebugStartSessionHandler;
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
 * Handler for DestroySession tool.
 * Destroys a session and cleans up resources, including debug sessions.
 *
 * <p>Note: This handler has a dependency on {@link DebugStartSessionHandler} for
 * cleaning up pending debug listeners. This cross-package dependency is intentional
 * as session destruction must clean up all associated resources including debug state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DestroySessionHandler implements ToolHandler {

    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id", "Session ID to destroy (from CreateSession)")
                .buildTool(
                        "DestroySession",
                        "Destroy a session and clean up resources. Call this after completing all editing " +
                        "operations to properly release SAP connections. Warns if locks are still active. " +
                        "Automatically cleans up active debug sessions. " +
                        "BEST PRACTICE: Always destroy sessions when done to free resources."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }

        log.info("DestroySession called for session: {}", sessionId);

        StringBuilder sb = new StringBuilder();
        boolean debugCleanedUp = false;
        String debugCleanupError = null;

        try {
            // Get session before destroying to check for debug state
            JcoSession session = sessionManager.getSession(sessionId);

            // Clean up debug session if active (uses helper from JcoSession)
            Map<String, String> deleteParams = session.buildDebugCleanupParams();
            if (deleteParams != null) {
                log.info("Cleaning up active debug session: terminalId={}, ideId={}",
                        session.getDebugTerminalId(), session.getDebugIdeId());
                try {
                    sessionManager.executeInContext(sessionId, (dest, sess) -> {
                        adtClient.debugDeleteBreakpointRest(dest, deleteParams,
                                sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
                        return null;
                    });

                    session.clearDebugState();
                    debugCleanedUp = true;
                    log.info("Debug cleanup successful");
                } catch (Exception e) {
                    debugCleanupError = e.getMessage();
                    log.warn("Debug cleanup failed (continuing with session destroy): {}", e.getMessage());
                }
            }

            // Also clean up any pending listeners for this session
            if (DebugStartSessionHandler.cleanupPendingListener(sessionId)) {
                log.info("Removed pending debug listener for session");
            }

            // Now destroy the session
            sessionManager.destroySession(sessionId);

            sb.append("Session destroyed successfully!\n\n");
            sb.append(String.format("Session ID: %s\n", sessionId));
            sb.append(String.format("Active sessions remaining: %d\n", sessionManager.getActiveSessionCount()));

            if (debugCleanedUp) {
                sb.append("\nDebug session cleaned up automatically.");
            } else if (debugCleanupError != null) {
                sb.append("\nNote: Debug cleanup failed: ").append(debugCleanupError);
                sb.append("\nYou may need to wait before starting a new debug session.");
            }

            return McpResponseFormatter.success(sb.toString());

        } catch (IllegalArgumentException e) {
            log.warn("Session not found: {}", sessionId);
            return McpResponseFormatter.error("Session not found: " + sessionId);

        } catch (Exception e) {
            log.error("DestroySession failed", e);
            return McpResponseFormatter.error(e);
        }
    }
}
