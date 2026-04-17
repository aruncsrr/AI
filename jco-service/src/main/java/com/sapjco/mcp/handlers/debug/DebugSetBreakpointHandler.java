package com.sapjco.mcp.handlers.debug;

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
 * Handler for DebugSetBreakpoint tool.
 * Sets an external breakpoint in ABAP code via RFC proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebugSetBreakpointHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name",
                        "Name of the ABAP class with unit tests (e.g., \"ZCL_MY_CLASS\" or \"/SCMTMS/CL_TRS_E_MODEL\"). " +
                        "Namespaced objects are automatically URL-encoded.")
                .requiredEnum("object_type",
                        "Type of object: class, interface, program, function_group, include.",
                        List.of("class", "interface", "program", "function_group", "include"))
                .requiredNumber("line_number",
                        "Line number for the breakpoint. Line numbers match exactly what you write - " +
                        "count lines starting from 1, including blank lines.")
                .optionalEnum("include_type",
                        "For classes only: which include to set breakpoint in (default: testClasses)",
                        List.of("testclasses", "definitions", "implementations", "macros", "main"),
                        "testclasses")
                .requiredString("request_user",
                        "SAP username. REQUIRED for SNC-authenticated systems. Ask the user for their SAP username.")
                .requiredString("terminal_id",
                        "Terminal ID from DebugStartSession response. Required to associate breakpoint with active debug session.")
                .requiredString("ide_id",
                        "IDE ID from DebugStartSession response. Required to associate breakpoint with active debug session.")
                .optionalString("session_id",
                        "Session ID from CreateSession (for HTTP session affinity)")
                .buildTool(
                        "DebugSetBreakpoint",
                        "Set an additional breakpoint during an active debug session. " +
                        "REQUIRES terminal_id and ide_id from DebugStartSession to associate the new breakpoint " +
                        "with the active debug session. For starting a new debug session, use DebugStartSession instead. " +
                        "IMPORTANT: For SNC-authenticated systems, request_user parameter is REQUIRED."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        int lineNumber = ((Number) args.get("line_number")).intValue();
        String includeType = args.get("include_type") != null ? (String) args.get("include_type") : "testclasses";
        String requestUser = (String) args.get("request_user");
        String terminalId = (String) args.get("terminal_id");
        String ideId = (String) args.get("ide_id");
        String sessionId = (String) args.get("session_id");

        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }
        if (requestUser == null || requestUser.isEmpty()) {
            return McpResponseFormatter.error("request_user is required. Please provide your SAP username.");
        }
        if (terminalId == null || terminalId.isEmpty()) {
            return McpResponseFormatter.error("terminal_id is required. Use the terminal_id from DebugStartSession response.");
        }
        if (ideId == null || ideId.isEmpty()) {
            return McpResponseFormatter.error("ide_id is required. Use the ide_id from DebugStartSession response.");
        }

        log.info("DebugSetBreakpoint called: {} line {} (type: {}, include: {}, user: {}, terminal: {}, session: {})",
                objectName, lineNumber, objectType, includeType, requestUser, terminalId,
                sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(null);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // Generate client ID for this specific breakpoint request
            String clientId = "BP_" + System.currentTimeMillis();

            // Build object URI (without /source/main for class includes)
            String objectUri = buildObjectUri(objectType, objectName, includeType);

            // Build breakpoint request XML using the correct SAP ADT format
            String requestBody = buildBreakpointXml(requestUser, terminalId, ideId, objectUri, lineNumber, clientId);

            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.debugSetBreakpointRest(dest, requestBody,
                        session.getHttpClient(), session.getCsrfTokenCache(), session);
            });

            // Extract breakpoint ID from response for confirmation
            String breakpointId = extractBreakpointId(response);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            StringBuilder sb = new StringBuilder();
            sb.append("Additional Breakpoint Set\n");
            sb.append(String.format("Object: %s (%s/%s)\n", objectName.toUpperCase(), objectType, includeType));
            sb.append(String.format("Line: %d\n", lineNumber));
            sb.append(String.format("User: %s\n", requestUser));
            sb.append("-".repeat(60)).append("\n");
            sb.append(String.format("Terminal ID: %s\n", terminalId));
            sb.append(String.format("IDE ID: %s\n", ideId));
            if (breakpointId != null) {
                sb.append(String.format("Breakpoint ID: %s\n", breakpointId));
            }
            sb.append("-".repeat(60)).append("\n\n");
            sb.append("The breakpoint has been added to the active debug session.\n");
            sb.append("Use DebugResume to continue execution - it will stop at this breakpoint.\n\n");
            sb.append("Response:\n").append(response);

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("DebugSetBreakpoint failed for {}", objectName, e);
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

    private String encodeObjectName(String name) {
        return URLEncoder.encode(name.toLowerCase(), StandardCharsets.UTF_8);
    }

    private String buildObjectUri(String objectType, String objectName, String includeType) {
        String encodedName = encodeObjectName(objectName);

        switch (objectType.toLowerCase()) {
            case "class":
                if ("main".equalsIgnoreCase(includeType)) {
                    return "/sap/bc/adt/oo/classes/" + encodedName + "/source/main";
                } else {
                    // Class includes don't use /source/main suffix
                    return "/sap/bc/adt/oo/classes/" + encodedName + "/includes/" + includeType.toLowerCase();
                }
            case "interface":
                return "/sap/bc/adt/oo/interfaces/" + encodedName + "/source/main";
            case "program":
                return "/sap/bc/adt/programs/programs/" + encodedName + "/source/main";
            case "function_group":
                return "/sap/bc/adt/functions/groups/" + encodedName + "/source/main";
            case "include":
                return "/sap/bc/adt/programs/includes/" + encodedName + "/source/main";
            default:
                throw new IllegalArgumentException("Unsupported object type: " + objectType);
        }
    }

    private String escapeXml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String buildBreakpointXml(String requestUser, String terminalId, String ideId,
                                       String objectUri, int lineNumber, String clientId) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<dbg:breakpoints scope=\"external\" debuggingMode=\"user\" " +
                "requestUser=\"" + escapeXml(requestUser) + "\" " +
                "terminalId=\"" + terminalId + "\" " +
                "ideId=\"" + ideId + "\" " +
                "systemDebugging=\"false\" deactivated=\"false\" " +
                "xmlns:dbg=\"http://www.sap.com/adt/debugger\">" +
                "<syncScope mode=\"partial\">" +
                "<adtcore:objectReference xmlns:adtcore=\"http://www.sap.com/adt/core\" " +
                "adtcore:uri=\"" + escapeXml(objectUri) + "\"/>" +
                "</syncScope>" +
                "<breakpoint kind=\"line\" clientId=\"" + clientId + "\" skipCount=\"0\" " +
                "adtcore:uri=\"" + escapeXml(objectUri) + "#start=" + lineNumber + "\" " +
                "xmlns:adtcore=\"http://www.sap.com/adt/core\"></breakpoint>" +
                "</dbg:breakpoints>";
    }

    private String extractBreakpointId(String responseXml) {
        // Try to extract breakpoint ID from response
        Pattern[] patterns = {
                Pattern.compile("id=\"([^\"]+)\""),
                Pattern.compile("<breakpoint[^>]*dbg:id=\"([^\"]+)\""),
                Pattern.compile("clientId=\"([^\"]+)\"")
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(responseXml);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return null;
    }
}
