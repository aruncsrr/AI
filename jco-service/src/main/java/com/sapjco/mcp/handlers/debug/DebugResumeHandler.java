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
 * Handler for DebugResume tool.
 * Resumes execution in a debug session and detects program termination.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebugResumeHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED)")
                .buildTool(
                        "DebugResume",
                        "Resume execution in a debug session. Continues until program ends or hits another breakpoint."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }

        log.info("DebugResume called - session: {}", sessionId);

        try {
            JcoSession session = sessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            String responseXml = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                return adtClient.debugResume(
                        destination,
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

            // Check if program terminated by trying to get stack
            boolean programTerminated = false;
            String terminationReason = null;
            try {
                // Brief delay to let the program finish if it's going to
                Thread.sleep(200);

                sessionManager.executeInContext(sessionId, (destination, sess) -> {
                    return adtClient.debugGetStack(
                            destination,
                            sess.getHttpClient(),
                            sess.getCsrfTokenCache(),
                            sess
                    );
                });
                log.info("Stack still accessible - program may be running or paused at another breakpoint");
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("CM_NO_DATA_RECEIVED") ||
                                    msg.contains("connection closed") ||
                                    msg.contains("no debug session") ||
                                    msg.contains("RFC call failed"))) {
                    programTerminated = true;
                    terminationReason = msg;
                    log.info("Program terminated - detaching debugger and clearing debug state");

                    // CRITICAL: First detach from debugger to clear SAP singleton ref_session
                    // Without this, CL_TPDAPI_SERVICE still holds a reference and will
                    // reject new attach attempts with "Debuggee already attached" error.
                    try {
                        sessionManager.executeInContext(sessionId, (dest, sess) -> {
                            adtClient.debugDetach(dest, sess.getHttpClient(),
                                    sess.getCsrfTokenCache(), sess);
                            return null;
                        });
                        log.info("✓ Detached from debugger after program termination");
                    } catch (Exception detachEx) {
                        // Non-fatal - debugger may already be detached
                        log.debug("Debugger detach after termination (non-fatal): {}", detachEx.getMessage());
                    }

                    // Clear local debug state since session is over
                    session.clearDebugState();
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Execution Resumed\n\n");

            if (programTerminated) {
                sb.append("Program Terminated\n\n");
                sb.append("The debugged program has completed execution.\n");
                sb.append("The debug session has ended automatically.\n\n");
                sb.append("Next steps:\n");
                sb.append("  • Call DestroySession(session_id) to clean up the JCo session\n");
                sb.append("  • Or start a new debug session with DebugStartSession\n");
                if (terminationReason != null) {
                    sb.append("\nTermination detected via: ").append(
                            terminationReason.length() > 100
                                    ? terminationReason.substring(0, 100) + "..."
                                    : terminationReason);
                }
            } else {
                sb.append("Process will continue until:\n");
                sb.append("  • Next breakpoint is hit\n");
                sb.append("  • Program completes\n");
                sb.append("  • Error/exception occurs\n");
            }

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("DebugResume failed", e);

            // Check if the error itself indicates program termination
            String msg = e.getMessage();
            if (msg != null && (msg.contains("CM_NO_DATA_RECEIVED") ||
                                msg.contains("connection closed"))) {
                return McpResponseFormatter.error(
                        "Debug session ended - the debugged program has likely completed.\n\n" +
                        "Call DestroySession to clean up, then start a new debug session if needed.\n\n" +
                        "Error: " + msg);
            }

            return McpResponseFormatter.error(e);
        }
    }
}
