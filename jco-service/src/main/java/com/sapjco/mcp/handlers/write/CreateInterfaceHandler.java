package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateResponse;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.JcoSessionManager;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler for CreateInterface tool.
 * Creates a new ABAP interface via ADT REST API.
 *
 * This handler is only enabled when SAP_ADT_DANGEROUS_OPERATIONS=true.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sap.adt.dangerous-operations.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CreateInterfaceHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED - write operations only work via JCo)")
                .requiredString("interface_name",
                        "Name of the ABAP interface to create (must follow naming convention: ZIF_* or YIF_* prefix)")
                .requiredString("description",
                        "Short description for the interface (max 60 characters)")
                .requiredString("package_name",
                        "Target package name ($TMP for local development, or package with transport)")
                .optionalString("transport_number",
                        "Transport request number. Not required for $TMP package. " +
                        "Use GetTransportRequests to find available transports, then ALWAYS ask user for " +
                        "confirmation before selecting a transport.")
                .buildTool(
                        "CreateInterface",
                        "Create a new ABAP interface. Creates an empty interface that you can then modify " +
                        "using GetInterface and SaveInterface. The interface is created INACTIVE - use ActivateObject to activate. " +
                        "For $TMP package, no transport is needed. For other packages, provide transport_number."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        String interfaceName = (String) args.get("interface_name");
        String description = (String) args.get("description");
        String packageName = (String) args.get("package_name");
        String transportNumber = (String) args.get("transport_number");

        // Validate required parameters
        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }
        if (interfaceName == null || interfaceName.isEmpty()) {
            return McpResponseFormatter.error("interface_name is required");
        }
        if (description == null || description.isEmpty()) {
            return McpResponseFormatter.error("description is required");
        }
        if (packageName == null || packageName.isEmpty()) {
            return McpResponseFormatter.error("package_name is required");
        }

        log.info("CreateInterface called: {} (session: {}, package: {}, transport: {})",
                interfaceName, sessionId, packageName, transportNumber != null ? transportNumber : "(auto)");

        try {
            // Get session info for system header
            JcoSession session = sessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            // Execute creation within JCo context
            CreateResponse createResponse = sessionManager.executeInContext(sessionId, (destination, sess) -> {
                return adtClient.createInterface(
                        destination,
                        interfaceName,
                        description,
                        packageName,
                        transportNumber,
                        sess.getHttpClient(),
                        sess.getCsrfTokenCache(),
                        sess
                );
            });

            // Build response
            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    systemId,
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            StringBuilder result = new StringBuilder();
            result.append(systemHeader).append("\n\n");
            result.append("Interface created successfully!\n\n");
            result.append("Name: ").append(createResponse.getObjectName()).append("\n");
            result.append("Package: ").append(packageName.toUpperCase()).append("\n");
            result.append("Status: INACTIVE (use ActivateObject to activate)\n");
            if (createResponse.getObjectUri() != null) {
                result.append("URI: ").append(createResponse.getObjectUri()).append("\n");
            }
            result.append("HTTP Status: ").append(createResponse.getHttpStatus()).append("\n");

            // Add next steps hint
            result.append("\nNext steps:\n");
            result.append("1. Use GetInterface to retrieve the generated skeleton\n");
            result.append("2. Add methods and attributes as needed\n");
            result.append("3. Use SaveInterface to save your changes\n");
            result.append("4. Use ActivateObject to activate the interface");

            return McpResponseFormatter.success(result.toString());

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", sessionId);
            return McpResponseFormatter.error("Session not found: " + sessionId);

        } catch (Exception e) {
            log.error("CreateInterface failed for {}", interfaceName, e);
            return McpResponseFormatter.error(e);
        }
    }
}
