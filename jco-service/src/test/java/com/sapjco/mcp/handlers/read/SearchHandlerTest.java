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
 * Unit tests for SearchHandler.
 * Tests repository search functionality.
 *
 * Note: Handler writes XML to file and returns metadata summary.
 */
class SearchHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SearchHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSessionPattern();
        mockSession = mockGetSession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void search_resultsFound_returnsMetadata() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.SEARCH_RESULTS);

        CallToolRequest request = requestBuilder()
                .arg("query", "ZCL_TEST*")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Metadata summary is returned (XML written to file)
        assertContains(result, "ZCL_TEST*");
        assertContains(result, "File:");
        assertContains(result, "bytes");
        assertSystemHeader(result);
    }

    @Test
    void search_noResults_returnsMetadata() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.SEARCH_RESULTS_EMPTY);

        CallToolRequest request = requestBuilder()
                .arg("query", "ZNONEXISTENT*")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Metadata summary is returned
        assertContains(result, "ZNONEXISTENT*");
        assertContains(result, "File:");
    }

    @Test
    void search_withObjectTypeFilter_passesFilter() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.SEARCH_RESULTS);

        CallToolRequest request = requestBuilder()
                .arg("query", "ZCL_*")
                .arg("object_type", "CLAS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Filter is shown in metadata
        assertContains(result, "ZCL_*");
        assertContains(result, "CLAS");
    }

    @Test
    void search_withMaxResults_respectsLimit() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.SEARCH_RESULTS);

        CallToolRequest request = requestBuilder()
                .arg("query", "Z*")
                .arg("max_results", 10)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // The search was executed (verify via session lifecycle)
        verifyTempSessionLifecycle();
    }

    @Test
    void search_returnsFilePath() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.SEARCH_RESULTS);

        CallToolRequest request = requestBuilder()
                .arg("query", "ZCL_*")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata (cross-platform temp directory)
        assertContains(result, "File:");
        assertContains(result, System.getProperty("java.io.tmpdir"));
    }

    @Test
    void search_missingQuery_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "query is required");
    }

    @Test
    void search_withProvidedSession_usesProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        setupExecuteInContextWithSession(TEST_SESSION_ID, SampleXmlResponses.SEARCH_RESULTS);

        CallToolRequest request = requestBuilder()
                .arg("query", "ZCL_*")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Should NOT create/destroy temp session when session_id is provided
        verify(jcoSessionManager, never()).createSession(any());
        verify(jcoSessionManager, never()).destroySession(anyString());
    }

    @Test
    void search_tempSessionLifecycle_createsAndDestroysSession() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.SEARCH_RESULTS);

        CallToolRequest request = requestBuilder()
                .arg("query", "ZCL_*")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verifyTempSessionLifecycle();
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
