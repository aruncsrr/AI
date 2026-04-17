package com.sapjco.mcp.handlers.debug;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.handlers.debug.DebugStartSessionHandler.AttachResult;
import com.sapjco.mcp.handlers.debug.DebugStartSessionHandler.DebugListenerState;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handler for DebugWaitForBreakpoint tool.
 *
 * <p>Phase 2 of the split debug workflow for non-testclasses includes.
 * Waits for a background listener (started by DebugStartSession) to complete
 * the attach operation and returns a ready-to-use debug session.
 *
 * <p>Option 5 Fix: The listener future now includes attach + settings operations
 * performed immediately after breakpoint hit. This ensures the attach happens
 * in the same JCo/SAP session context, avoiding "noSessionAttached" errors.
 *
 * <p>Typical workflow:
 * <ol>
 *   <li>DebugStartSession(include_type: "implementations") → returns immediately, starts background listener</li>
 *   <li>RunAbapUnit(debug_mode: true) → triggers code that hits the breakpoint</li>
 *   <li>DebugWaitForBreakpoint(session_id) → waits for listener+attach to complete, verifies, returns session</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebugWaitForBreakpointHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED - must be the same session_id " +
                        "used in the preceding DebugStartSession call)")
                .optionalNumber("timeout_seconds",
                        "Timeout in seconds for waiting for breakpoint hit (default: 120). " +
                        "The actual wait time is limited by the remaining time on the background listener.")
                .buildTool(
                        "DebugWaitForBreakpoint",
                        "Wait for a breakpoint to be hit after DebugStartSession was called with a " +
                        "non-testclasses include_type. Blocks until the background listener detects a " +
                        "breakpoint hit, then attaches to the debuggee and returns the debug session info. " +
                        "Call this AFTER triggering execution (e.g., RunAbapUnit with debug_mode=true)."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        int timeoutSeconds = args.get("timeout_seconds") != null
                ? ((Number) args.get("timeout_seconds")).intValue()
                : 120;

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }

        log.info("DebugWaitForBreakpoint called: session={}, timeout={}s", sessionId, timeoutSeconds);

        try {
            // Verify session exists
            JcoSession session = jcoSessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    systemId,
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Look up pending listener
            DebugListenerState state = DebugStartSessionHandler.getPendingListener(sessionId);
            if (state == null) {
                return McpResponseFormatter.error(
                        "No pending debug listener found for session: " + sessionId + "\n\n" +
                        "Make sure you called DebugStartSession with a non-testclasses include_type first.\n" +
                        "The listener may have already been consumed or timed out.");
            }

            // ═══════════════════════════════════════════════════════════
            // Clean up any previous debug attachment before waiting
            // This prevents "Debuggee already attached" errors from lingering sessions
            // ═══════════════════════════════════════════════════════════
            Map<String, String> cleanupParams = session.buildDebugCleanupParams();
            if (cleanupParams != null) {
                log.info("Cleaning up previous debug attachment: terminalId={}, ideId={}",
                        session.getDebugTerminalId(), session.getDebugIdeId());
                try {
                    jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                        adtClient.debugDeleteBreakpointRest(dest, cleanupParams,
                                sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
                        return null;
                    });
                    log.info("✓ Previous debug attachment cleaned up");
                } catch (Exception e) {
                    log.debug("Previous debug cleanup (non-fatal): {}", e.getMessage());
                }
                session.clearDebugState();
            }

            // ═══════════════════════════════════════════════════════════
            // Wait for listener + attach to complete (Option 5 fix)
            // The future now returns AttachResult which includes the attach
            // ═══════════════════════════════════════════════════════════
            log.info("Waiting for breakpoint hit + attach (timeout: {}s)...", timeoutSeconds);

            AttachResult attachResult;
            try {
                attachResult = state.listenerFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                return McpResponseFormatter.error(
                        "Debug listener timed out after " + timeoutSeconds + " seconds.\n\n" +
                        "The breakpoint was not hit. Possible reasons:\n" +
                        "  - The triggered code does not execute the breakpoint line\n" +
                        "  - The execution has not been triggered yet\n" +
                        "  - The trigger is still in progress\n\n" +
                        "You can call DebugWaitForBreakpoint again to continue waiting.");
            } catch (Exception e) {
                // Clean up on failure
                DebugStartSessionHandler.removePendingListener(sessionId);
                String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                return McpResponseFormatter.error("Debug listener failed: " + msg);
            }

            // Clean up pending listener (success path)
            DebugStartSessionHandler.removePendingListener(sessionId);

            // Check attach result for errors
            if (attachResult.error != null) {
                return McpResponseFormatter.error(
                        "Debug attach failed in listener thread.\n\n" +
                        "Error: " + attachResult.error.getMessage());
            }

            String debuggeeId = attachResult.debuggeeId;
            if (debuggeeId == null || debuggeeId.isEmpty()) {
                return McpResponseFormatter.error(
                        "No debuggee detected from listener response.\n" +
                        "The breakpoint may not have been hit.\n\n" +
                        "Listener response:\n" + attachResult.listenerResponse);
            }

            log.info("Debuggee detected and attached by listener: {}", debuggeeId);

            // ═══════════════════════════════════════════════════════════
            // Verify we're actually at a breakpoint by getting the stack
            // This confirms the attach in the listener thread was successful
            // ═══════════════════════════════════════════════════════════
            try {
                jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                    return adtClient.debugGetStack(dest, sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
                });
                log.info("Stack retrieved - confirmed at breakpoint");
            } catch (Exception stackEx) {
                return McpResponseFormatter.error(
                        "Failed to verify breakpoint position after attach.\n" +
                        "The debuggee may have terminated or the session is invalid.\n\n" +
                        "Error: " + stackEx.getMessage());
            }

            // Store debug state in session for auto-cleanup
            session.setDebugTerminalId(state.terminalId);
            session.setDebugIdeId(state.ideId);
            session.setDebugRequestUser(state.requestUser);

            // Format success response
            StringBuilder sb = new StringBuilder();
            sb.append("Debug Session Ready!\n\n");
            sb.append("Session Information:\n");
            sb.append(String.format("  Session ID: %s\n", sessionId));
            sb.append(String.format("  Debuggee ID: %s\n", debuggeeId));
            sb.append(String.format("  Terminal ID: %s\n", state.terminalId));
            sb.append(String.format("  IDE ID: %s\n", state.ideId));
            sb.append("\n");
            sb.append("Breakpoint Location:\n");
            sb.append(String.format("  Object: %s\n", state.objectName));
            sb.append(String.format("  URI: %s\n", state.objectUri));
            sb.append(String.format("  Line: %d\n", state.lineNumber));
            sb.append("\n");
            sb.append("Status: Attached and paused at breakpoint\n\n");
            sb.append("Next Steps - Use these commands with session_id=").append(sessionId).append(":\n\n");
            sb.append("  1. DebugGetStack(session_id)\n");
            sb.append("     → See current execution position and call stack\n\n");
            sb.append("  2. DebugGetVariables(session_id, \"@LOCALS\")\n");
            sb.append("     → Inspect local variables at current position\n\n");
            sb.append("  3. DebugStep(session_id, \"stepOver\")\n");
            sb.append("     → Execute next line (options: stepOver, stepInto, stepReturn)\n\n");
            sb.append("  4. DebugResume(session_id)\n");
            sb.append("     → Continue execution until program ends or next breakpoint\n\n");
            sb.append("  5. DestroySession(session_id) when done\n");
            sb.append("     → Clean up debug session (IMPORTANT!)");

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("DebugWaitForBreakpoint failed", e);
            return McpResponseFormatter.error(e);
        }
    }
}
