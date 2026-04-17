package com.sapjco.mcp.handlers.transport;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.JcoSessionManager;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for GetTransportRequests tool.
 * Gets available transport requests for a specific ABAP object via RFC proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetTransportRequestsHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name", "Name of the ABAP object")
                .requiredEnum("object_type",
                        "Type of object: class, interface, program, function_group, include",
                        List.of("class", "interface", "program", "function_group", "include"))
                .optionalString("devclass",
                        "Optional: Package name (DEVCLASS). If not provided, will be automatically " +
                        "fetched from object metadata.")
                .optionalString("system_id",
                        "Optional SAP system ID (e.g., \"dev\", \"prod\"). If not specified, uses the default system.")
                .buildTool(
                        "GetTransportRequests",
                        "Get available transport requests for a specific ABAP object. Performs a transport " +
                        "check to determine which transport requests can be used to modify the given object. " +
                        "Use this when you need to save an object and need to find an appropriate transport."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        String devclass = (String) args.get("devclass");
        String systemId = (String) args.get("system_id");

        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }

        log.info("GetTransportRequests called: {} (type: {}, devclass: {}, system: {})", objectName, objectType, devclass, systemId);

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
            tempSessionId = jcoSessionManager.createSession(sessionRequest);
            final String sessionId = tempSessionId;

            // Build transport check request
            String pgmid = getPgmid(objectType);
            String objType = getObjType(objectType);

            // Auto-fetch package if not provided (REQUIRED by SAP transport check API)
            String resolvedDevclass = devclass;
            if (resolvedDevclass == null || resolvedDevclass.isEmpty()) {
                log.info("No package provided, fetching from object metadata");
                resolvedDevclass = fetchObjectPackage(objectType, objectName, sessionId);
                log.info("Auto-fetched package: {}", resolvedDevclass);
            }

            // Build object URI (required field)
            String objectUri = buildObjectPath(objectType, objectName);

            String requestBody = String.format(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<asx:abap xmlns:asx=\"http://www.sap.com/abapxml\" version=\"1.0\">\n" +
                    "  <asx:values>\n" +
                    "    <DATA>\n" +
                    "      <PGMID>%s</PGMID>\n" +
                    "      <OBJECT>%s</OBJECT>\n" +
                    "      <OBJECTNAME>%s</OBJECTNAME>\n" +
                    "      <DEVCLASS>%s</DEVCLASS>\n" +
                    "      <SUPER_PACKAGE></SUPER_PACKAGE>\n" +
                    "      <OPERATION></OPERATION>\n" +
                    "      <URI>%s</URI>\n" +
                    "    </DATA>\n" +
                    "  </asx:values>\n" +
                    "</asx:abap>",
                    escapeXml(pgmid),
                    escapeXml(objType),
                    escapeXml(objectName.toUpperCase()),
                    escapeXml(resolvedDevclass),
                    escapeXml(objectUri)
            );

            String path = "/sap/bc/adt/cts/transportchecks";
            String response = jcoSessionManager.executeInContext(sessionId, (dest, session) -> {
                return adtClient.postTextViaRfc(dest, session, path, null, requestBody,
                        "application/vnd.sap.as+xml;charset=utf-8;dataname=com.sap.adt.transport.service.checkData",
                        "application/vnd.sap.as+xml");
            });

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            return McpResponseFormatter.success(systemHeader, response);

        } catch (Exception e) {
            log.error("GetTransportRequests failed for {}", objectName, e);
            return McpResponseFormatter.error(e);
        } finally {
            if (tempSessionId != null) {
                try {
                    jcoSessionManager.destroySession(tempSessionId);
                } catch (Exception e) {
                    log.warn("Failed to destroy temp session: {}", e.getMessage());
                }
            }
        }
    }

    private String getPgmid(String objectType) {
        return "R3TR"; // All these object types use R3TR
    }

    private String getObjType(String objectType) {
        switch (objectType.toLowerCase()) {
            case "class": return "CLAS";
            case "interface": return "INTF";
            case "program": return "PROG";
            case "function_group": return "FUGR";
            case "include": return "PROG";
            default: return objectType.toUpperCase();
        }
    }

    private String buildObjectPath(String objectType, String objectName) {
        String encodedName = encodeObjectName(objectName);
        switch (objectType.toLowerCase()) {
            case "class": return "/sap/bc/adt/oo/classes/" + encodedName;
            case "interface": return "/sap/bc/adt/oo/interfaces/" + encodedName;
            case "program": return "/sap/bc/adt/programs/programs/" + encodedName;
            case "function_group": return "/sap/bc/adt/functions/groups/" + encodedName;
            case "include": return "/sap/bc/adt/programs/includes/" + encodedName;
            default: throw new IllegalArgumentException("Unsupported object type: " + objectType);
        }
    }

    private String encodeObjectName(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String fetchObjectPackage(String objectType, String objectName, String sessionId) throws Exception {
        String path = buildObjectPath(objectType, objectName);
        String response = jcoSessionManager.executeInContext(sessionId, (dest, session) -> {
            return adtClient.statelessGetViaRfc(dest, session, path, null,
                    "application/vnd.sap.adt.oo.classes.v4+xml, application/xml");
        });

        // Parse packageName or packageRef from XML
        Pattern pattern = Pattern.compile("(?:adtcore:)?packageName=\"([^\"]+)\"|<(?:adtcore:)?packageRef[^>]*name=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        throw new RuntimeException("Could not find package name in object metadata for " + objectName);
    }
}
