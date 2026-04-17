package com.sapjco.mcp.handlers.debug;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for DebugStartSession tool.
 *
 * <p>Behavior depends on include_type:
 * <ul>
 *   <li><b>testclasses (default)</b>: Blocking - sets breakpoint, triggers unit tests,
 *       waits for breakpoint hit, attaches, and returns a ready-to-use debug session.</li>
 *   <li><b>other include types</b>: Non-blocking - sets breakpoint, starts listener in
 *       background, and returns immediately. The caller must then trigger execution externally
 *       (e.g., RunAbapUnit with debug_mode=true) and call DebugWaitForBreakpoint to complete
 *       the session setup.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebugStartSessionHandler implements ToolHandler {

    private final SystemConfigLoader systemConfigLoader;
    private final JcoSessionManager jcoSessionManager;
    private final AdtClient adtClient;

    private static final List<String> VALID_CLASS_INCLUDE_TYPES =
            List.of("testclasses", "definitions", "implementations", "macros", "main");

    /**
     * Timeout multiplier for split workflow listener.
     * For non-testclasses includes, the listener starts immediately but test triggering
     * happens later. We need extra time for:
     *   1. Time to trigger the test (5-60s depending on user/tool)
     *   2. Test execution time (often 60-120s for CTF tests)
     *   3. Original timeout window for breakpoint hit
     */
    private static final int SPLIT_WORKFLOW_TIMEOUT_MULTIPLIER = 3;

    /**
     * Pending debug listeners keyed by session_id.
     * Used by {@link DebugWaitForBreakpointHandler} to retrieve background listener futures.
     */
    private static final ConcurrentHashMap<String, DebugListenerState> pendingListeners = new ConcurrentHashMap<>();

    /**
     * Clean up a pending debug listener for a session.
     * Called by DestroySessionHandler when cleaning up sessions.
     * @param sessionId the session ID to clean up
     * @return true if a listener was found and cleaned up
     */
    public static boolean cleanupPendingListener(String sessionId) {
        DebugListenerState state = pendingListeners.remove(sessionId);
        if (state != null) {
            state.listenerFuture.cancel(true);
            return true;
        }
        return false;
    }

    /**
     * Get a pending listener state for a session (package-private for DebugWaitForBreakpointHandler).
     */
    static DebugListenerState getPendingListener(String sessionId) {
        return pendingListeners.get(sessionId);
    }

    /**
     * Remove a pending listener state (package-private for DebugWaitForBreakpointHandler).
     */
    static DebugListenerState removePendingListener(String sessionId) {
        return pendingListeners.remove(sessionId);
    }

    /**
     * Add a pending listener state (package-private for testing).
     */
    static void putPendingListener(String sessionId, DebugListenerState state) {
        pendingListeners.put(sessionId, state);
    }

    /**
     * Check if a pending listener exists for a session (package-private for testing).
     */
    static boolean hasPendingListener(String sessionId) {
        return pendingListeners.containsKey(sessionId);
    }

    /**
     * Clear all pending listeners (package-private for testing).
     */
    static void clearPendingListeners() {
        pendingListeners.clear();
    }

    /**
     * Result of the listener + attach operation performed in the background.
     * Captures either success (with debuggeeId) or failure (with error).
     */
    static class AttachResult {
        final String debuggeeId;
        final String listenerResponse;
        final Exception error;

        AttachResult(String debuggeeId, String listenerResponse, Exception error) {
            this.debuggeeId = debuggeeId;
            this.listenerResponse = listenerResponse;
            this.error = error;
        }

        boolean isSuccess() {
            return error == null && debuggeeId != null && !debuggeeId.isEmpty();
        }

        static AttachResult success(String debuggeeId, String listenerResponse) {
            return new AttachResult(debuggeeId, listenerResponse, null);
        }

        static AttachResult failure(Exception error) {
            return new AttachResult(null, null, error);
        }

        static AttachResult noDebuggee(String listenerResponse) {
            return new AttachResult(null, listenerResponse, null);
        }
    }

    /**
     * State for a background debug listener waiting for a breakpoint hit.
     * The listenerFuture now returns AttachResult (includes attach + settings).
     */
    static class DebugListenerState {
        final CompletableFuture<AttachResult> listenerFuture;
        final String terminalId;
        final String ideId;
        final String requestUser;
        final String objectName;
        final String objectUri;
        final int lineNumber;
        final int timeoutSeconds;
        final long startedAt;

        DebugListenerState(CompletableFuture<AttachResult> listenerFuture, String terminalId, String ideId,
                           String requestUser, String objectName, String objectUri,
                           int lineNumber, int timeoutSeconds) {
            this.listenerFuture = listenerFuture;
            this.terminalId = terminalId;
            this.ideId = ideId;
            this.requestUser = requestUser;
            this.objectName = objectName;
            this.objectUri = objectUri;
            this.lineNumber = lineNumber;
            this.timeoutSeconds = timeoutSeconds;
            this.startedAt = System.currentTimeMillis();
        }
    }

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("session_id",
                        "Session ID from CreateSession (REQUIRED for HTTP session affinity)")
                .requiredString("object_name",
                        "Name of the ABAP class with unit tests (e.g., \"ZCL_MY_CLASS\" or \"/SCMTMS/CL_TRS_E_MODEL\"). " +
                        "Namespaced objects are automatically URL-encoded.")
                .requiredEnum("object_type",
                        "Type of object: class, interface, program, function_group, include.",
                        List.of("class", "interface", "program", "function_group", "include"))
                .requiredNumber("line_number",
                        "Line number for the breakpoint in the specified include. Line numbers match exactly " +
                        "what you write - count lines starting from 1, including blank lines.")
                .requiredString("request_user",
                        "SAP username. REQUIRED for SNC-authenticated systems. Ask the user for their SAP username.")
                .optionalEnum("include_type",
                        "For classes only: which include to set breakpoint in. Default: \"testclasses\" " +
                        "(auto-triggers unit tests). Other values (definitions, implementations, macros, main) " +
                        "require external execution trigger.",
                        List.of("testclasses", "definitions", "implementations", "macros", "main"),
                        "testclasses")
                .optionalNumber("timeout_seconds",
                        "Timeout in seconds for waiting for breakpoint (default: 120)")
                .buildTool(
                        "DebugStartSession",
                        "Start a complete debug session in one operation: set breakpoint, trigger test, " +
                        "wait for breakpoint hit, and attach. This combines the manual workflow " +
                        "(DebugSetBreakpoint → DebugStartListener → RunAbapUnit → DebugAttach) into a single " +
                        "synchronous call. After completion, use DebugGetStack, DebugGetVariables, DebugStep, " +
                        "DebugResume with the returned JCo session_id. " +
                        "For non-testclasses includes (definitions, implementations, macros, main): returns " +
                        "immediately after setting breakpoint and starting listener. Call DebugWaitForBreakpoint " +
                        "after triggering execution externally (e.g., RunAbapUnit with debug_mode=true)."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String sessionId = (String) args.get("session_id");
        String objectName = (String) args.get("object_name");
        String objectType = (String) args.get("object_type");
        int lineNumber = ((Number) args.get("line_number")).intValue();
        String requestUser = (String) args.get("request_user");
        String includeType = args.get("include_type") != null
                ? (String) args.get("include_type")
                : "testclasses";
        int timeoutSeconds = args.get("timeout_seconds") != null
                ? ((Number) args.get("timeout_seconds")).intValue()
                : 120;

        if (sessionId == null || sessionId.isEmpty()) {
            return McpResponseFormatter.error("session_id is required");
        }
        if (objectName == null || objectName.isEmpty()) {
            return McpResponseFormatter.error("object_name is required");
        }
        if (objectType == null || objectType.isEmpty()) {
            return McpResponseFormatter.error("object_type is required");
        }
        if (requestUser == null || requestUser.isEmpty()) {
            return McpResponseFormatter.error("request_user is required. Please provide your SAP username.");
        }

        log.info("DebugStartSession called: {} line {} (type: {}, include: {}, user: {}, timeout: {}s)",
                objectName, lineNumber, objectType, includeType, requestUser, timeoutSeconds);

        String testTriggerSessionId = null;

        try {
            JcoSession session = jcoSessionManager.getSession(sessionId);
            String systemId = session.getSystemId();
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    systemId,
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient()
            );

            // Determine if we should auto-trigger unit tests (only for testclasses)
            boolean autoTriggerTests = objectType.equalsIgnoreCase("class") &&
                    includeType.equalsIgnoreCase("testclasses");

            // ═══════════════════════════════════════════════════════════
            // STEP 0a: User-level cleanup - clear ANY debug state for this user
            // This handles the case where a new JCo session is created but SAP
            // still has a lingering debug attachment from a previous session
            // for the same user. Without this, consecutive debug sessions with
            // new JCo sessions fail with "Debuggee already attached" error.
            // ═══════════════════════════════════════════════════════════
            try {
                Map<String, String> userCleanupParams = new LinkedHashMap<>();
                userCleanupParams.put("debuggingMode", "user");
                userCleanupParams.put("requestUser", requestUser);
                // No terminalId/ideId = clean up ALL debug state for this user

                jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                    adtClient.debugDeleteBreakpointRest(dest, userCleanupParams,
                            sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
                    return null;
                });
                log.info("✓ User-level debug cleanup completed for: {}", requestUser);
            } catch (Exception e) {
                // Non-fatal - may not have any debug state to clean
                log.debug("User-level debug cleanup (non-fatal): {}", e.getMessage());
            }

            // ═══════════════════════════════════════════════════════════
            // STEP 0b: Clean up any previous debug state for this session
            // This handles cleanup when reusing the same JCo session for
            // consecutive debug runs (complements user-level cleanup above).
            // ═══════════════════════════════════════════════════════════

            // Clean up pending listener from previous debug session (if any)
            DebugListenerState oldState = pendingListeners.remove(sessionId);
            if (oldState != null) {
                log.info("Cleaning up previous pending listener for session: {}", sessionId);
                oldState.listenerFuture.cancel(true);

                // Also try to clean up SAP-side breakpoints from the old listener
                try {
                    Map<String, String> deleteParams = new LinkedHashMap<>();
                    deleteParams.put("debuggingMode", "user");
                    deleteParams.put("terminalId", oldState.terminalId);
                    deleteParams.put("ideId", oldState.ideId);
                    deleteParams.put("requestUser", oldState.requestUser);

                    jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                        adtClient.debugDeleteBreakpointRest(dest, deleteParams,
                                sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
                        return null;
                    });
                    log.info("✓ Previous breakpoints cleaned up");
                } catch (Exception e) {
                    // Ignore - may not exist or already cleaned up
                    log.debug("Previous breakpoint cleanup (non-fatal): {}", e.getMessage());
                }
            }

            // Clean up debug state stored in session (from previous attach)
            if (session.hasActiveDebugSession()) {
                log.info("Cleaning up previous debug session state: terminalId={}, ideId={}",
                        session.getDebugTerminalId(), session.getDebugIdeId());

                // CRITICAL: First detach from debugger to clear singleton ref_session
                // Without this, CL_TPDAPI_SERVICE still holds a reference and will
                // reject new attach attempts with "Debuggee already attached" error.
                // The detachDebugger endpoint triggers: end_debugger() -> raises
                // debuggee_detached event -> clears ref_session in singleton.
                try {
                    jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                        adtClient.debugDetach(dest, sess.getHttpClient(),
                                sess.getCsrfTokenCache(), sess);
                        return null;
                    });
                    log.info("✓ Detached from previous debugger session");
                } catch (Exception e) {
                    // Non-fatal - may not be attached
                    log.debug("Debugger detach (non-fatal): {}", e.getMessage());
                }

                // Then clean up breakpoints/listeners
                try {
                    Map<String, String> deleteParams = new LinkedHashMap<>();
                    deleteParams.put("debuggingMode", "user");
                    deleteParams.put("terminalId", session.getDebugTerminalId());
                    deleteParams.put("ideId", session.getDebugIdeId());
                    if (session.getDebugRequestUser() != null) {
                        deleteParams.put("requestUser", session.getDebugRequestUser());
                    }

                    jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                        adtClient.debugDeleteBreakpointRest(dest, deleteParams,
                                sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
                        return null;
                    });
                    log.info("✓ Previous debug session cleaned up");
                } catch (Exception e) {
                    // Ignore - may not exist or already cleaned up
                    log.debug("Previous debug session cleanup (non-fatal): {}", e.getMessage());
                }
                session.clearDebugState();
            }

            // Generate terminal/IDE IDs
            String terminalId = generateUUID();
            String ideId = generateUUID();

            // Build object URI for breakpoint
            String objectUri = buildObjectUri(objectType, objectName, includeType);

            // ═══════════════════════════════════════════════════════════
            // STEP 1: Set Breakpoint via ADT REST
            // ═══════════════════════════════════════════════════════════
            log.info("Step 1: Setting breakpoint at line {}", lineNumber);

            String clientId = "BP_" + System.currentTimeMillis();
            String breakpointXml = buildBreakpointXml(requestUser, terminalId, ideId, objectUri, lineNumber, clientId);

            String breakpointResponse = jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                return adtClient.debugSetBreakpointRest(dest, breakpointXml,
                        sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
            });

            log.info("✓ Breakpoint set successfully");

            // ═══════════════════════════════════════════════════════════
            // STEP 2: Start Debug Listener
            // ═══════════════════════════════════════════════════════════
            log.info("Step 2: Starting debug listener (timeout: {}s)", timeoutSeconds);

            Map<String, String> listenerParams = new LinkedHashMap<>();
            listenerParams.put("debuggingMode", "user");
            listenerParams.put("requestUser", requestUser);
            listenerParams.put("terminalId", terminalId);
            listenerParams.put("ideId", ideId);
            listenerParams.put("checkConflict", "true");
            listenerParams.put("isNotifiedOnConflict", "true");

            // Start listener as CompletableFuture
            // For split workflow, use extended timeout (see SPLIT_WORKFLOW_TIMEOUT_MULTIPLIER).
            // The actual wait timeout in DebugWaitForBreakpoint remains timeoutSeconds.
            //
            // OPTION 5 FIX: The listener future now performs attach + settings immediately
            // after breakpoint hit to avoid SAP debuggee context expiration.
            // This ensures attach happens in the same JCo session context.
            int listenerTimeout = timeoutSeconds * SPLIT_WORKFLOW_TIMEOUT_MULTIPLIER;
            final String finalRequestUser = requestUser;
            CompletableFuture<AttachResult> listenerFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    // Step 1: Wait for breakpoint hit
                    String listenerResponse = jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                        return adtClient.debugStartListenerRest(dest, listenerParams,
                                listenerTimeout * 1000, sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
                    }, listenerTimeout + 10);

                    // Step 2: Extract debuggee ID
                    String debuggeeId = extractDebuggeeId(listenerResponse);
                    if (debuggeeId == null || debuggeeId.isEmpty()) {
                        log.warn("No debuggee detected in listener response");
                        return AttachResult.noDebuggee(listenerResponse);
                    }

                    log.info("✓ Debuggee detected in listener: {}", debuggeeId);

                    // Step 3: Attach immediately (same JCo context = same SAP session)
                    log.info("Attaching to debuggee in listener thread: {}", debuggeeId);
                    jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                        return adtClient.debugAttach(dest, debuggeeId, finalRequestUser, true,
                                sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
                    });
                    log.info("✓ Attached to debuggee in listener thread");

                    // Step 4: Set debugger settings
                    jcoSessionManager.executeInContext(sessionId, (dest, sess) -> {
                        return adtClient.debugSetSettings(dest, sess.getHttpClient(), sess.getCsrfTokenCache(), sess);
                    });
                    log.info("✓ Debugger settings applied in listener thread");

                    return AttachResult.success(debuggeeId, listenerResponse);

                } catch (Exception e) {
                    log.error("Listener/attach failed: {}", e.getMessage());
                    return AttachResult.failure(e);
                }
            });

            // ═══════════════════════════════════════════════════════════
            // SPLIT: Non-testclasses → return immediately
            //        Testclasses → continue with blocking flow
            // ═══════════════════════════════════════════════════════════
            if (!autoTriggerTests) {
                // Store listener state for DebugWaitForBreakpoint to retrieve
                pendingListeners.put(sessionId, new DebugListenerState(
                        listenerFuture, terminalId, ideId, requestUser,
                        objectName, objectUri, lineNumber, timeoutSeconds));

                log.info("Non-testclasses mode: returning immediately. Listener running in background.");

                StringBuilder sb = new StringBuilder();
                sb.append("Breakpoint Set - Waiting for Trigger\n\n");
                sb.append("Session Information:\n");
                sb.append(String.format(Locale.ROOT, "  Session ID: %s\n", sessionId));
                sb.append(String.format(Locale.ROOT, "  Terminal ID: %s\n", terminalId));
                sb.append(String.format(Locale.ROOT, "  IDE ID: %s\n", ideId));
                sb.append("\n");
                sb.append("Breakpoint Location:\n");
                sb.append(String.format(Locale.ROOT, "  Object: %s (%s)\n", objectName, objectType));
                if (objectType.equalsIgnoreCase("class")) {
                    sb.append(String.format(Locale.ROOT, "  Include: %s\n", includeType));
                }
                sb.append(String.format(Locale.ROOT, "  URI: %s\n", objectUri));
                sb.append(String.format(Locale.ROOT, "  Line: %d\n", lineNumber));
                sb.append("\n");
                sb.append("Status: Listener active, waiting for breakpoint hit\n\n");
                sb.append("Next Steps:\n\n");
                sb.append("  1. Trigger execution that hits the breakpoint:\n");
                sb.append("     - RunAbapUnit(object_name: \"<TEST_CLASS>\", debug_mode: true)\n");
                sb.append("     - Or trigger from SAP GUI / HTTP / RFC\n\n");
                sb.append("  2. Then call:\n");
                sb.append("     DebugWaitForBreakpoint(session_id: \"").append(sessionId).append("\")\n");
                sb.append("     → Waits for breakpoint hit, attaches to debuggee\n\n");
                sb.append(String.format(Locale.ROOT,
                        "Listener timeout: %d seconds from now.", timeoutSeconds));

                return McpResponseFormatter.success(systemHeader, sb.toString());
            }

            // ═══════════════════════════════════════════════════════════
            // TESTCLASSES PATH: Create trigger session + auto-trigger
            // CRITICAL: JCo sessions have single-threaded executors.
            // The listener blocks waiting for debuggee, so the test
            // trigger MUST use a separate session for concurrent execution.
            // ═══════════════════════════════════════════════════════════
            log.info("Step 3: Creating test trigger session");
            CreateSessionRequest testSessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
            testTriggerSessionId = jcoSessionManager.createSession(testSessionRequest);
            log.info("✓ Test trigger session created: {}", testTriggerSessionId);

            final String finalTestTriggerSessionId = testTriggerSessionId;

            log.info("Step 4: Waiting 2s before triggering unit tests...");
            Thread.sleep(2000);

            log.info("Triggering unit tests for {}", objectName);
            String testRunXml = buildTestRunXml(objectType, objectName);

            // Fire and forget - the test will block at breakpoint
            CompletableFuture.runAsync(() -> {
                try {
                    jcoSessionManager.executeInContext(finalTestTriggerSessionId, (dest, sess) -> {
                        return adtClient.statelessPostViaRfc(dest, sess,
                                "/sap/bc/adt/abapunit/testruns", null, testRunXml,
                                "application/vnd.sap.adt.abapunit.testruns.config.v4+xml",
                                "application/vnd.sap.adt.abapunit.testruns.result.v2+xml").getBody();
                    });
                } catch (Exception e) {
                    // Expected - test blocks at breakpoint
                    log.debug("Unit test trigger completed or blocked: {}", e.getMessage());
                }
            });

            // ═══════════════════════════════════════════════════════════
            // STEP 5: Wait for listener + attach to complete
            // The listener future now includes attach + settings (Option 5 fix)
            // ═══════════════════════════════════════════════════════════
            log.info("Step 5: Waiting for debuggee...");

            AttachResult attachResult;
            try {
                attachResult = listenerFuture.get(timeoutSeconds + 10, TimeUnit.SECONDS);
            } catch (Exception e) {
                return McpResponseFormatter.error(
                        "Debug listener timed out. No debuggee was detected.\n\n" +
                        "Possible reasons:\n" +
                        "  - The test class has no unit tests\n" +
                        "  - The unit tests don't execute the line where the breakpoint is set\n" +
                        "  - The line number is in a method that is not called during tests");
            }

            // Check attach result
            if (attachResult.error != null) {
                return McpResponseFormatter.error(
                        "Debug attach failed: " + attachResult.error.getMessage());
            }

            String debuggeeId = attachResult.debuggeeId;
            if (debuggeeId == null || debuggeeId.isEmpty()) {
                return McpResponseFormatter.error(
                        "No debuggee detected. Possible reasons:\n" +
                        "  - Breakpoint was not hit (code path not executed)\n" +
                        "  - Object has no unit tests\n" +
                        "  - Line number is invalid or unreachable\n\n" +
                        "Listener response:\n" + attachResult.listenerResponse);
            }

            log.info("✓ Debuggee detected and attached: {}", debuggeeId);

            // Clean up test trigger session
            if (testTriggerSessionId != null) {
                try {
                    jcoSessionManager.destroySession(testTriggerSessionId);
                    testTriggerSessionId = null; // Mark as cleaned up
                } catch (Exception e) {
                    log.warn("Failed to cleanup test trigger session: {}", e.getMessage());
                }
            }

            // Store debug state in session for auto-cleanup
            session.setDebugTerminalId(terminalId);
            session.setDebugIdeId(ideId);
            session.setDebugRequestUser(requestUser);

            // ═══════════════════════════════════════════════════════════
            // SUCCESS - Format Response
            // ═══════════════════════════════════════════════════════════
            StringBuilder sb = new StringBuilder();
            sb.append("🎯 Debug Session Started Successfully!\n\n");
            sb.append("Session Information:\n");
            sb.append(String.format(Locale.ROOT, "  Session ID: %s\n", sessionId));
            sb.append(String.format(Locale.ROOT, "  Debuggee ID: %s\n", debuggeeId));
            sb.append(String.format(Locale.ROOT, "  Terminal ID: %s\n", terminalId));
            sb.append(String.format(Locale.ROOT, "  IDE ID: %s\n", ideId));
            sb.append("\n");
            sb.append("Breakpoint Location:\n");
            sb.append(String.format(Locale.ROOT, "  Object: %s (%s)\n", objectName, objectType));
            if (objectType.equalsIgnoreCase("class")) {
                sb.append(String.format(Locale.ROOT, "  Include: %s\n", includeType));
            }
            sb.append(String.format(Locale.ROOT, "  URI: %s\n", objectUri));
            sb.append(String.format(Locale.ROOT, "  Line: %d\n", lineNumber));
            sb.append("\n");
            sb.append("Status: ✅ Attached and paused at breakpoint\n\n");
            sb.append("Next Steps - Use these commands with session_id=").append(sessionId).append(":\n\n");
            sb.append("  1. DebugGetStack(session_id)\n");
            sb.append("     → See current execution position and call stack\n\n");
            sb.append("  2. DebugGetVariables(session_id, \"@LOCALS\")\n");
            sb.append("     → Inspect local variables at current position\n\n");
            sb.append("  3. DebugStep(session_id, \"stepOver\")\n");
            sb.append("     → Execute next line (options: stepOver, stepInto, stepReturn)\n\n");
            sb.append("  4. DebugSetVariable(session_id, variable_id, \"new_value\")\n");
            sb.append("     → Modify variable values during debugging\n\n");
            sb.append("  5. DebugResume(session_id)\n");
            sb.append("     → Continue execution until program ends or next breakpoint\n\n");
            sb.append("  6. DestroySession(session_id) when done\n");
            sb.append("     → Clean up debug session (IMPORTANT!)\n\n");
            sb.append("⚠️  IMPORTANT: Use the Session ID above for all debug commands!");

            return McpResponseFormatter.success(systemHeader, sb.toString());

        } catch (IllegalArgumentException e) {
            return McpResponseFormatter.error("Session not found: " + sessionId);
        } catch (Exception e) {
            log.error("DebugStartSession failed", e);
            return McpResponseFormatter.error(e);
        } finally {
            // Clean up test trigger session if still active
            if (testTriggerSessionId != null) {
                try {
                    jcoSessionManager.destroySession(testTriggerSessionId);
                } catch (Exception e) {
                    log.warn("Failed to cleanup test trigger session in finally: {}", e.getMessage());
                }
            }
        }
    }

    private String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
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

    /**
     * Build request XML for /testruns endpoint (runConfiguration format).
     * Uses URI-based object references compatible with test discovery.
     */
    private String buildTestRunXml(String objectType, String objectName) {
        String objectUri = buildObjectUri(objectType, objectName, "testclasses");
        // Strip the /includes/testclasses suffix — /testruns needs the class root URI
        String classUri;
        switch (objectType.toLowerCase()) {
            case "class":
                classUri = "/sap/bc/adt/oo/classes/" + encodeObjectName(objectName);
                break;
            case "program":
            case "include":
                classUri = "/sap/bc/adt/programs/programs/" + encodeObjectName(objectName);
                break;
            case "function_group":
                classUri = "/sap/bc/adt/functions/groups/" + encodeObjectName(objectName);
                break;
            default:
                classUri = "/sap/bc/adt/oo/classes/" + encodeObjectName(objectName);
                break;
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<aunit:runConfiguration xmlns:aunit=\"http://www.sap.com/adt/aunit\">\n" +
                "  <external>\n" +
                "    <coverage active=\"false\"/>\n" +
                "  </external>\n" +
                "  <options>\n" +
                "    <uriType value=\"semantic\"/>\n" +
                "    <testDeterminationStrategy sameProgram=\"true\" assignedTests=\"false\"/>\n" +
                "    <testRiskLevels harmless=\"true\" dangerous=\"true\" critical=\"true\"/>\n" +
                "    <testDurations short=\"true\" medium=\"true\" long=\"true\"/>\n" +
                "    <withNavigationUri enabled=\"true\"/>\n" +
                "  </options>\n" +
                "  <adtcore:objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">\n" +
                "    <objectSet kind=\"inclusive\">\n" +
                "      <adtcore:objectReferences>\n" +
                "        <adtcore:objectReference adtcore:uri=\"" + classUri + "\"/>\n" +
                "      </adtcore:objectReferences>\n" +
                "    </objectSet>\n" +
                "  </adtcore:objectSets>\n" +
                "</aunit:runConfiguration>";
    }

    /**
     * Extract debuggee ID from listener response XML.
     * Package-private so {@link DebugWaitForBreakpointHandler} can reuse it.
     */
    static String extractDebuggeeId(String responseXml) {
        // Try different patterns to extract debuggee ID
        Pattern[] patterns = {
                Pattern.compile("<DEBUGGEE_ID>([^<]+)</DEBUGGEE_ID>"),
                Pattern.compile("debuggeeId=\"([^\"]+)\""),
                Pattern.compile("<debuggee[^>]*id=\"([^\"]+)\""),
                Pattern.compile("<dbg:debuggee[^>]*dbg:id=\"([^\"]+)\"")
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
