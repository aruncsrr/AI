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
 * Handler for ActivateObject tool.
 * Activates an ABAP object via ADT REST API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivateObjectHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name", "Name of the ABAP object")
                .requiredEnum("object_type", "Type of object: class, interface, program, function_group, function_module, include, behavior_definition, service_definition, service_binding",
                        List.of("class", "interface", "program", "function_group", "function_module", "include", "behavior_definition", "service_definition", "service_binding"))
                .optionalString("group_name",
                        "Function group name. Required when object_type is function_module.")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically for proper cookie and CSRF token management.")
                .optionalBoolean("force", "Force activation despite certain errors (default: false)", false)
                .buildTool(
                        "ActivateObject",
                        "Activate an ABAP object via ADT REST API. Does NOT require a session - can activate " +
                        "any saved but inactive object directly. For function modules, provide group_name and " +
                        "set object_type to \"function_module\". Use this as an alternative to ActivateObject " +
                        "when JCo is unavailable or for simpler activation workflows."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        String groupName = (String) args.get("group_name");
        String sessionId = (String) args.get("session_id");
        Boolean force = args.get("force") != null ? (Boolean) args.get("force") : false;

        // Validate required parameters
        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }

        // Validate group_name is provided for function_module
        boolean isFunctionModule = "function_module".equals(objectType);
        if (isFunctionModule && (groupName == null || groupName.isEmpty())) {
            return McpResponseFormatter.error("group_name is required when object_type is function_module");
        }

        log.info("ActivateObject called: {} (type: {}, group: {}, session: {}, force: {})",
                objectName, objectType, groupName != null ? groupName : "(n/a)",
                sessionId != null ? sessionId : "(temp)", force);

        try {
            String systemId;
            String systemHeader;

            if (sessionId != null && !sessionId.isEmpty()) {
                // Use provided session
                JcoSession session = sessionManager.getSession(sessionId);
                systemId = session.getSystemId();
                ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

                systemHeader = McpResponseFormatter.formatSystemHeader(
                        systemId,
                        resolved.getConfig().getEffectiveHost(),
                        resolved.getConfig().getClient()
                );

                // Execute activation within session context and capture raw response
                String rawResponse = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                    if (isFunctionModule) {
                        return adtClient.activateFunctionModule(
                                destination,
                                groupName,
                                objectName,
                                sess.getHttpClient(),
                                sess.getCsrfTokenCache(),
                                sess
                        );
                    }
                    return adtClient.activateObject(
                            destination,
                            objectName,
                            objectType.toUpperCase(),
                            sess.getHttpClient(),
                            sess.getCsrfTokenCache(),
                            sess
                    );
                });

                // Return raw XML response
                return McpResponseFormatter.success(systemHeader, rawResponse);

            } else {
                // Create temporary session for activation
                systemId = systemConfigLoader.getDefaultSystemId();
                ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

                systemHeader = McpResponseFormatter.formatSystemHeader(
                        systemId,
                        resolved.getConfig().getEffectiveHost(),
                        resolved.getConfig().getClient()
                );

                // For now, activation without session requires the session
                // In future: could use stateless activation endpoint
                return McpResponseFormatter.error(
                        "session_id is recommended for reliable activation. " +
                        "Create a session first with CreateSession, then use that session_id here."
                );
            }

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", sessionId);
            return McpResponseFormatter.error("Session not found: " + sessionId);

        } catch (Exception e) {
            log.error("ActivateObject failed for {}", objectName, e);
            return McpResponseFormatter.error(e);
        }
    }
}
