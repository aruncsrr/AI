package com.sapjco.mcp.handlers.transport;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.JcoSessionManager;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for ListTransportRequests tool.
 * Lists transport requests for a user with optional filters via RFC proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListTransportRequestsHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .optionalString("user",
                        "Optional: Filter by username (e.g., \"D052860\"). If not specified, " +
                        "returns all transport requests.")
                .optionalEnum("trstatus",
                        "Optional: Filter by status. D=Modifiable, R=Released, L=Protected",
                        List.of("D", "R", "L"))
                .optionalEnum("trfunction",
                        "Optional: Filter by transport function. K=Workbench request, W=Customizing request",
                        List.of("K", "W"))
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "ListTransportRequests",
                        "List transport requests for a user. Useful for finding existing transport requests " +
                        "to use for object modifications. Optionally filter by user, status, or type."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String user = (String) args.get("user");
        String trstatus = (String) args.get("trstatus");
        String trfunction = (String) args.get("trfunction");
        String sessionId = (String) args.get("session_id");

        log.info("ListTransportRequests called: user={}, status={}, function={}, session={}",
                user, trstatus, trfunction, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(null);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // Build query parameters (use correct SAP parameter names)
            Map<String, String> queryParams = new HashMap<>();
            if (user != null && !user.isEmpty()) {
                queryParams.put("user", user);
            }
            if (trstatus != null && !trstatus.isEmpty()) {
                queryParams.put("requestStatus", trstatus);
            }
            if (trfunction != null && !trfunction.isEmpty()) {
                queryParams.put("requestType", trfunction);
            }

            String path = "/sap/bc/adt/cts/transportrequests";
            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, path,
                        queryParams.isEmpty() ? null : queryParams,
                        "application/vnd.sap.adt.transportorganizertree.v1+xml");
            });

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            return McpResponseFormatter.success(systemHeader, response);

        } catch (Exception e) {
            log.error("ListTransportRequests failed", e);
            return McpResponseFormatter.error(e);
        } finally {
            if (tempSessionId != null) {
                try {
                    jcoSessionManager.destroySession(tempSessionId);
                } catch (Exception e) {
                    log.warn("Failed to destroy temp session: {}", e.getMessage());
                }
            }
        }
    }
}
