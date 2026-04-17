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
 * Unit tests for GetRuntimeErrorHandler.
 * Tests runtime error dump retrieval (ST22) functionality.
 */
class GetRuntimeErrorHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetRuntimeErrorHandler handler;

    private JcoSession mockSession;

    private static final String TEST_DUMP_ID = "20240115120000_sap-host_TESTUSER_001_00";

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSessionPattern();
        mockSession = mockGetSession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getDump_formatted_returnsText() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.RUNTIME_ERROR_FORMATTED);

        CallToolRequest request = requestBuilder()
                .arg("dump_id", TEST_DUMP_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Runtime Error:");
        assertContains(result, TEST_DUMP_ID);
        assertContains(result, "formatted");
        assertContains(result, "File:");
        assertSystemHeader(result);
    }

    @Test
    void getDump_metadata_returnsXml() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.RUNTIME_ERROR_METADATA);

        CallToolRequest request = requestBuilder()
                .arg("dump_id", TEST_DUMP_ID)
                .arg("content", "metadata")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Runtime Error:");
        assertContains(result, "metadata");
        assertContains(result, "File:");
        assertSystemHeader(result);
    }

    @Test
    void getDump_summary_returnsHtml() throws Exception {
        // Arrange
        String htmlContent = "<html><body><h1>COMPUTE_INT_ZERODIVIDE</h1></body></html>";
        setupExecuteInContext(htmlContent);

        CallToolRequest request = requestBuilder()
                .arg("dump_id", TEST_DUMP_ID)
                .arg("content", "summary")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Runtime Error:");
        assertContains(result, "summary");
        assertContains(result, "File:");
    }

    @Test
    void getDump_missingDumpId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "dump_id is required");
    }

    @Test
    void getDump_tempSessionLifecycle_createsAndDestroysSession() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.RUNTIME_ERROR_FORMATTED);

        CallToolRequest request = requestBuilder()
                .arg("dump_id", TEST_DUMP_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verifyTempSessionLifecycle();
    }

    @Test
    void getDump_withProvidedSession_usesProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        setupExecuteInContextWithSession(TEST_SESSION_ID, SampleXmlResponses.RUNTIME_ERROR_FORMATTED);

        CallToolRequest request = requestBuilder()
                .arg("dump_id", TEST_DUMP_ID)
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
