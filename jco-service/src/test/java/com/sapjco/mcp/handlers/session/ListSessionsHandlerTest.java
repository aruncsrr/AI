package com.sapjco.mcp.handlers.session;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for ListSessionsHandler.
 * Tests session listing functionality.
 */
class ListSessionsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private ListSessionsHandler handler;

    @Test
    void listSessions_empty_returnsEmptyList() {
        // Arrange
        when(jcoSessionManager.listSessions()).thenReturn(new ArrayList<>());

        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "No active sessions");
        assertContains(result, "\"count\" : 0");
    }

    @Test
    void listSessions_singleSession_returnsSessionInfo() {
        // Arrange
        JcoSession session = createDetailedMockSession(
                "session-123",
                "dev",
                "sap.example.com",
                "100",
                "conn-456"
        );
        List<JcoSession> sessions = List.of(session);
        when(jcoSessionManager.listSessions()).thenReturn(sessions);

        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Found 1 active session");
        assertContains(result, "session-123");
        assertContains(result, "\"systemId\" : \"dev\"");
        assertContains(result, "\"count\" : 1");
    }

    @Test
    void listSessions_multipleSessions_returnsAllSessions() {
        // Arrange
        JcoSession session1 = createDetailedMockSession(
                "session-111",
                "dev",
                "dev.sap.com",
                "100",
                "conn-111"
        );
        JcoSession session2 = createDetailedMockSession(
                "session-222",
                "prod",
                "prod.sap.com",
                "200",
                "conn-222"
        );
        List<JcoSession> sessions = List.of(session1, session2);
        when(jcoSessionManager.listSessions()).thenReturn(sessions);

        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Found 2 active sessions");
        assertContains(result, "session-111");
        assertContains(result, "session-222");
        assertContains(result, "\"dev\"");
        assertContains(result, "\"prod\"");
        assertContains(result, "\"count\" : 2");
    }

    @Test
    void listSessions_sessionWithLocks_showsLockInfo() {
        // Arrange
        JcoSession session = createDetailedMockSession(
                "session-lock",
                "dev",
                "sap.example.com",
                "100",
                "conn-789"
        );

        // Add lock information
        List<String> locks = List.of("ZCL_TEST_CLASS");
        lenient().when(session.getLockCount()).thenReturn(1);
        lenient().when(session.getLocks()).thenReturn(locks);

        List<JcoSession> sessions = List.of(session);
        when(jcoSessionManager.listSessions()).thenReturn(sessions);

        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "\"lockCount\" : 1");
    }

    /**
     * Creates a detailed mock session with all required fields.
     */
    private JcoSession createDetailedMockSession(
            String sessionId,
            String systemId,
            String host,
            String client,
            String connectionId
    ) {
        JcoSession session = mock(JcoSession.class);
        lenient().when(session.getSessionId()).thenReturn(sessionId);
        lenient().when(session.getSystemId()).thenReturn(systemId);
        lenient().when(session.getHost()).thenReturn(host);
        lenient().when(session.getClient()).thenReturn(client);
        lenient().when(session.getConnectionId()).thenReturn(connectionId);
        lenient().when(session.getType()).thenReturn("jco");
        lenient().when(session.getCreatedAt()).thenReturn(LocalDateTime.now());
        lenient().when(session.getLastUsedAt()).thenReturn(LocalDateTime.now());
        lenient().when(session.getLockCount()).thenReturn(0);
        lenient().when(session.getLocks()).thenReturn(new ArrayList<>());
        return session;
    }
}
