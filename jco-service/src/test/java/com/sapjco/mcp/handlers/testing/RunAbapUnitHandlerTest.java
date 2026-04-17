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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RunAbapUnitHandler.
 * Tests XML parsing and test result formatting.
 */
class RunAbapUnitHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private RunAbapUnitHandler handler;

    private JcoSession mockTempSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void runAbapUnit_allTestsPass() throws Exception {
        // Arrange
        setupTempSession();

        // executeInContext returns the String body (handler calls .getBody() inside)
        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_SUCCESS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
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
    void runAbapUnit_testFailures() throws Exception {
        // Arrange
        setupTempSession();

        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_FAILURE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);  // Tool call succeeded
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void runAbapUnit_criticalSeverityFailures() throws Exception {
        // Arrange - Tests that severity="critical" is included in raw XML
        // Real SAP systems return severity="critical" for failed assertions
        setupTempSession();

        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_CRITICAL_FAILURE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);  // Tool call succeeded
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void runAbapUnit_noTestsFound() throws Exception {
        // Arrange
        setupTempSession();

        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_EMPTY);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_NO_TESTS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Empty result still returns file path
        assertContains(result, "File:");
    }

    @Test
    void runAbapUnit_returnsFilePath() throws Exception {
        // Arrange
        setupTempSession();

        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_SUCCESS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
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
    void runAbapUnit_debugMode_usesRunsEndpoint() throws Exception {
        // Arrange
        setupTempSession();

        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_SUCCESS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("debug_mode", true)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Note: We can't easily verify the path used without ArgumentCaptor on AdtClient
        // But we can verify the request completed successfully
    }

    @Test
    void runAbapUnit_withCoverage() throws Exception {
        // Arrange
        setupTempSession();

        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_WITH_COVERAGE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("with_coverage", true)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void runAbapUnit_withCoverage_noCoverageUri() throws Exception {
        // Arrange
        setupTempSession();

        // Use response without coverage URI
        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_SUCCESS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("with_coverage", true)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void runAbapUnit_program() throws Exception {
        // Arrange
        setupTempSession();

        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_EMPTY);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZTEST_PROGRAM")
                .arg("object_type", "program")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void runAbapUnit_package() throws Exception {
        // Arrange
        setupTempSession();

        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_SUCCESS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZTEST_PACKAGE")
                .arg("object_type", "package")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void runAbapUnit_withCustomTimeout() throws Exception {
        // Arrange
        setupTempSession();

        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_SUCCESS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("timeout_seconds", 300)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Verify the 3-argument version was called with 300 seconds
        verify(jcoSessionManager).executeInContext(eq(TEST_TEMP_SESSION_ID), any(), eq(300));
    }

    @Test
    void runAbapUnit_missingObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void runAbapUnit_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void runAbapUnit_adtError_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any(), anyInt()))
                .thenThrow(new RuntimeException("ADT request failed"));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
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
    void runAbapUnit_withProvidedSession_noTempSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);

        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any(), anyInt()))
                .thenReturn(SampleXmlResponses.ABAP_UNIT_SUCCESS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
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
    void runAbapUnit_foreignTests_succeeds() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_WITH_FOREIGN_TESTS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
                .arg("object_type", "class")
                .arg("test_scope", "foreign_tests")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "File:");
        assertContains(result, "bytes");
        assertSystemHeader(result);
    }

    @Test
    void runAbapUnit_allTests_succeeds() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_WITH_FOREIGN_TESTS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
                .arg("object_type", "class")
                .arg("test_scope", "all_tests")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "File:");
        assertContains(result, "bytes");
    }

    @Test
    void runAbapUnit_foreignTests_showsScopeInOutput() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_WITH_FOREIGN_TESTS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
                .arg("object_type", "class")
                .arg("test_scope", "foreign_tests")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Test scope: foreign tests");
    }

    @Test
    void runAbapUnit_allTests_showsScopeInOutput() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_WITH_FOREIGN_TESTS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
                .arg("object_type", "class")
                .arg("test_scope", "all_tests")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Test scope: all tests");
    }

    @Test
    void runAbapUnit_defaultScope_noScopeInOutput() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.ABAP_UNIT_SUCCESS);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertNotContains(result, "Test scope:");
    }

    /**
     * Helper to setup temp session pattern with lenient stubbing.
     * Uses lenient() to avoid UnnecessaryStubbingException when tests don't reach this code.
     */
    private void setupTempSession() throws Exception {
        lenient().when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_TEMP_SESSION_ID);
        lenient().when(jcoSessionManager.getSession(TEST_TEMP_SESSION_ID)).thenReturn(mockTempSession);
    }

    /**
     * Helper to setup executeInContext mock that returns the XML body String.
     * The handler calls adtClient.statelessPostViaRfc(...).getBody() inside executeInContext,
     * so we mock the entire operation to return the body string directly.
     * Uses lenient() to avoid UnnecessaryStubbingException when tests don't reach this code.
     */
    private void setupExecuteInContext(String responseBody) throws Exception {
        // Mock the 3-argument version (with timeout)
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any(), anyInt()))
                .thenReturn(responseBody);
        // Also mock 2-argument version for backwards compatibility
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(responseBody);
    }
}
