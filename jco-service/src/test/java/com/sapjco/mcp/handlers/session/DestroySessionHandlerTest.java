package com.sapjco.mcp.handlers.session;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DestroySessionHandler.
 */
class DestroySessionHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DestroySessionHandler handler;

    @Test
    void destroySession_success() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);  // Must mock getSession before destroySession
        doNothing().when(jcoSessionManager).destroySession(TEST_SESSION_ID);
        when(jcoSessionManager.getActiveSessionCount()).thenReturn(0);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Session destroyed successfully");
        assertContains(result, TEST_SESSION_ID);
        verify(jcoSessionManager).destroySession(TEST_SESSION_ID);
    }

    @Test
    void destroySession_showsRemainingSessions() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);  // Must mock getSession before destroySession
        doNothing().when(jcoSessionManager).destroySession(TEST_SESSION_ID);
        when(jcoSessionManager.getActiveSessionCount()).thenReturn(2);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Active sessions remaining: 2");
    }

    @Test
    void destroySession_sessionNotFound_returnsError() throws Exception {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
        assertContains(result, unknownSession);
    }

    @Test
    void destroySession_missingSessionId_returnsError() {
        // Arrange - use empty map instead of null to test missing session_id
        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void destroySession_emptySessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void destroySession_jcoException_returnsError() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);  // Must mock getSession before destroySession
        doThrow(new RuntimeException("JCo context end failed"))
                .when(jcoSessionManager).destroySession(TEST_SESSION_ID);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "JCo context end failed");
    }

    @Test
    void destroySession_withActiveDebugSession_cleansUpDebug() throws Exception {
        // Arrange - session with active debug session
        JcoSession session = mockGetSession(TEST_SESSION_ID);
        Map<String, String> cleanupParams = new java.util.LinkedHashMap<>();
        cleanupParams.put("debuggingMode", "user");
        cleanupParams.put("terminalId", "TERM123");
        cleanupParams.put("ideId", "IDE456");
        cleanupParams.put("requestUser", "TESTUSER");
        when(session.buildDebugCleanupParams()).thenReturn(cleanupParams);
        when(session.getDebugTerminalId()).thenReturn("TERM123");
        when(session.getDebugIdeId()).thenReturn("IDE456");

        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(null);  // Debug cleanup succeeds
        doNothing().when(jcoSessionManager).destroySession(TEST_SESSION_ID);
        when(jcoSessionManager.getActiveSessionCount()).thenReturn(0);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Session destroyed successfully");
        assertContains(result, "Debug session cleaned up automatically");
        verify(session).clearDebugState();
        verify(jcoSessionManager).executeInContext(eq(TEST_SESSION_ID), any());
    }

    @Test
    void destroySession_debugCleanupFails_continuesWithDestroy() throws Exception {
        // Arrange - session with active debug session, but cleanup fails
        JcoSession session = mockGetSession(TEST_SESSION_ID);
        Map<String, String> cleanupParams = new java.util.LinkedHashMap<>();
        cleanupParams.put("debuggingMode", "user");
        cleanupParams.put("terminalId", "TERM123");
        cleanupParams.put("ideId", "IDE456");
        when(session.buildDebugCleanupParams()).thenReturn(cleanupParams);
        when(session.getDebugTerminalId()).thenReturn("TERM123");
        when(session.getDebugIdeId()).thenReturn("IDE456");

        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Debug cleanup failed: connection lost"));
        doNothing().when(jcoSessionManager).destroySession(TEST_SESSION_ID);
        when(jcoSessionManager.getActiveSessionCount()).thenReturn(0);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert - should still succeed, but report the cleanup failure
        assertSuccess(result);
        assertContains(result, "Session destroyed successfully");
        assertContains(result, "Debug cleanup failed");
        verify(jcoSessionManager).destroySession(TEST_SESSION_ID);
    }

    @Test
    void destroySession_noActiveDebugSession_skipsDebugCleanup() throws Exception {
        // Arrange - session without active debug session
        JcoSession session = mockGetSession(TEST_SESSION_ID);
        when(session.buildDebugCleanupParams()).thenReturn(null);  // No active debug session

        doNothing().when(jcoSessionManager).destroySession(TEST_SESSION_ID);
        when(jcoSessionManager.getActiveSessionCount()).thenReturn(0);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Session destroyed successfully");
        // Should NOT contain debug cleanup message
        assertFalse(getResultText(result).contains("Debug session cleaned up"),
                "Should not mention debug cleanup when no debug session was active");
        // executeInContext should NOT be called for debug cleanup
        verify(jcoSessionManager, never()).executeInContext(anyString(), any());
    }

    // Note: Pending listener cleanup is tested indirectly - the actual cleanup
    // functionality is covered by DebugStartSessionHandler tests in the debug package.
    // DestroySession just calls cleanupPendingListener() which is package-private.
}
