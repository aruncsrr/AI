package com.sapjco.mcp.handlers.system;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.SystemInfo;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler for RemoveSystem tool.
 * Removes a system from .sap-systems.json configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoveSystemHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("system_id", "System ID to remove")
                .buildTool(
                        "RemoveSystem",
                        "Remove a system from .sap-systems.json configuration. " +
                        "Cannot remove the default system if other systems exist; set a different default first."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String systemId = (String) args.get("system_id");

        // Validate required parameter
        if (systemId == null || systemId.isEmpty()) {
            return McpResponseFormatter.error("Missing required parameter: system_id");
        }

        log.info("RemoveSystem called: {}", systemId);

        try {
            // Get system list before removal for confirmation
            List<SystemInfo> systemsBefore = systemConfigLoader.listSystems();
            SystemInfo systemToRemove = systemsBefore.stream()
                    .filter(s -> s.getSystemId().equals(systemId))
                    .findFirst()
                    .orElse(null);

            if (systemToRemove == null) {
                String availableSystems = systemsBefore.stream()
                        .map(SystemInfo::getSystemId)
                        .collect(Collectors.joining(", "));
                return McpResponseFormatter.error(
                        String.format("System \"%s\" not found. Available systems: %s",
                                systemId, availableSystems.isEmpty() ? "(none)" : availableSystems));
            }

            // Perform removal
            systemConfigLoader.removeSystem(systemId);

            // Get updated list
            List<SystemInfo> systemsAfter = systemConfigLoader.listSystems();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Successfully removed system \"%s\"\n\n", systemId));
            sb.append(String.format("  Removed URL:    %s\n", systemToRemove.getUrl()));
            sb.append(String.format("  Removed Client: %s\n", systemToRemove.getClient()));

            if (systemToRemove.isDefault() && !systemsAfter.isEmpty()) {
                SystemInfo newDefault = systemsAfter.stream()
                        .filter(SystemInfo::isDefault)
                        .findFirst()
                        .orElse(null);
                if (newDefault != null) {
                    sb.append(String.format("\n  Note: Default system changed to \"%s\"", newDefault.getSystemId()));
                }
            }

            sb.append(String.format("\n\nRemaining configured systems: %d", systemsAfter.size()));

            if (systemsAfter.isEmpty()) {
                sb.append("\n\nWarning: No systems configured. Use AddSystem to add a new system.");
            }

            return McpResponseFormatter.success(sb.toString());

        } catch (Exception e) {
            log.error("RemoveSystem failed for {}", systemId, e);
            return McpResponseFormatter.error(e);
        }
    }
}
