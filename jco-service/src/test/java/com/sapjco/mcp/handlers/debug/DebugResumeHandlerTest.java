package com.sapjco.mcp.handlers.debug;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DebugResumeHandler.
 * Tests execution resume and program termination detection.
 */
class DebugResumeHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugResumeHandler handler;

    private JcoSession mockSession;

    private static final String RESUME_RESPONSE = "";
    private static final String STACK_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger">
                <frame level="1"/>
            </dbg:stack>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession(TEST_SESSION_ID);  // Use the mock returned by mockGetSession
    }

    @Test
    void resume_success() throws Exception {
        // Two calls: resume + stack check
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(RESUME_RESPONSE)
                .thenReturn(STACK_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "Execution Resumed");
        assertContains(result, "Process will continue until");
    }

    @Test
    void resume_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder().build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void resume_sessionNotFound_returnsError() {
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
    void resume_executionError_returnsError() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Debug session error"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Program termination detection tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void resume_programTerminated_detachesAndClearsState() throws Exception {
        // Resume succeeds, then stack check fails (termination), then detach succeeds
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(RESUME_RESPONSE)                                    // 1st: resume
                .thenThrow(new RuntimeException("CM_NO_DATA_RECEIVED"))         // 2nd: stack check
                .thenReturn(null);                                              // 3rd: debugDetach

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        // Should still succeed but indicate termination
        assertSuccess(result);
        assertContains(result, "Program Terminated");
        assertContains(result, "debug session has ended automatically");

        // Verify both detach AND clearDebugState are called
        verify(jcoSessionManager, times(3)).executeInContext(eq(TEST_SESSION_ID), any());
        verify(mockSession).clearDebugState();
    }

    @Test
    void resume_connectionClosed_detachesAndClearsState() throws Exception {
        // Resume succeeds, then stack check fails, then detach succeeds
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(RESUME_RESPONSE)                                    // 1st: resume
                .thenThrow(new RuntimeException("connection closed by remote")) // 2nd: stack check
                .thenReturn(null);                                              // 3rd: debugDetach

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "Program Terminated");
        verify(jcoSessionManager, times(3)).executeInContext(eq(TEST_SESSION_ID), any());
        verify(mockSession).clearDebugState();
    }

    @Test
    void resume_resumeFails_withConnectionError_givesHelpfulMessage() throws Exception {
        // Resume itself fails with connection error
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("CM_NO_DATA_RECEIVED"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Debug session ended");
        assertContains(result, "program has likely completed");
    }
}
