package com.sapjco.mcp.handlers.system;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetLandscapeSystems tool.
 * Reads SNC-enabled SAP systems from the SAP GUI landscape file (SAPGUILandscape.xml).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetLandscapeSystemsHandler implements ToolHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .optionalString("landscape_path",
                        "Optional: Custom path to landscape file. Defaults to platform-specific location " +
                        "(macOS: ~/Library/Preferences/SAP/SAPGUILandscape.xml, " +
                        "Windows: %APPDATA%/SAP/Common/SAPUILandscape.xml).")
                .buildTool(
                        "GetLandscapeSystems",
                        "Read SNC-enabled SAP systems from the SAP GUI landscape file (SAPGUILandscape.xml). " +
                        "Returns systems with SNC configuration that can be added using AddSystem. " +
                        "Systems without SNC are filtered out (basic auth is deprecated)."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String landscapePath = args != null ? (String) args.get("landscape_path") : null;

        log.info("GetLandscapeSystems called (path: {})", landscapePath != null ? landscapePath : "(default)");

        try {
            // Determine landscape path
            String path = landscapePath != null ? landscapePath : getDefaultLandscapePath();

            if (path == null) {
                return McpResponseFormatter.error(
                        "Could not determine landscape file path for this platform. " +
                        "Please provide landscape_path parameter."
                );
            }

            File file = new File(path);
            if (!file.exists()) {
                return McpResponseFormatter.error("Landscape file not found: " + path);
            }

            // Parse the landscape file
            List<LandscapeSystem> systems = parseLandscapeFile(file);

            // Filter to SNC-enabled systems only
            List<LandscapeSystem> sncSystems = new ArrayList<>();
            for (LandscapeSystem system : systems) {
                if (system.sncPartnername != null && !system.sncPartnername.isEmpty()) {
                    sncSystems.add(system);
                }
            }

            // Format response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("SAP Systems from Landscape File: %s\n\n", path));
            sb.append(String.format("Found %d SNC-enabled system(s):\n\n", sncSystems.size()));

            if (sncSystems.isEmpty()) {
                sb.append("  (No SNC-enabled systems found. Only SNC authentication is supported.)\n\n");
                sb.append("Note: Systems with basic auth are not listed as basic auth is deprecated.");
            } else {
                for (LandscapeSystem system : sncSystems) {
                    sb.append(String.format("  %s\n", system.systemId));
                    sb.append(String.format("    URL:           %s\n", system.url));
                    sb.append(String.format("    Client:        %s\n", system.client));
                    sb.append(String.format("    Instance:      %s\n", system.instanceNumber));
                    sb.append(String.format("    SNC Partner:   %s\n", system.sncPartnername));
                    sb.append(String.format("    SNC QoP:       %d\n", system.sncQop));
                    if (system.description != null && !system.description.isEmpty()) {
                        sb.append(String.format("    Description:   %s\n", system.description));
                    }
                    sb.append("\n");
                }
                sb.append("Use AddSystem to add any of these systems to your configuration.");
            }

            return McpResponseFormatter.success(sb.toString());

        } catch (Exception e) {
            log.error("GetLandscapeSystems failed", e);
            return McpResponseFormatter.error(e);
        }
    }

    /**
     * Get default landscape file path for the current platform.
     */
    private String getDefaultLandscapePath() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (home == null) {
            return null;
        }

        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Preferences", "SAP", "SAPGUILandscape.xml").toString();
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return Paths.get(appData, "SAP", "Common", "SAPUILandscape.xml").toString();
            }
            return Paths.get(home, "AppData", "Roaming", "SAP", "Common", "SAPUILandscape.xml").toString();
        } else if (os.contains("linux")) {
            return Paths.get(home, ".sap", "SAPGUILandscape.xml").toString();
        }

        return null;
    }

    /**
     * Parse SAP GUI landscape file.
     */
    private List<LandscapeSystem> parseLandscapeFile(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        doc.getDocumentElement().normalize();

        // Parse message servers for hostname and port lookup
        Map<String, String> messageServerHosts = new HashMap<>();
        Map<String, String> messageServerPorts = new HashMap<>();
        NodeList msNodes = doc.getElementsByTagName("Messageserver");
        for (int i = 0; i < msNodes.getLength(); i++) {
            Element ms = (Element) msNodes.item(i);
            String uuid = ms.getAttribute("uuid");
            String host = ms.getAttribute("host");
            String port = ms.getAttribute("port");
            if (uuid != null && host != null) {
                messageServerHosts.put(uuid, host);
            }
            if (uuid != null && port != null) {
                messageServerPorts.put(uuid, port);
            }
        }

        // Parse services
        List<LandscapeSystem> systems = new ArrayList<>();
        NodeList serviceNodes = doc.getElementsByTagName("Service");

        for (int i = 0; i < serviceNodes.getLength(); i++) {
            Element service = (Element) serviceNodes.item(i);

            // Only process SAPGUI services
            String type = service.getAttribute("type");
            if (!"SAPGUI".equals(type)) {
                continue;
            }

            String systemId = service.getAttribute("systemid");
            String msid = service.getAttribute("msid");
            String sncName = service.getAttribute("sncname");
            String sncOp = service.getAttribute("sncop");
            String description = service.getAttribute("description");
            String name = service.getAttribute("name");

            // Get hostname from message server
            String hostname = messageServerHosts.get(msid);
            if (hostname == null || hostname.isEmpty()) {
                log.warn("No message server host found for system {} (msid: {})", systemId, msid);
                continue;
            }

            // Derive HTTPS port from message server port
            // Message server port format: 36xx where xx is instance number
            // HTTPS port formula: 443xx where xx is instance number
            String msPort = messageServerPorts.get(msid);
            int httpsPort = 44300; // Default to instance 00
            String instanceNumber = "00";
            if (msPort != null && !msPort.isEmpty()) {
                try {
                    int portNum = Integer.parseInt(msPort);
                    // Extract instance number from various port patterns:
                    // - Message server: 36xx (e.g., 3600, 3620, 3655)
                    // - Some systems use: port directly encodes instance (e.g., 19363 for instance 63)
                    if (portNum >= 3600 && portNum < 3700) {
                        // Standard message server port 36xx
                        instanceNumber = String.format("%02d", portNum - 3600);
                    } else if (portNum >= 100 && portNum < 100000) {
                        // Try to extract last 2 digits as instance number
                        instanceNumber = String.format("%02d", portNum % 100);
                    }
                    int instNum = Integer.parseInt(instanceNumber);
                    httpsPort = 44300 + instNum;
                } catch (NumberFormatException e) {
                    log.warn("Could not parse message server port {} for system {}, using default 44300", msPort, systemId);
                }
            }

            LandscapeSystem system = new LandscapeSystem();
            system.systemId = systemId != null ? systemId.toLowerCase() : name;
            system.url = "https://" + hostname + ":" + httpsPort;
            system.instanceNumber = instanceNumber;
            system.client = "001"; // Default, user should override
            system.description = description != null ? description : name;

            if (sncName != null && !sncName.isEmpty()) {
                system.sncPartnername = sncName;
                try {
                    system.sncQop = sncOp != null ? Integer.parseInt(sncOp) : 9;
                } catch (NumberFormatException e) {
                    system.sncQop = 9;
                }
            }

            systems.add(system);
        }

        return systems;
    }

    /**
     * Simple data class for landscape system.
     */
    private static class LandscapeSystem {
        String systemId;
        String url;
        String client;
        String instanceNumber;
        String description;
        String sncPartnername;
        int sncQop;
    }
}
