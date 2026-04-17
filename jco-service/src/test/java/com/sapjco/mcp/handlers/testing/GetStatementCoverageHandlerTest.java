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
import org.mockito.invocation.InvocationOnMock;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GetStatementCoverageHandler.
 * Tests statement-level coverage parsing and formatting using the bulk statements API.
 */
class GetStatementCoverageHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetStatementCoverageHandler handler;

    private JcoSession mockTempSession;

    // Test URIs matching the ADT pattern
    private static final String BULK_STATEMENTS_URI =
            "/sap/bc/adt/runtime/traces/coverage/results/bulkstatements?measurementId=00505681-1234-5678-ABCD-0000DEADBEEF";
    private static final List<String> STATEMENT_URIS = List.of(
            "/sap/bc/adt/runtime/traces/coverage/results/statements?object=%2Fsap%2Fbc%2Fadt%2Foo%2Fclasses%2Fzcl_test_class&measurementId=00505681-1234-5678-ABCD-0000DEADBEEF"
    );

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getStatementCoverage_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextWithSource(SampleXmlResponses.STATEMENT_COVERAGE, null);

        CallToolRequest request = requestBuilder()
                .arg("bulk_statements_uri", BULK_STATEMENTS_URI)
                .arg("statement_uris", STATEMENT_URIS)
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
    void getStatementCoverage_rawXmlContainsStatements() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextWithSource(SampleXmlResponses.STATEMENT_COVERAGE, null);

        CallToolRequest request = requestBuilder()
                .arg("bulk_statements_uri", BULK_STATEMENTS_URI)
                .arg("statement_uris", STATEMENT_URIS)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void getStatementCoverage_emptyResult() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextWithSource(SampleXmlResponses.STATEMENT_COVERAGE_EMPTY, null);

        CallToolRequest request = requestBuilder()
                .arg("bulk_statements_uri", BULK_STATEMENTS_URI)
                .arg("statement_uris", STATEMENT_URIS)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Empty result still returns file path
        assertContains(result, "File:");
    }

    @Test
    void getStatementCoverage_returnsFilePath() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextWithSource(SampleXmlResponses.STATEMENT_COVERAGE, null);

        CallToolRequest request = requestBuilder()
                .arg("bulk_statements_uri", BULK_STATEMENTS_URI)
                .arg("statement_uris", STATEMENT_URIS)
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
    void getStatementCoverage_missingBulkStatementsUri_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("statement_uris", STATEMENT_URIS)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "bulk_statements_uri is required");
    }

    @Test
    void getStatementCoverage_missingStatementUris_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("bulk_statements_uri", BULK_STATEMENTS_URI)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "statement_uris is required");
    }

    @Test
    void getStatementCoverage_emptyStatementUris_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("bulk_statements_uri", BULK_STATEMENTS_URI)
                .arg("statement_uris", List.of())
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "statement_uris is required");
    }

    @Test
    void getStatementCoverage_adtError_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("ADT request failed"));

        CallToolRequest request = requestBuilder()
                .arg("bulk_statements_uri", BULK_STATEMENTS_URI)
                .arg("statement_uris", STATEMENT_URIS)
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
    void getStatementCoverage_withProvidedSession_noTempSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);

        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenAnswer(invocation -> SampleXmlResponses.STATEMENT_COVERAGE);

        CallToolRequest request = requestBuilder()
                .arg("bulk_statements_uri", BULK_STATEMENTS_URI)
                .arg("statement_uris", STATEMENT_URIS)
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
    void getStatementCoverage_multipleStatementUris() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextWithSource(SampleXmlResponses.STATEMENT_COVERAGE, null);

        List<String> multipleUris = List.of(
                "/sap/bc/adt/runtime/traces/coverage/results/statements?object=%2Fsap%2Fbc%2Fadt%2Foo%2Fclasses%2Fzcl_test_class&measurementId=00505681-1234-5678-ABCD-0000DEADBEEF",
                "/sap/bc/adt/runtime/traces/coverage/results/statements?object=%2Fsap%2Fbc%2Fadt%2Foo%2Fclasses%2Fzcl_test_helper&measurementId=00505681-1234-5678-ABCD-0000DEADBEEF"
        );

        CallToolRequest request = requestBuilder()
                .arg("bulk_statements_uri", BULK_STATEMENTS_URI)
                .arg("statement_uris", multipleUris)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
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
     * Helper to setup executeInContext mock that returns coverage XML for first call,
     * and optionally source code for subsequent source fetch calls.
     */
    private void setupExecuteInContextWithSource(String coverageXml, String sourceCode) throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenAnswer((InvocationOnMock invocation) -> {
                    int count = callCount.incrementAndGet();
                    if (count == 1) {
                        return coverageXml;
                    }
                    // Subsequent calls are source fetches
                    return sourceCode;
                });
    }
}
