package com.sapjco.mcp.handlers.testing;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.testdata.SampleXmlResponses;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GetCoverageResultHandler.
 * Tests coverage result parsing and formatting.
 */
class GetCoverageResultHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetCoverageResultHandler handler;

    private JcoSession mockTempSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getCoverageResult_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.COVERAGE_RESULT);

        CallToolRequest request = requestBuilder()
                .arg("measurement_uri", "/sap/bc/adt/runtime/traces/coverage/measurements/00505681-1234-5678-ABCD-0000DEADBEEF")
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
    void getCoverageResult_containsCoverageData() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.COVERAGE_RESULT);

        CallToolRequest request = requestBuilder()
                .arg("measurement_uri", "/sap/bc/adt/runtime/traces/coverage/measurements/00505681-1234-5678-ABCD-0000DEADBEEF")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void getCoverageResult_withObjectUris() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.COVERAGE_RESULT);

        CallToolRequest request = requestBuilder()
                .arg("measurement_uri", "/sap/bc/adt/runtime/traces/coverage/measurements/00505681-1234-5678-ABCD-0000DEADBEEF")
                .arg("object_uris", List.of("/sap/bc/adt/oo/classes/zcl_test_class"))
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "File:");
    }

    @Test
    void getCoverageResult_emptyResult() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.COVERAGE_RESULT_EMPTY);

        CallToolRequest request = requestBuilder()
                .arg("measurement_uri", "/sap/bc/adt/runtime/traces/coverage/measurements/00505681-1234-5678-ABCD-0000DEADBEEF")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Empty result still returns file path
        assertContains(result, "File:");
    }

    @Test
    void getCoverageResult_returnsFilePath() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.COVERAGE_RESULT);

        CallToolRequest request = requestBuilder()
                .arg("measurement_uri", "/sap/bc/adt/runtime/traces/coverage/measurements/00505681-1234-5678-ABCD-0000DEADBEEF")
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
    void getCoverageResult_missingMeasurementUri_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "measurement_uri is required");
    }

    @Test
    void getCoverageResult_adtError_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("ADT request failed"));

        CallToolRequest request = requestBuilder()
                .arg("measurement_uri", "/sap/bc/adt/runtime/traces/coverage/measurements/00505681-1234-5678-ABCD-0000DEADBEEF")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "ADT request failed");

        // Verify temp session cleanup
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getCoverageResult_withProvidedSession_noTempSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);

        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenAnswer(invocation -> SampleXmlResponses.COVERAGE_RESULT);

        CallToolRequest request = requestBuilder()
                .arg("measurement_uri", "/sap/bc/adt/runtime/traces/coverage/measurements/00505681-1234-5678-ABCD-0000DEADBEEF")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager, never()).destroySession(anyString());
    }

    @Test
    void getCoverageResult_sapFormatWithCoveragesWrapper() throws Exception {
        // Arrange - Test real SAP response format with <coverages> wrapper inside <node>
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.COVERAGE_RESULT_SAP_FORMAT);

        CallToolRequest request = requestBuilder()
                .arg("measurement_uri", "/sap/bc/adt/runtime/traces/coverage/measurements/00505681-REAL-SAP-ABCD-FORMAT123456")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
        assertContains(result, "bytes");
        assertSystemHeader(result);
    }

    /**
     * Helper to setup temp session pattern with lenient stubbing.
     */
    private void setupTempSession() throws Exception {
        lenient().when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_TEMP_SESSION_ID);
        lenient().when(jcoSessionManager.getSession(TEST_TEMP_SESSION_ID)).thenReturn(mockTempSession);
    }

    /**
     * Helper to setup executeInContext mock that returns the XML body String.
     */
    private void setupExecuteInContext(String responseBody) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenAnswer(invocation -> responseBody);
    }
}
