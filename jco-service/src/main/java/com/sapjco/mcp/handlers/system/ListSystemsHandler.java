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

/**
 * Handler for ListSystems tool.
 * Lists all configured SAP systems without exposing credentials.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListSystemsHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .buildTool(
                        "ListSystems",
                        "List all configured SAP systems. Returns system IDs, URLs, clients, and descriptions. " +
                        "Use this to discover available systems before creating sessions. Does NOT expose credentials."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        log.info("ListSystems called");

        try {
            List<SystemInfo> systems = systemConfigLoader.listSystems();

            if (systems.isEmpty()) {
                return McpResponseFormatter.error(
                        "No SAP systems configured. Create a .sap-systems.json configuration file or " +
                        "use AddSystem to add a system."
                );
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Configured SAP Systems:\n");
            sb.append("=".repeat(60)).append("\n\n");

            for (SystemInfo system : systems) {
                sb.append(String.format("System ID: %s%s\n",
                        system.getSystemId(),
                        system.isDefault() ? " (default)" : ""));
                sb.append(String.format("  URL: %s\n", system.getUrl()));
                sb.append(String.format("  Host: %s\n", system.getHost()));
                sb.append(String.format("  Client: %s\n", system.getClient()));
                sb.append(String.format("  System Nr: %s\n", system.getSysnr() != null ? system.getSysnr() : "00"));
                sb.append(String.format("  Auth Type: %s\n", system.getAuthType()));
                if (system.getDescription() != null && !system.getDescription().isEmpty()) {
                    sb.append(String.format("  Description: %s\n", system.getDescription()));
                }
                sb.append("\n");
            }

            sb.append(String.format("Total: %d system(s)\n", systems.size()));
            sb.append(String.format("Config file: %s\n", systemConfigLoader.getConfigPath()));

            return McpResponseFormatter.success(sb.toString());

        } catch (Exception e) {
            log.error("ListSystems failed", e);
            return McpResponseFormatter.error(e);
        }
    }
}
