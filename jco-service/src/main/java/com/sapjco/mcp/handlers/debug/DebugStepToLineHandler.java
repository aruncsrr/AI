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
 * Handler for DebugStepToLine tool.
 * Steps to a specific line in source code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebugStepToLineHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED)")
                .requiredString("source_uri",
                        "Source URI with line fragment (e.g., \"/sap/bc/adt/oo/classes/zclass/includes/testclasses#start=42\")")
                .optionalEnum("step_type",
                        "Step type: stepRunToLine (execute until) or stepJumpToLine (jump directly) (default: stepRunToLine)",
                        List.of("stepRunToLine", "stepJumpToLine"), "stepRunToLine")
                .buildTool(
                        "DebugStepToLine",
                        "Step to a specific line in source code. stepRunToLine executes until line, " +
                        "stepJumpToLine jumps directly. Line numbers should come from DebugGetStack. " +
                        "Line numbers match exactly what you write - count lines starting from 1, including blank lines."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        String sourceUri = (String) args.get("source_uri");
        String stepType = args.get("step_type") != null ? (String) args.get("step_type") : "stepRunToLine";

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }
        if (sourceUri == null || sourceUri.isEmpty()) {
            return McpResponseFormatter.error("source_uri is required");
        }

        log.info("DebugStepToLine called - session: {}, uri: {}, type: {}", sessionId, sourceUri, stepType);

        try {
            JcoSession session = sessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            String responseXml = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                return adtClient.debugStepToLine(
                        destination,
                        stepType,
                        sourceUri,
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
            sb.append(String.format("Debug Step To Line: %s\n", stepType));
            sb.append(String.format("URI: %s\n", sourceUri));
            sb.append("-".repeat(60)).append("\n\n");
            sb.append(responseXml);

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("DebugStepToLine failed", e);
            return McpResponseFormatter.error(e);
        }
    }
}
