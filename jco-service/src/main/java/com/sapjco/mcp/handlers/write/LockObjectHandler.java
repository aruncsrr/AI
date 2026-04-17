package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.LockResponse;
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
 * Handler for LockObject tool.
 * Locks an ABAP object for editing via JCo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LockObjectHandler implements ToolHandler {

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
                .buildTool(
                        "LockObject",
                        "Lock an ABAP object for editing via JCo. REQUIRED: Must provide session_id from CreateSession. " +
                        "Returns a lock handle to use with save operations. NOTE: SaveClass automatically handles " +
                        "locking - only use this for manual lock management or non-class objects."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }
        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }

        log.info("LockObject called: {} (type: {}, session: {})", objectName, objectType, sessionId);

        try {
            JcoSession session = sessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            // Map object type to ADT type code
            String adtType = mapObjectType(objectType);

            LockResponse lockResponse = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                return adtClient.lockObject(
                        destination, objectName, adtType,
                        sess.getHttpClient(), sess.getCsrfTokenCache(), sess
                );
            });

            // Track lock in session
            session.addLock(objectName);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    systemId, resolved.getConfig().getEffectiveHost(), resolved.getConfig().getClient()
            );

            // Return structured text response (not formatted message)
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("LOCK_HANDLE=%s\n", lockResponse.getLockHandle()));
            if (lockResponse.getTransportNumber() != null && !lockResponse.getTransportNumber().isEmpty()) {
                sb.append(String.format("CORRNR=%s\n", lockResponse.getTransportNumber()));
            }
            sb.append(String.format("object=%s\n", objectName.toUpperCase()));
            sb.append(String.format("type=%s\n", objectType));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("LockObject failed for {}", objectName, e);
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
