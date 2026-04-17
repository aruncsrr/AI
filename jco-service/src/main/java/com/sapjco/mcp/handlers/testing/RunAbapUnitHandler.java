package com.sapjco.mcp.handlers.testing;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateSessionRequest;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for RunAbapUnit tool.
 * Runs ABAP Unit tests for an ABAP object via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunAbapUnitHandler implements ToolHandler {

    private static final Pattern COVERAGE_URI_PATTERN = Pattern.compile("<measurement[^>]+href=\"([^\"]+)\"");
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final String CONTENT_TYPE_TESTRUNS_V4 = "application/vnd.sap.adt.abapunit.testruns.config.v4+xml";
    private static final String ACCEPT_TYPE_TESTRUNS_V2 = "application/vnd.sap.adt.abapunit.testruns.result.v2+xml";

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;
    private final FileStorageService fileStorageService;
    private final MetadataExtractorService metadataExtractorService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name",
                        "Name of the ABAP object to test (e.g., \"ZCL_MY_CLASS\", \"ZTEST_PROGRAM\")")
                .requiredEnum("object_type",
                        "Type of object: class, program, function_group, include, package",
                        List.of("class", "program", "function_group", "include", "package"))
                .optionalBoolean("with_coverage",
                        "Include code coverage data in results (default: false)")
                .optionalBoolean("debug_mode",
                        "Run in debug mode (pauses at external breakpoints). When true, uses /runs endpoint " +
                        "which supports debugging. Set breakpoints with DebugSetBreakpoint and start listener " +
                        "with DebugStartListener BEFORE calling this. Default: false")
                .optionalEnum("test_scope",
                        "Scope of test discovery: \"own_tests\" (default, tests in same program), " +
                        "\"foreign_tests\" (tests in external classes assigned to this object via TAUNIT_TEST_REL), " +
                        "\"all_tests\" (both own and foreign)",
                        List.of("own_tests", "foreign_tests", "all_tests"), "own_tests")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session will " +
                        "be created automatically. IMPORTANT: For debug mode, use the SAME session_id used " +
                        "for DebugSetBreakpoint and DebugStartListener.")
                .optionalNumber("timeout_seconds",
                        "Timeout in seconds for test execution (default: 120). Increase for large test suites.")
                .buildTool(
                        "RunAbapUnit",
                        "Run ABAP Unit tests for an ABAP object. Executes unit tests and returns results " +
                        "including pass/fail counts, execution time, and optional coverage data. Supports " +
                        "classes, programs, function groups, and packages. Use test_scope to include foreign " +
                        "(assigned) tests from external test classes. Use debug_mode=true to pause at " +
                        "external breakpoints (requires DebugSetBreakpoint + DebugStartListener first). " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        boolean withCoverage = args.get("with_coverage") != null ? (Boolean) args.get("with_coverage") : false;
        boolean debugMode = args.get("debug_mode") != null ? (Boolean) args.get("debug_mode") : false;
        String testScope = args.get("test_scope") != null ? (String) args.get("test_scope") : "own_tests";
        String sessionId = (String) args.get("session_id");
        int timeoutSeconds = args.get("timeout_seconds") != null
                ? ((Number) args.get("timeout_seconds")).intValue()
                : DEFAULT_TIMEOUT_SECONDS;

        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }

        log.info("RunAbapUnit called: {} (type: {}, coverage: {}, debug: {}, scope: {}, timeout: {}s, session: {})",
                objectName, objectType, withCoverage, debugMode, testScope, timeoutSeconds, sessionId != null ? sessionId : "(temp)");

        String tempSessionId = null;

        try {
            ResolvedSystem resolved = systemConfigLoader.getSystem(null);

            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
            }

            final String finalSessionId = sessionId;

            // Build ABAP Unit request body
            String objectUri = buildObjectUri(objectType, objectName);
            String requestBody;
            String path;
            String contentType;
            String acceptType = ACCEPT_TYPE_TESTRUNS_V2;

            // Debug mode: fire-and-forget via /testruns in a background thread.
            // The /testruns endpoint is synchronous and will block at the breakpoint,
            // allowing the debug listener to catch the hit. We return immediately so
            // the caller can invoke DebugWaitForBreakpoint before the HTTP timeout
            // kills the debuggee.
            if (debugMode) {
                path = "/sap/bc/adt/abapunit/testruns";
                contentType = CONTENT_TYPE_TESTRUNS_V4;
                requestBody = buildAbapUnitRequest(objectUri, false, testScope);

                // Create a dedicated session for the background trigger
                CreateSessionRequest triggerSessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                String triggerSessionId = jcoSessionManager.createSession(triggerSessionRequest);
                log.info("Created trigger session {} for debug-mode fire-and-forget", triggerSessionId);

                final String bgPath = path;
                final String bgContentType = contentType;
                final String bgRequestBody = requestBody;
                final int bgTimeout = timeoutSeconds;

                CompletableFuture.runAsync(() -> {
                    try {
                        jcoSessionManager.executeInContext(triggerSessionId, (dest, session) -> {
                            return adtClient.statelessPostViaRfc(dest, session, bgPath, null, bgRequestBody,
                                    bgContentType, acceptType).getBody();
                        }, bgTimeout);
                    } catch (Exception e) {
                        log.debug("Debug-mode test trigger completed or timed out: {}", e.getMessage());
                    } finally {
                        try {
                            jcoSessionManager.destroySession(triggerSessionId);
                        } catch (Exception e) {
                            log.warn("Failed to destroy trigger session: {}", e.getMessage());
                        }
                    }
                });

                String systemHeader = McpResponseFormatter.formatSystemHeader(
                        resolved.getSystemId(),
                        resolved.getConfig().getEffectiveHost(),
                        resolved.getConfig().getClient()
                );

                StringBuilder sb = new StringBuilder();
                sb.append(String.format("ABAP Unit (debug mode): %s (%s)\n\n", objectName.toUpperCase(), objectType));
                sb.append("Tests triggered in background via /testruns (fire-and-forget).\n");
                sb.append("The test execution will pause when it hits a breakpoint.\n\n");
                sb.append("Next step: Call DebugWaitForBreakpoint to attach to the debuggee.\n");

                return McpResponseFormatter.success(systemHeader, sb.toString());
            }

            // Normal mode: synchronous /testruns
            path = "/sap/bc/adt/abapunit/testruns";
            contentType = CONTENT_TYPE_TESTRUNS_V4;
            requestBody = buildAbapUnitRequest(objectUri, withCoverage, testScope);

            String response = jcoSessionManager.executeInContext(finalSessionId, (dest, session) -> {
                return adtClient.statelessPostViaRfc(dest, session, path, null, requestBody,
                        contentType, acceptType).getBody();
            }, timeoutSeconds);

            // Build system identifier for file path
            String systemFileId = resolved.getSystemId() + "_" + resolved.getConfig().getClient();

            // Write results to file
            String sanitizedName = fileStorageService.sanitizeFilename(objectName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_ABAP_UNIT, sanitizedName + "_tests", response);
            long byteSize = fileStorageService.getByteSize(filePath);

            log.info("Wrote ABAP Unit results to file: {} ({} bytes)", filePath, byteSize);

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractAbapUnitMetadata(response);

            // Extract coverage URI if present
            String coverageUri = null;
            if (withCoverage) {
                Matcher matcher = COVERAGE_URI_PATTERN.matcher(response);
                if (matcher.find()) {
                    coverageUri = matcher.group(1);
                }
            }

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Build summary response
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("ABAP Unit: %s (%s)\n", objectName.toUpperCase(), objectType));
            if (!"own_tests".equals(testScope)) {
                String scopeLabel = "foreign_tests".equals(testScope) ? "foreign tests" : "all tests (own + foreign)";
                sb.append(String.format("Test scope: %s\n", scopeLabel));
            }
            sb.append(metadataExtractorService.formatMetadata(metadata));
            if (coverageUri != null) {
                sb.append(String.format("Coverage URI: %s\n", coverageUri));
            }
            sb.append(String.format(java.util.Locale.ROOT, "Size: %,d bytes\n", byteSize));
            sb.append(String.format("File: %s\n", filePath));

            // Add content excerpt
            sb.append(fileStorageService.formatExcerptSection(response, FileStorageService.EXT_XML));

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (Exception e) {
            log.error("RunAbapUnit failed for {}", objectName, e);
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

    /**
     * Build request body for /testruns endpoint (runConfiguration format).
     * Uses V4 content type which supports testDeterminationStrategy.
     *
     * @param objectUri   ADT object URI
     * @param withCoverage whether to include coverage measurement
     * @param testScope   test discovery scope: own_tests, foreign_tests, or all_tests
     */
    private String buildAbapUnitRequest(String objectUri, boolean withCoverage, String testScope) {
        // Map test_scope to XML attributes
        boolean sameProgram;
        boolean assignedTests;
        switch (testScope) {
            case "foreign_tests":
                sameProgram = false;
                assignedTests = true;
                break;
            case "all_tests":
                sameProgram = true;
                assignedTests = true;
                break;
            default: // own_tests
                sameProgram = true;
                assignedTests = false;
                break;
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<aunit:runConfiguration xmlns:aunit=\"http://www.sap.com/adt/aunit\">\n");
        xml.append("  <external>\n");
        xml.append("    <coverage active=\"").append(withCoverage).append("\"/>\n");
        xml.append("  </external>\n");
        xml.append("  <options>\n");
        xml.append("    <uriType value=\"semantic\"/>\n");
        xml.append("    <testDeterminationStrategy sameProgram=\"").append(sameProgram)
                .append("\" assignedTests=\"").append(assignedTests).append("\"/>\n");
        xml.append("    <testRiskLevels harmless=\"true\" dangerous=\"true\" critical=\"true\"/>\n");
        xml.append("    <testDurations short=\"true\" medium=\"true\" long=\"true\"/>\n");
        xml.append("    <withNavigationUri enabled=\"true\"/>\n");
        xml.append("  </options>\n");
        xml.append("  <adtcore:objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">\n");
        xml.append("    <objectSet kind=\"inclusive\">\n");
        xml.append("      <adtcore:objectReferences>\n");
        xml.append("        <adtcore:objectReference adtcore:uri=\"").append(objectUri).append("\"/>\n");
        xml.append("      </adtcore:objectReferences>\n");
        xml.append("    </objectSet>\n");
        xml.append("  </adtcore:objectSets>\n");
        xml.append("</aunit:runConfiguration>");
        return xml.toString();
    }

    private String encodeObjectName(String name) {
        if (name.startsWith("/")) {
            return name.replace("/", "%2F");
        }
        return name;
    }
}
