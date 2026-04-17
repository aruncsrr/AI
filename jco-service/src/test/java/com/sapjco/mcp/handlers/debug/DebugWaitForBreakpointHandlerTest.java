package com.sapjco.mcp.handlers.debug;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.handlers.debug.DebugStartSessionHandler.AttachResult;
import com.sapjco.mcp.handlers.debug.DebugStartSessionHandler.DebugListenerState;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DebugWaitForBreakpointHandler.
 * Tests the phase 2 of split debug workflow.
 *
 * Option 5 Fix: The listener future now returns AttachResult which includes
 * the attach + settings operations performed in the listener thread.
 */
class DebugWaitForBreakpointHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugWaitForBreakpointHandler handler;

    private static final String LISTENER_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <dbg:debuggee xmlns:dbg="http://www.sap.com/adt/debugger">
                <DEBUGGEE_ID>DEBUG123456</DEBUGGEE_ID>
            </dbg:debuggee>
            """;

    private static final String STACK_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger">
                <frame level="1"/>
            </dbg:stack>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
    }

    @AfterEach
    void tearDown() {
        DebugStartSessionHandler.clearPendingListeners();
    }

    @Test
    void waitForBreakpoint_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder().build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void waitForBreakpoint_emptySessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", "")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void waitForBreakpoint_sessionNotFound_returnsError() {
        when(jcoSessionManager.getSession("invalid-session"))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", "invalid-session")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void waitForBreakpoint_noPendingListener_returnsError() throws Exception {
        mockGetSession(TEST_SESSION_ID);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "No pending debug listener found");
    }

    @Test
    void waitForBreakpoint_listenerHasResult_verifiesAndReturnsSession() throws Exception {
        // Setup: session exists and pending listener has completed with AttachResult
        JcoSession session = mockGetSession(TEST_SESSION_ID);
        when(session.buildDebugCleanupParams()).thenReturn(null);  // No previous debug session
        // Only getStack call needed now (attach is done in listener)
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STACK_RESPONSE);

        // Create completed future with successful AttachResult
        AttachResult successResult = AttachResult.success("DEBUG123456", LISTENER_RESPONSE);
        CompletableFuture<AttachResult> completedFuture = CompletableFuture.completedFuture(successResult);
        DebugListenerState state = new DebugListenerState(
                completedFuture, "TERM123", "IDE456", "D052860",
                "ZCL_LOGIC", "/sap/bc/adt/oo/classes/zcl_logic/includes/implementations",
                42, 120);
        DebugStartSessionHandler.putPendingListener(TEST_SESSION_ID, state);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Debug Session Ready!");
        assertContains(result, "DEBUG123456");
        assertContains(result, "TERM123");
        assertContains(result, "IDE456");
        assertContains(result, "ZCL_LOGIC");
        assertContains(result, "Line: 42");

        // Verify pending listener was removed
        assertFalse(DebugStartSessionHandler.hasPendingListener(TEST_SESSION_ID),
                "Pending listener should be removed after successful wait");

        // Verify only getStack was called (attach is done in listener thread)
        verify(jcoSessionManager, times(1)).executeInContext(eq(TEST_SESSION_ID), any());
    }

    @Test
    void waitForBreakpoint_timeout_returnsError() throws Exception {
        mockGetSession(TEST_SESSION_ID);

        // Create a future that never completes
        CompletableFuture<AttachResult> neverCompleteFuture = new CompletableFuture<>();
        DebugListenerState state = new DebugListenerState(
                neverCompleteFuture, "TERM123", "IDE456", "D052860",
                "ZCL_LOGIC", "/sap/bc/adt/oo/classes/zcl_logic/includes/implementations",
                42, 120);
        DebugStartSessionHandler.putPendingListener(TEST_SESSION_ID, state);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("timeout_seconds", 1) // Short timeout for test
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "timed out");

        // Pending listener should NOT be removed on timeout (can retry)
        assertTrue(DebugStartSessionHandler.hasPendingListener(TEST_SESSION_ID),
                "Pending listener should remain after timeout (retryable)");
    }

    @Test
    void waitForBreakpoint_listenerFailed_returnsErrorAndCleansUp() throws Exception {
        mockGetSession(TEST_SESSION_ID);

        // Create future with failure AttachResult
        AttachResult failureResult = AttachResult.failure(new RuntimeException("Connection lost"));
        CompletableFuture<AttachResult> completedFuture = CompletableFuture.completedFuture(failureResult);
        DebugListenerState state = new DebugListenerState(
                completedFuture, "TERM123", "IDE456", "D052860",
                "ZCL_LOGIC", "/sap/bc/adt/oo/classes/zcl_logic/includes/implementations",
                42, 120);
        DebugStartSessionHandler.putPendingListener(TEST_SESSION_ID, state);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Connection lost");

        // Pending listener should be removed on failure
        assertFalse(DebugStartSessionHandler.hasPendingListener(TEST_SESSION_ID),
                "Pending listener should be removed after failure");
    }

    @Test
    void waitForBreakpoint_futureException_returnsErrorAndCleansUp() throws Exception {
        mockGetSession(TEST_SESSION_ID);

        // Create failed future (exception thrown by CompletableFuture itself)
        CompletableFuture<AttachResult> failedFuture = CompletableFuture.failedFuture(
                new RuntimeException("Async execution failed"));
        DebugListenerState state = new DebugListenerState(
                failedFuture, "TERM123", "IDE456", "D052860",
                "ZCL_LOGIC", "/sap/bc/adt/oo/classes/zcl_logic/includes/implementations",
                42, 120);
        DebugStartSessionHandler.putPendingListener(TEST_SESSION_ID, state);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Async execution failed");

        // Pending listener should be removed on exception
        assertFalse(DebugStartSessionHandler.hasPendingListener(TEST_SESSION_ID),
                "Pending listener should be removed after exception");
    }

    @Test
    void waitForBreakpoint_noDebuggeeInResponse_returnsError() throws Exception {
        mockGetSession(TEST_SESSION_ID);

        // Create AttachResult with no debuggee (noDebuggee result)
        String emptyResponse = """
                <?xml version="1.0" encoding="utf-8"?>
                <dbg:debuggee xmlns:dbg="http://www.sap.com/adt/debugger">
                </dbg:debuggee>
                """;
        AttachResult noDebuggeeResult = AttachResult.noDebuggee(emptyResponse);
        CompletableFuture<AttachResult> completedFuture = CompletableFuture.completedFuture(noDebuggeeResult);
        DebugListenerState state = new DebugListenerState(
                completedFuture, "TERM123", "IDE456", "D052860",
                "ZCL_LOGIC", "/sap/bc/adt/oo/classes/zcl_logic/includes/implementations",
                42, 120);
        DebugStartSessionHandler.putPendingListener(TEST_SESSION_ID, state);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "No debuggee detected");
    }

    @Test
    void waitForBreakpoint_withPreviousDebugSession_cleansUpFirst() throws Exception {
        // Setup: session has an existing debug session that needs cleanup
        JcoSession session = mockGetSession(TEST_SESSION_ID);
        Map<String, String> cleanupParams = new java.util.LinkedHashMap<>();
        cleanupParams.put("debuggingMode", "user");
        cleanupParams.put("terminalId", "OLD_TERM");
        cleanupParams.put("ideId", "OLD_IDE");
        cleanupParams.put("requestUser", "OLDUSER");
        when(session.buildDebugCleanupParams()).thenReturn(cleanupParams);
        when(session.getDebugTerminalId()).thenReturn("OLD_TERM");
        when(session.getDebugIdeId()).thenReturn("OLD_IDE");

        // Two calls: cleanup + getStack
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(null)           // cleanup returns null
                .thenReturn(STACK_RESPONSE); // getStack returns stack

        AttachResult successResult = AttachResult.success("DEBUG123456", LISTENER_RESPONSE);
        CompletableFuture<AttachResult> completedFuture = CompletableFuture.completedFuture(successResult);
        DebugListenerState state = new DebugListenerState(
                completedFuture, "TERM123", "IDE456", "D052860",
                "ZCL_LOGIC", "/sap/bc/adt/oo/classes/zcl_logic/includes/implementations",
                42, 120);
        DebugStartSessionHandler.putPendingListener(TEST_SESSION_ID, state);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Debug Session Ready!");
        // Verify clearDebugState was called (cleanup of old session)
        verify(session).clearDebugState();
        // 2 calls: cleanup old debug + getStack (attach is done in listener)
        verify(jcoSessionManager, times(2)).executeInContext(eq(TEST_SESSION_ID), any());
    }

    @Test
    void waitForBreakpoint_previousDebugCleanupFails_continuesAnyway() throws Exception {
        // Setup: session has existing debug but cleanup fails (non-fatal)
        JcoSession session = mockGetSession(TEST_SESSION_ID);
        Map<String, String> cleanupParams = new java.util.LinkedHashMap<>();
        cleanupParams.put("debuggingMode", "user");
        cleanupParams.put("terminalId", "OLD_TERM");
        cleanupParams.put("ideId", "OLD_IDE");
        when(session.buildDebugCleanupParams()).thenReturn(cleanupParams);
        when(session.getDebugTerminalId()).thenReturn("OLD_TERM");
        when(session.getDebugIdeId()).thenReturn("OLD_IDE");

        // First call (cleanup) fails, second call (getStack) succeeds
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Cleanup failed"))  // cleanup fails
                .thenReturn(STACK_RESPONSE);                        // getStack succeeds

        AttachResult successResult = AttachResult.success("DEBUG123456", LISTENER_RESPONSE);
        CompletableFuture<AttachResult> completedFuture = CompletableFuture.completedFuture(successResult);
        DebugListenerState state = new DebugListenerState(
                completedFuture, "TERM123", "IDE456", "D052860",
                "ZCL_LOGIC", "/sap/bc/adt/oo/classes/zcl_logic/includes/implementations",
                42, 120);
        DebugStartSessionHandler.putPendingListener(TEST_SESSION_ID, state);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert - should still succeed (cleanup failure is non-fatal)
        assertSuccess(result);
        assertContains(result, "Debug Session Ready!");
    }

    @Test
    void waitForBreakpoint_stackVerificationFails_returnsError() throws Exception {
        // Setup: attach succeeded (in listener) but stack verification fails
        JcoSession session = mockGetSession(TEST_SESSION_ID);
        when(session.buildDebugCleanupParams()).thenReturn(null);  // No previous debug session

        // getStack fails (debuggee terminated after attach)
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("noSessionAttached: Debuggee terminated"));

        AttachResult successResult = AttachResult.success("DEBUG123456", LISTENER_RESPONSE);
        CompletableFuture<AttachResult> completedFuture = CompletableFuture.completedFuture(successResult);
        DebugListenerState state = new DebugListenerState(
                completedFuture, "TERM123", "IDE456", "D052860",
                "ZCL_LOGIC", "/sap/bc/adt/oo/classes/zcl_logic/includes/implementations",
                42, 120);
        DebugStartSessionHandler.putPendingListener(TEST_SESSION_ID, state);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Failed to verify breakpoint position");
        assertContains(result, "noSessionAttached");
    }

    @Test
    void waitForBreakpoint_attachFailedInListener_returnsError() throws Exception {
        // Setup: listener ran but attach failed inside it
        JcoSession session = mockGetSession(TEST_SESSION_ID);
        when(session.buildDebugCleanupParams()).thenReturn(null);

        // Create AttachResult with failure from attach operation
        AttachResult failureResult = AttachResult.failure(
                new RuntimeException("409 Conflict: Another debug session exists"));
        CompletableFuture<AttachResult> completedFuture = CompletableFuture.completedFuture(failureResult);
        DebugListenerState state = new DebugListenerState(
                completedFuture, "TERM123", "IDE456", "D052860",
                "ZCL_LOGIC", "/sap/bc/adt/oo/classes/zcl_logic/includes/implementations",
                42, 120);
        DebugStartSessionHandler.putPendingListener(TEST_SESSION_ID, state);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Debug attach failed");
        assertContains(result, "409 Conflict");
    }
}
