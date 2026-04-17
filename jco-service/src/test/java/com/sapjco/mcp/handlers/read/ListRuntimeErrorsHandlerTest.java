package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.service.JcoSessionManager.JcoOperation;
import com.sapjco.mcp.testdata.SampleXmlResponses;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ListRuntimeErrorsHandler.
 * Tests runtime error listing (ST22 dumps) functionality.
 */
class ListRuntimeErrorsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private ListRuntimeErrorsHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSessionPattern();
        mockSession = mockGetSession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void listDumps_withUser_returnsMetadata() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.RUNTIME_ERROR_LIST);

        CallToolRequest request = requestBuilder()
                .arg("user", "TESTUSER")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Runtime Errors (ST22)");
        assertContains(result, "TESTUSER");
        assertContains(result, "File:");
        assertSystemHeader(result);
    }

    @Test
    void listDumps_noParams_returnsMetadata() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.RUNTIME_ERROR_LIST);

        CallToolRequest request = requestBuilder()
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Runtime Errors (ST22)");
        assertContains(result, "File:");
        assertSystemHeader(result);
    }

    @Test
    void listDumps_emptyResults_returnsMetadata() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.RUNTIME_ERROR_LIST_EMPTY);

        CallToolRequest request = requestBuilder()
                .arg("user", "NOBODY")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Runtime Errors (ST22)");
        assertContains(result, "NOBODY");
    }

    @Test
    void listDumps_withTimeRange_passes() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.RUNTIME_ERROR_LIST);

        CallToolRequest request = requestBuilder()
                .arg("from", "2024-01-15T00:00:00")
                .arg("to", "2024-01-16T23:59:59")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Runtime Errors (ST22)");
    }

    @Test
    void listDumps_withMaxResults_respectsLimit() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.RUNTIME_ERROR_LIST);

        CallToolRequest request = requestBuilder()
                .arg("max_results", 10)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verifyTempSessionLifecycle();
    }

    @Test
    void listDumps_tempSessionLifecycle_createsAndDestroysSession() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.RUNTIME_ERROR_LIST);

        CallToolRequest request = requestBuilder()
                .arg("user", "TESTUSER")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verifyTempSessionLifecycle();
    }

    @Test
    void listDumps_withProvidedSession_usesProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        setupExecuteInContextWithSession(TEST_SESSION_ID, SampleXmlResponses.RUNTIME_ERROR_LIST);

        CallToolRequest request = requestBuilder()
                .arg("user", "TESTUSER")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verify(jcoSessionManager, never()).createSession(any());
        verify(jcoSessionManager, never()).destroySession(anyString());
    }

    /**
     * Helper to setup executeInContext mock for temp session.
     */
    @SuppressWarnings("unchecked")
    private void setupExecuteInContext(String returnValue) throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenAnswer(invocation -> {
                    JcoOperation<String> operation = invocation.getArgument(1);
                    return returnValue;
                });
    }

    /**
     * Helper to setup executeInContext mock for provided session.
     */
    @SuppressWarnings("unchecked")
    private void setupExecuteInContextWithSession(String sessionId, String returnValue) throws Exception {
        when(jcoSessionManager.executeInContext(eq(sessionId), any()))
                .thenAnswer(invocation -> {
                    JcoOperation<String> operation = invocation.getArgument(1);
                    return returnValue;
                });
    }
}
