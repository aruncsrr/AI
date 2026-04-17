package com.sapjco.mcp.handlers.system;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.AuthType;
import com.sapjco.mcp.config.SystemConfigLoader.SncConfig;
import com.sapjco.mcp.config.SystemConfigLoader.SystemConfig;
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
import java.util.Map;

/**
 * Handler for AddSystem tool.
 * Adds a new SNC-authenticated SAP system to .sap-systems.json configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AddSystemHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("system_id",
                        "Unique identifier for the system (lowercase letters, numbers, hyphens; must start with letter)")
                .requiredString("url", "SAP system URL with port (e.g., \"https://sap.example.com:44300\")")
                .requiredString("client", "SAP client number (3 digits, e.g., \"001\", \"100\")")
                .requiredString("snc_partnername",
                        "SNC partner name (e.g., \"p/secude:CN=SID, O=SAP-AG, C=DE\")")
                .optionalString("sysnr",
                        "SAP system number (2 digits, e.g., \"00\", \"55\"). Derived from URL port if not specified: port 443xx means instance xx.")
                .optionalNumber("snc_qop",
                        "Quality of protection (1-9, default: 9). Higher values provide stronger security.", 9)
                .optionalString("description", "Optional human-readable description of the system")
                .optionalBoolean("set_as_default",
                        "Set this system as the default (default: false, or true if first system)", false)
                .buildTool(
                        "AddSystem",
                        "Add a new SNC-authenticated SAP system to .sap-systems.json configuration. " +
                        "Only SNC authentication is supported (basic auth is deprecated). " +
                        "Creates the config file if it does not exist."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String systemId = (String) args.get("system_id");
        String url = (String) args.get("url");
        String client = (String) args.get("client");
        String sncPartnername = (String) args.get("snc_partnername");
        String sysnr = (String) args.get("sysnr");
        Number sncQopNum = (Number) args.get("snc_qop");
        int sncQop = sncQopNum != null ? sncQopNum.intValue() : 9;
        String description = (String) args.get("description");
        Boolean setAsDefault = args.get("set_as_default") != null
                ? (Boolean) args.get("set_as_default")
                : false;

        log.info("AddSystem called: {} (url: {}, client: {}, sysnr: {})", systemId, url, client, sysnr);

        // Validate required parameters
        if (systemId == null || systemId.isEmpty()) {
            return McpResponseFormatter.error("Missing required parameter: system_id");
        }
        if (url == null || url.isEmpty()) {
            return McpResponseFormatter.error("Missing required parameter: url");
        }
        if (client == null || client.isEmpty()) {
            return McpResponseFormatter.error("Missing required parameter: client");
        }
        if (sncPartnername == null || sncPartnername.isEmpty()) {
            return McpResponseFormatter.error(
                    "Missing required parameter: snc_partnername (SNC authentication is required)");
        }

        // Validate system_id format
        if (!systemId.matches("^[a-z][a-z0-9-]*$")) {
            return McpResponseFormatter.error(
                    "Invalid system_id: must start with a letter and contain only lowercase letters, numbers, and hyphens");
        }

        // Validate URL format
        try {
            new URL(url);
        } catch (Exception e) {
            return McpResponseFormatter.error("Invalid URL format: " + url);
        }

        // Validate client format
        if (!client.matches("^\\d{3}$")) {
            return McpResponseFormatter.error(
                    "Invalid client: must be a 3-digit number (e.g., \"001\", \"100\")");
        }

        // Validate SNC QoP
        if (sncQop < 1 || sncQop > 9) {
            return McpResponseFormatter.error("Invalid snc_qop: must be between 1 and 9");
        }

        // Derive sysnr from URL port if not provided
        if (sysnr == null || sysnr.isEmpty()) {
            try {
                URL parsedUrl = new URL(url);
                int port = parsedUrl.getPort();
                if (port >= 44300 && port < 44400) {
                    // HTTPS port: 443xx where xx is instance number
                    sysnr = String.format("%02d", port - 44300);
                    log.info("Derived sysnr={} from URL port {}", sysnr, port);
                } else {
                    sysnr = "00"; // Default
                }
            } catch (Exception e) {
                sysnr = "00"; // Default on error
            }
        }

        // Validate sysnr format
        if (!sysnr.matches("^\\d{2}$")) {
            return McpResponseFormatter.error(
                    "Invalid sysnr: must be a 2-digit number (e.g., \"00\", \"55\")");
        }

        try {
            // Build the system config
            SystemConfig config = new SystemConfig();
            config.setUrl(url);
            config.setClient(client);
            config.setSysnr(sysnr);
            config.setAuthType(AuthType.snc);
            config.setDescription(description);

            SncConfig snc = new SncConfig();
            snc.setPartnername(sncPartnername);
            snc.setQop(sncQop);
            config.setSnc(snc);

            // Add the system
            systemConfigLoader.addSystem(systemId, config);

            // Set as default if requested
            if (setAsDefault) {
                SystemConfig partial = new SystemConfig();
                partial.setAuthType(AuthType.snc); // Required to pass validation
                systemConfigLoader.updateSystem(systemId, partial, true);
            }

            // Get updated list for confirmation
            var systems = systemConfigLoader.listSystems();
            var addedSystem = systems.stream()
                    .filter(s -> s.getSystemId().equals(systemId))
                    .findFirst()
                    .orElse(null);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Successfully added system \"%s\"\n\n", systemId));
            sb.append(String.format("  URL:           %s\n", url));
            sb.append(String.format("  Client:        %s\n", client));
            sb.append(String.format("  System Nr:     %s\n", sysnr));
            sb.append(String.format("  SNC Partner:   %s\n", sncPartnername));
            sb.append(String.format("  SNC QoP:       %d\n", sncQop));

            if (description != null && !description.isEmpty()) {
                sb.append(String.format("  Description:   %s\n", description));
            }

            if (addedSystem != null && addedSystem.isDefault()) {
                sb.append("\n  This system is now the default.");
            }

            sb.append(String.format("\n\nTotal configured systems: %d\n", systems.size()));
            sb.append(String.format("Config file: %s", systemConfigLoader.getConfigPath()));

            return McpResponseFormatter.success(sb.toString());

        } catch (Exception e) {
            log.error("AddSystem failed for {}", systemId, e);
            return McpResponseFormatter.error(e);
        }
    }
}
