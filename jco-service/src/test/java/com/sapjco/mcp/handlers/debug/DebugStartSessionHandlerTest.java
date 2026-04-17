package com.sapjco.mcp.handlers.debug;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.handlers.debug.DebugStartSessionHandler.AttachResult;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DebugStartSessionHandler.
 * Tests the all-in-one debug session startup.
 */
class DebugStartSessionHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugStartSessionHandler handler;

    private JcoSession mockSession;
    private JcoSession mockTriggerSession;

    private static final String BREAKPOINT_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <dbg:breakpoints xmlns:dbg="http://www.sap.com/adt/debugger">
                <breakpoint kind="line" id="BP001"/>
            </dbg:breakpoints>
            """;

    private static final String LISTENER_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <dbg:debuggee xmlns:dbg="http://www.sap.com/adt/debugger">
                <DEBUGGEE_ID>DEBUG123456</DEBUGGEE_ID>
            </dbg:debuggee>
            """;

    private static final String ATTACH_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <RESULT>OK</RESULT>
                </asx:values>
            </asx:abap>
            """;

    private static final String SETTINGS_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <RESULT>OK</RESULT>
                </asx:values>
            </asx:abap>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockTriggerSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @AfterEach
    void tearDown() {
        // Clean up any pending listeners left by tests
        DebugStartSessionHandler.clearPendingListeners();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases - Check parameter validation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void debugStartSession_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void debugStartSession_emptySessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", "")
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void debugStartSession_missingObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void debugStartSession_emptyObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void debugStartSession_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void debugStartSession_emptyObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void debugStartSession_missingRequestUser_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "request_user is required");
    }

    @Test
    void debugStartSession_emptyRequestUser_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "request_user is required");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void debugStartSession_sessionNotFound_returnsError() {
        // Arrange
        when(jcoSessionManager.getSession("invalid-session"))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", "invalid-session")
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void debugStartSession_breakpointSetFailed_returnsError() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Failed to set breakpoint: Permission denied"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Permission denied");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Parameter Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void debugStartSession_includeType_testclasses() {
        // The default include_type is testclasses
        // This test verifies that the handler accepts this parameter
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .arg("include_type", "testclasses")
                .build();

        // Should not fail on parameter validation
        // (will fail on session lookup, which is expected)
        when(jcoSessionManager.getSession(TEST_SESSION_ID))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolResult result = handler.handle(exchange, request);

        // Verify it got past parameter validation
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void debugStartSession_includeType_implementations() {
        // This tests non-testclasses include types
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .arg("include_type", "implementations")
                .build();

        when(jcoSessionManager.getSession(TEST_SESSION_ID))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void debugStartSession_customTimeout() {
        // Test custom timeout parameter
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .arg("timeout_seconds", 60)
                .build();

        when(jcoSessionManager.getSession(TEST_SESSION_ID))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Session not found");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Non-testclasses: returns immediately with pending listener
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void debugStartSession_implementations_returnsImmediately() throws Exception {
        // Arrange: mock session and breakpoint set
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_LOGIC")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "D052860")
                .arg("include_type", "implementations")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert: returns success immediately (not error)
        assertSuccess(result);
        assertContains(result, "Breakpoint Set - Waiting for Trigger");
        assertContains(result, "DebugWaitForBreakpoint");
        assertContains(result, "implementations");
        assertContains(result, "ZCL_LOGIC");

        // Verify a pending listener was stored
        assertTrue(DebugStartSessionHandler.hasPendingListener(TEST_SESSION_ID),
                "Should have stored a pending listener for the session");

        // Verify NO test trigger session was created (non-testclasses)
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
    }

    @Test
    void debugStartSession_definitions_returnsImmediately() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_LOGIC")
                .arg("object_type", "class")
                .arg("line_number", 10)
                .arg("request_user", "D052860")
                .arg("include_type", "definitions")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Breakpoint Set - Waiting for Trigger");
        assertTrue(DebugStartSessionHandler.hasPendingListener(TEST_SESSION_ID));
    }

    @Test
    void debugStartSession_program_nonTestclasses_returnsImmediately() throws Exception {
        // Non-class objects are never auto-trigger (autoTriggerTests = false)
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZTEST_PROGRAM")
                .arg("object_type", "program")
                .arg("line_number", 5)
                .arg("request_user", "D052860")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert: programs always use non-blocking path (autoTriggerTests is false)
        assertSuccess(result);
        assertContains(result, "Breakpoint Set - Waiting for Trigger");
        assertTrue(DebugStartSessionHandler.hasPendingListener(TEST_SESSION_ID));
    }

    @Test
    void debugStartSession_pendingListenerState_hasCorrectValues() throws Exception {
        // Verify that the stored listener state captures the right values
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_MY_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 99)
                .arg("request_user", "TESTUSER")
                .arg("include_type", "implementations")
                .arg("timeout_seconds", 60)
                .build();

        handler.handle(exchange, request);

        DebugStartSessionHandler.DebugListenerState state =
                DebugStartSessionHandler.getPendingListener(TEST_SESSION_ID);

        assertNotNull(state, "Pending listener state should exist");
        assertEquals("TESTUSER", state.requestUser);
        assertEquals("ZCL_MY_CLASS", state.objectName);
        assertEquals(99, state.lineNumber);
        assertEquals(60, state.timeoutSeconds);
        assertNotNull(state.terminalId);
        assertNotNull(state.ideId);
        assertNotNull(state.listenerFuture);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // User-level cleanup: ensures lingering debug state is cleared
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void debugStartSession_performsUserLevelCleanup() throws Exception {
        // This test verifies that user-level cleanup is called at the start
        // to clear any lingering debug attachments from previous sessions.
        // This fixes the "Debuggee already attached" error on consecutive
        // debug sessions with new JCo sessions.

        mockGetSession(TEST_SESSION_ID);

        // First call is user-level cleanup, second is breakpoint set
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(null)  // user-level cleanup returns null
                .thenReturn(BREAKPOINT_RESPONSE);  // breakpoint set

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("line_number", 10)
                .arg("request_user", "CLEANUP_USER")
                .arg("include_type", "implementations")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert: should succeed
        assertSuccess(result);
        assertContains(result, "Breakpoint Set - Waiting for Trigger");

        // Verify executeInContext was called at least twice:
        // 1. User-level cleanup
        // 2. Breakpoint set
        verify(jcoSessionManager, atLeast(2)).executeInContext(eq(TEST_SESSION_ID), any());
    }

    @Test
    void debugStartSession_userLevelCleanupFailure_isNonFatal() throws Exception {
        // User-level cleanup failure should not prevent the debug session from starting.
        // This is important because there may not be any debug state to clean.

        mockGetSession(TEST_SESSION_ID);

        // First call (user-level cleanup) throws, second call (breakpoint) succeeds
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("No debug state to clean"))
                .thenReturn(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("line_number", 10)
                .arg("request_user", "D052860")
                .arg("include_type", "implementations")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert: should still succeed despite cleanup failure
        assertSuccess(result);
        assertContains(result, "Breakpoint Set - Waiting for Trigger");
    }

    @Test
    void debugStartSession_withExistingDebugSession_callsDebugDetach() throws Exception {
        // This test verifies that when a session has an active debug session (from a
        // previous debug run on the same JCo session), debugDetach is called to clear
        // the SAP singleton ref_session before starting a new debug session.
        // This is the critical fix for "Debuggee already attached" errors.

        JcoSession session = mockGetSession(TEST_SESSION_ID);
        // Mark session as having an active debug session
        when(session.hasActiveDebugSession()).thenReturn(true);
        when(session.getDebugTerminalId()).thenReturn("OLD_TERM_ID");
        when(session.getDebugIdeId()).thenReturn("OLD_IDE_ID");
        when(session.getDebugRequestUser()).thenReturn("OLDUSER");

        // Calls: user cleanup, detach, old breakpoint cleanup, new breakpoint set
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(null)                // user-level cleanup
                .thenReturn(null)                // debugDetach (clears singleton)
                .thenReturn(null)                // old breakpoint cleanup
                .thenReturn(BREAKPOINT_RESPONSE);// new breakpoint set

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("line_number", 10)
                .arg("request_user", "NEWUSER")
                .arg("include_type", "implementations")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Breakpoint Set - Waiting for Trigger");
        // Verify clearDebugState was called after cleanup
        verify(session).clearDebugState();
        // Verify at least 4 executeInContext calls (user cleanup + detach + old bp cleanup + new bp)
        verify(jcoSessionManager, atLeast(4)).executeInContext(eq(TEST_SESSION_ID), any());
    }
}
