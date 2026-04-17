package com.sapjco.mcp.handlers.system;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.AuthType;
import com.sapjco.mcp.config.SystemConfigLoader.SncConfig;
import com.sapjco.mcp.config.SystemConfigLoader.SystemConfig;
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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handler for UpdateSystem tool.
 * Updates an existing system configuration in .sap-systems.json.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateSystemHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("system_id", "System ID to update")
                .optionalString("url", "New SAP system URL with port")
                .optionalString("client", "New SAP client number (3 digits)")
                .optionalString("snc_partnername", "New SNC partner name")
                .optionalNumber("snc_qop", "New quality of protection (1-9)")
                .optionalString("description", "New description")
                .optionalBoolean("set_as_default", "Set this system as the default")
                .buildTool(
                        "UpdateSystem",
                        "Update an existing system configuration in .sap-systems.json. " +
                        "Only provided fields are updated; others remain unchanged. " +
                        "Only SNC authentication is supported."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String systemId = (String) args.get("system_id");
        String url = (String) args.get("url");
        String client = (String) args.get("client");
        String sncPartnername = (String) args.get("snc_partnername");
        Number sncQopNum = (Number) args.get("snc_qop");
        String description = (String) args.get("description");
        Boolean setAsDefault = (Boolean) args.get("set_as_default");

        // Validate required parameter
        if (systemId == null || systemId.isEmpty()) {
            return McpResponseFormatter.error("Missing required parameter: system_id");
        }

        // Check if any updates are provided
        boolean hasUpdates = url != null ||
                client != null ||
                sncPartnername != null ||
                sncQopNum != null ||
                description != null ||
                setAsDefault != null;

        if (!hasUpdates) {
            return McpResponseFormatter.error(
                    "No updates provided. Specify at least one field to update " +
                    "(url, client, snc_partnername, snc_qop, description, set_as_default)");
        }

        log.info("UpdateSystem called: {}", systemId);

        // Validate URL format if provided
        if (url != null) {
            try {
                new URL(url);
            } catch (Exception e) {
                return McpResponseFormatter.error("Invalid URL format: " + url);
            }
        }

        // Validate client format if provided
        if (client != null && !client.matches("^\\d{3}$")) {
            return McpResponseFormatter.error(
                    "Invalid client: must be a 3-digit number (e.g., \"001\", \"100\")");
        }

        // Validate SNC QoP if provided
        if (sncQopNum != null) {
            int sncQop = sncQopNum.intValue();
            if (sncQop < 1 || sncQop > 9) {
                return McpResponseFormatter.error("Invalid snc_qop: must be between 1 and 9");
            }
        }

        try {
            // Build partial config
            SystemConfig partialConfig = new SystemConfig();
            partialConfig.setAuthType(AuthType.snc); // Required for validation

            if (url != null) {
                partialConfig.setUrl(url);
            }
            if (client != null) {
                partialConfig.setClient(client);
            }
            if (description != null) {
                partialConfig.setDescription(description);
            }
            if (sncPartnername != null || sncQopNum != null) {
                SncConfig snc = new SncConfig();
                if (sncPartnername != null) {
                    snc.setPartnername(sncPartnername);
                }
                if (sncQopNum != null) {
                    snc.setQop(sncQopNum.intValue());
                }
                partialConfig.setSnc(snc);
            }

            // Perform update
            systemConfigLoader.updateSystem(systemId, partialConfig, setAsDefault);

            // Get updated system for confirmation
            List<SystemInfo> systems = systemConfigLoader.listSystems();
            SystemInfo updatedSystem = systems.stream()
                    .filter(s -> s.getSystemId().equals(systemId))
                    .findFirst()
                    .orElse(null);

            // Track updated fields
            List<String> updatedFields = new ArrayList<>();
            if (url != null) updatedFields.add("url");
            if (client != null) updatedFields.add("client");
            if (sncPartnername != null) updatedFields.add("snc_partnername");
            if (sncQopNum != null) updatedFields.add("snc_qop");
            if (description != null) updatedFields.add("description");
            if (Boolean.TRUE.equals(setAsDefault)) updatedFields.add("default");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Successfully updated system \"%s\"\n\n", systemId));
            sb.append(String.format("Updated fields: %s\n\n", String.join(", ", updatedFields)));

            if (updatedSystem != null) {
                sb.append("Current configuration:\n");
                sb.append(String.format("  URL:         %s\n", updatedSystem.getUrl()));
                sb.append(String.format("  Client:      %s\n", updatedSystem.getClient()));
                sb.append(String.format("  Host:        %s\n", updatedSystem.getHost()));
                if (updatedSystem.getDescription() != null && !updatedSystem.getDescription().isEmpty()) {
                    sb.append(String.format("  Description: %s\n", updatedSystem.getDescription()));
                }
                if (updatedSystem.isDefault()) {
                    sb.append("\n  This system is the default.");
                }
            }

            return McpResponseFormatter.success(sb.toString());

        } catch (Exception e) {
            log.error("UpdateSystem failed for {}", systemId, e);
            return McpResponseFormatter.error(e);
        }
    }
}
