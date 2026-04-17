package com.sapjco.mcp.handlers.session;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.config.SystemConfigLoader.SystemConfig;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateSessionRequest;
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
 * Handler for CreateSession tool.
 * Creates a new editing session for stateful SAP operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateSessionHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .optionalString("system_id",
                        "Optional: SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system. Use ListSystems to see available systems.")
                .buildTool(
                        "CreateSession",
                        "Create a new editing session for stateful SAP operations. " +
                        "IMPORTANT: Always create a session BEFORE using SaveClass, LockObject, ActivateObject, " +
                        "or UnlockObject. Sessions maintain connection state, CSRF tokens, and lock tracking, " +
                        "enabling reliable atomic operations. " +
                        "TYPICAL WORKFLOW: (1) CreateSession → (2) SaveClass with session_id → " +
                        "(3) ActivateObject with session_id → (4) DestroySession."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String systemId = args != null ? (String) args.get("system_id") : null;

        log.info("CreateSession called for system: {}", systemId != null ? systemId : "(default)");

        try {
            // Resolve system configuration
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);
            SystemConfig config = resolved.getConfig();
            String resolvedSystemId = resolved.getSystemId();

            // Build JCo session request
            CreateSessionRequest jcoRequest = new CreateSessionRequest();
            jcoRequest.setSystemId(resolvedSystemId);
            jcoRequest.setHost(config.getEffectiveHost());
            jcoRequest.setSysnr(config.getSysnr());
            jcoRequest.setClient(config.getClient());
            jcoRequest.setSaprouter(config.getSaprouter());

            // Set authentication based on type
            switch (config.getAuthType()) {
                case basic:
                    jcoRequest.setUsername(config.getUsername());
                    jcoRequest.setPassword(config.getPassword());
                    break;
                case snc:
                    // SNC authentication
                    if (config.getSnc() != null) {
                        CreateSessionRequest.SncConfig sncConfig = new CreateSessionRequest.SncConfig();
                        sncConfig.setPartnername(config.getSnc().getPartnername());
                        sncConfig.setQop(config.getSnc().getQop() != null ? config.getSnc().getQop() : 9);
                        sncConfig.setLib(config.getSnc().getLib());
                        jcoRequest.setSnc(sncConfig);
                    }
                    jcoRequest.setEnableHttpSso(true);
                    break;
                case x509:
                    // X.509 - credentials set at HTTP level
                    break;
            }

            // Create the session
            String sessionId = sessionManager.createSession(jcoRequest);

            // Format response
            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolvedSystemId,
                    config.getEffectiveHost(),
                    config.getClient()
            );

            StringBuilder sb = new StringBuilder();
            sb.append("Session created successfully!\n\n");
            sb.append(String.format("Session ID: %s\n", sessionId));
            sb.append(String.format("System: %s\n", resolvedSystemId));
            sb.append(String.format("Auth Type: %s\n", config.getAuthType()));
            sb.append("\nUse this session_id in subsequent operations:\n");
            sb.append("  - SaveClass, SaveInterface, SaveProgram, etc.\n");
            sb.append("  - ActivateObject, CheckSyntax\n");
            sb.append("  - LockObject, UnlockObject (manual locking)\n");
            sb.append("\nRemember to call DestroySession when done to release resources.");

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("CreateSession failed", e);
            return McpResponseFormatter.error(e);
        }
    }
}
