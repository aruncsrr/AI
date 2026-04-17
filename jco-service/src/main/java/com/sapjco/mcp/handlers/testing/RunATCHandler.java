package com.sapjco.mcp.handlers.testing;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.HttpProxyResponse;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.JcoSessionManager;
import com.sapjco.mcp.service.MetadataExtractorService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for RunATC tool.
 * Runs ATC (ABAP Test Cockpit) checks on an ABAP object via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunATCHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name",
                        "Name of the ABAP object to check (e.g., \"ZCL_MY_CLASS\", \"ZTEST_PROGRAM\")")
                .requiredEnum("object_type",
                        "Type of object: class, interface, program, function_group, include, package",
                        List.of("class", "interface", "program", "function_group", "include", "package"))
                .optionalString("check_variant",
                        "ATC check variant name (default: system default)")
                .optionalNumber("max_findings",
                        "Maximum number of findings to return (default: 100)")
                .optionalBoolean("with_exemptions",
                        "Include exempted findings in results (default: false)")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .buildTool(
                        "RunATC",
                        "Run ATC (ABAP Test Cockpit) checks on an ABAP object. Executes static code analysis " +
                        "and returns findings including errors, warnings, and informational messages. When " +
                        "checking a package, all objects within will be checked. Returns a display ID for " +
                        "retrieving results later via GetATCFindings. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        String checkVariant = (String) args.get("check_variant");
        int maxFindings = args.get("max_findings") != null
                ? ((Number) args.get("max_findings")).intValue()
                : 100;
        boolean withExemptions = args.get("with_exemptions") != null
                ? (Boolean) args.get("with_exemptions")
                : false;
        String sessionId = (String) args.get("session_id");

        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }

        log.info("RunATC called: {} (type: {}, variant: {}, max: {}, session: {})",
                objectName, objectType, checkVariant, maxFindings, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(null);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // Resolve check variant (required by SAP backend)
            String resolvedCheckVariant = checkVariant;
            if (resolvedCheckVariant == null || resolvedCheckVariant.isEmpty()) {
                resolvedCheckVariant = getSystemDefaultCheckVariant(finalSessionId);
                if (resolvedCheckVariant == null) {
                    return McpResponseFormatter.error(
                            "No check variant specified and no system default check variant configured. " +
                            "Please provide a check_variant parameter or configure a system default in ATC customizing.");
                }
            }
            log.info("Using check variant: {}", resolvedCheckVariant);

            // Step 1: Create worklist (checkVariant is REQUIRED)
            String worklistPath = "/sap/bc/adt/atc/worklists";
            Map<String, String> worklistParams = new LinkedHashMap<>();
            worklistParams.put("checkVariant", resolvedCheckVariant);

            HttpProxyResponse worklistResponse = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessPostViaRfc(dest, session, worklistPath,
                        worklistParams, "",
                        "text/plain", "text/plain");
            });

            // Server returns plain text 32-character GUID - NO fallbacks
            String worklistId = worklistResponse.getBody() != null ? worklistResponse.getBody().trim() : "";
            if (worklistId.length() != 32) {
                return McpResponseFormatter.error(
                        "Invalid worklist ID returned from server: \"" + worklistId + "\" (expected 32-character GUID)");
            }
            log.info("Created ATC worklist: {}", worklistId);

            // Step 2: Submit ATC run with worklist
            String objectUri = buildObjectUri(objectType, objectName);
            String runRequestBody = buildAtcRunRequest(objectUri, maxFindings);

            Map<String, String> runParams = new LinkedHashMap<>();
            runParams.put("worklistId", worklistId);
            runParams.put("clientWait", "false");

            String runsPath = "/sap/bc/adt/atc/runs";
            HttpProxyResponse runResponse = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessPostViaRfc(dest, session, runsPath, runParams, runRequestBody,
                        "application/xml", "application/xml");
            });

            // Step 3: Poll for completion
            String runId = extractRunId(runResponse);
            if (runId == null) {
                return McpResponseFormatter.error("No run ID returned from ATC run submission");
            }
            log.info("ATC run started: {}", runId);

            // Poll until finished (max 75 polls, 4s interval = 5 minutes)
            boolean finished = false;
            for (int i = 0; i < 75; i++) {
                if (i > 0) {
                    Thread.sleep(4000);
                }

                String statusPath = "/sap/bc/adt/atc/runs/" + runId;
                String statusResponse = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                    return adtClient.statelessGetViaRfc(dest, session, statusPath, null,
                            "application/vnd.sap.adt.backgroundrun.v1+xml");
                });

                if (statusResponse.contains("status=\"finished\"")) {
                    log.info("ATC run finished after {} polls", i + 1);
                    finished = true;
                    break;
                }
                if (statusResponse.contains("status=\"cancelled\"")) {
                    return McpResponseFormatter.error("ATC run was cancelled");
                }
            }

            if (!finished) {
                return McpResponseFormatter.error("ATC run timed out after 5 minutes");
            }

            // Step 4: Get worklist results
            String resultsPath = "/sap/bc/adt/atc/worklists/" + worklistId;
            Map<String, String> resultsParams = new LinkedHashMap<>();
            resultsParams.put("includeExemptedFindings", String.valueOf(withExemptions));

            String resultsResponse = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, resultsPath, resultsParams,
                        "application/atc.worklist.v1+xml");
            });

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            String sanitizedName = fileStorageService.sanitizeFilename(objectName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_ATC, sanitizedName + "_atc", resultsResponse);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote ATC results to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractAtcMetadata(resultsResponse);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ATC Check: %s (%s)\n", objectName.toUpperCase(), objectType));
            sb.append(String.format("Worklist ID: %s\n", worklistId));
            sb.append(String.format("Check Variant: %s\n", resolvedCheckVariant));
            sb.append(metadataExtractorService.formatMetadata(metadata));
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(resultsResponse, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("RunATC failed for {}", objectName, e);
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

    private String buildObjectUri(String objectType, String objectName) {
        String encodedName = encodeObjectName(objectName);
        switch (objectType.toLowerCase()) {
            case "class":
                return "/sap/bc/adt/oo/classes/" + encodedName;
            case "interface":
                return "/sap/bc/adt/oo/interfaces/" + encodedName;
            case "program":
            case "include":
                return "/sap/bc/adt/programs/programs/" + encodedName;
            case "function_group":
                return "/sap/bc/adt/functions/groups/" + encodedName;
            case "package":
                return "/sap/bc/adt/packages/" + encodedName;
            default:
                throw new IllegalArgumentException("Unsupported object type: " + objectType);
        }
    }

    private String buildAtcRunRequest(String objectUri, int maxFindings) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<atc:run maximumVerdicts=\"" + maxFindings + "\" xmlns:atc=\"http://www.sap.com/adt/atc\">" +
                "<objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">" +
                "<objectSet kind=\"inclusive\">" +
                "<adtcore:objectReferences>" +
                "<adtcore:objectReference adtcore:uri=\"" + objectUri + "\"/>" +
                "</adtcore:objectReferences>" +
                "</objectSet>" +
                "</objectSets>" +
                "</atc:run>";
    }

    /**
     * Extract ATC run ID from HTTP Location header.
     * SAP returns the run ID via Location header after async ATC run submission.
     *
     * @param response Full HTTP response with headers
     * @return Run ID extracted from Location header, or null if not found
     */
    private String extractRunId(HttpProxyResponse response) {
        if (response == null || response.getHeaders() == null) {
            log.error("No response or headers available to extract run ID");
            return null;
        }

        // Try both "location" and "Location" (case variations)
        String locationHeader = response.getHeaders().get("location");
        if (locationHeader == null) {
            locationHeader = response.getHeaders().get("Location");
        }

        if (locationHeader == null || locationHeader.isEmpty()) {
            log.error("No Location header in ATC run response. Available headers: {}",
                    response.getHeaders().keySet());
            return null;
        }

        // Extract last path segment from /sap/bc/adt/atc/runs/{runId}
        String[] parts = locationHeader.split("/");
        if (parts.length > 0) {
            String runId = parts[parts.length - 1];
            log.info("Extracted run ID from Location header: {}", runId);
            return runId;
        }

        log.error("Could not extract run ID from Location header: {}", locationHeader);
        return null;
    }

    /**
     * Fetch the system's default ATC check variant from customizing.
     *
     * @param finalSessionId Session ID for CSRF token management
     * @return System default check variant name or null if not configured
     */
    private String getSystemDefaultCheckVariant(String finalSessionId) {
        try {
            String customizingPath = "/sap/bc/adt/atc/customizing";
            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessGetViaRfc(dest, session, customizingPath, null,
                        "application/xml, application/vnd.sap.atc.customizing-v1+xml, application/vnd.sap.atc.customizing-v2+xml");
            });

            // Extract systemCheckVariant property from customizing XML
            // Example: <property name="systemCheckVariant" value="DEFAULT_VARIANT"/>
            int idx = response.indexOf("name=\"systemCheckVariant\"");
            if (idx >= 0) {
                int valueStart = response.indexOf("value=\"", idx);
                if (valueStart >= 0) {
                    valueStart += 7; // length of 'value="'
                    int valueEnd = response.indexOf("\"", valueStart);
                    if (valueEnd > valueStart) {
                        String variant = response.substring(valueStart, valueEnd);
                        log.info("System default check variant: {}", variant);
                        return variant;
                    }
                }
            }

            log.info("No system default check variant configured");
            return null;
        } catch (Exception e) {
            log.warn("Could not fetch system default check variant: {}", e.getMessage());
            return null;
        }
    }

    private String encodeObjectName(String name) {
        if (name.startsWith("/")) {
            return name.replace("/", "%2F");
        }
        return name;
    }
}
