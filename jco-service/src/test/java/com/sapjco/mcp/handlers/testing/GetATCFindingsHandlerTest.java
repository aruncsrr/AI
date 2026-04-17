package com.sapjco.mcp.handlers.testing;

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
 * Unit tests for GetATCFindingsHandler.
 * Tests ATC worklist findings retrieval.
 */
class GetATCFindingsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetATCFindingsHandler handler;

    private JcoSession mockSession;

    // Sample ATC worklist ID (32-character GUID)
    private static final String TEST_WORKLIST_ID = "12345678901234567890123456789012";

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSessionPattern();
        mockSession = mockGetSession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getATCFindings_withFindings_returnsRawXml() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.ATC_FINDINGS);

        CallToolRequest request = requestBuilder()
                .arg("worklist_id", TEST_WORKLIST_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata (XML written to file)
        assertContains(result, "File:");
        assertContains(result, "bytes");
        assertSystemHeader(result);
    }

    @Test
    void getATCFindings_noFindings_returnsRawXml() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.ATC_NO_FINDINGS);

        CallToolRequest request = requestBuilder()
                .arg("worklist_id", TEST_WORKLIST_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Empty findings still return file path
        assertContains(result, "File:");
    }

    @Test
    void getATCFindings_checkstyleFormat_returnsXml() throws Exception {
        // Arrange
        String checkstyleXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <checkstyle version="4.3">
                    <file name="ZCL_TEST_CLASS">
                        <error line="10" severity="error" message="Test error"/>
                    </file>
                </checkstyle>
                """;
        setupExecuteInContext(checkstyleXml);

        CallToolRequest request = requestBuilder()
                .arg("worklist_id", TEST_WORKLIST_ID)
                .arg("format", "checkstyle")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void getATCFindings_returnsFilePath() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.ATC_FINDINGS);

        CallToolRequest request = requestBuilder()
                .arg("worklist_id", TEST_WORKLIST_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
        assertContains(result, "bytes");
    }

    @Test
    void getATCFindings_missingWorklistId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "worklist_id is required");
    }

    @Test
    void getATCFindings_tempSessionLifecycle_createsAndDestroysSession() throws Exception {
        // Arrange
        setupExecuteInContext(SampleXmlResponses.ATC_FINDINGS);

        CallToolRequest request = requestBuilder()
                .arg("worklist_id", TEST_WORKLIST_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verifyTempSessionLifecycle();
    }

    @Test
    void getATCFindings_withProvidedSession_usesProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        setupExecuteInContextWithSession(TEST_SESSION_ID, SampleXmlResponses.ATC_FINDINGS);

        CallToolRequest request = requestBuilder()
                .arg("worklist_id", TEST_WORKLIST_ID)
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
