package com.sapjco.mcp.handlers.write;

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
 * Handler for UnlockObject tool.
 * Unlocks an ABAP object after editing via JCo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnlockObjectHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED - write operations only work via JCo)")
                .requiredString("object_name", "Name of the ABAP object")
                .requiredEnum("object_type",
                        "Type of object: class, interface, program, function_group, include",
                        List.of("class", "interface", "program", "function_group", "include"))
                .requiredString("lock_handle", "Lock handle obtained from LockObject")
                .buildTool(
                        "UnlockObject",
                        "Unlock an ABAP object after editing via JCo. REQUIRED: Must provide session_id and lock_handle " +
                        "from LockObject. NOTE: SaveClass automatically handles unlocking - only use this for manual " +
                        "lock management."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        String lockHandle = (String) args.get("lock_handle");

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }
        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }
        if (lockHandle == null || lockHandle.isEmpty()) {
            return McpResponseFormatter.error("lock_handle is required");
        }

        log.info("UnlockObject called: {} (type: {}, session: {})", objectName, objectType, sessionId);

        try {
            JcoSession session = sessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            String adtType = mapObjectType(objectType);

            // Execute unlock and capture raw response
            String rawResponse = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                return adtClient.unlockObject(
                        destination, objectName, adtType, lockHandle,
                        sess.getHttpClient(), sess.getCsrfTokenCache(), sess
                );
            });

            // Remove lock from session tracking
            session.removeLock(objectName);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    systemId, resolved.getConfig().getEffectiveHost(), resolved.getConfig().getClient()
            );

            // Return raw XML response
            return McpResponseFormatter.success(systemHeader, rawResponse);

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("UnlockObject failed for {}", objectName, e);
            return McpResponseFormatter.error(e);
        }
    }

    private String mapObjectType(String objectType) {
        switch (objectType.toLowerCase()) {
            case "class": return "CLASS";
            case "interface": return "INTF";
            case "program": return "PROG";
            case "function_group": return "FUGR";
            case "include": return "INCL";
            default: return objectType.toUpperCase();
        }
    }
}
