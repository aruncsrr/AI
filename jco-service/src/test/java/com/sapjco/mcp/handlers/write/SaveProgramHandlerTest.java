package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.model.LockResponse;
import com.sapjco.mcp.service.JcoSessionManager.JcoOperation;
import com.sapjco.mcp.testdata.SampleAbapSource;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SaveProgramHandler.
 * Tests atomic lock/save/unlock operations for program objects.
 */
class SaveProgramHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveProgramHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void saveProgram_success_atomicOperation() throws Exception {
        // Arrange
        LockResponse lockResponse = createMockLockResponse();
        setupExecuteInContext();

        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn("<response>saved</response>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("program_name", "ZTEST_PROGRAM")
                .arg("source_code", SampleAbapSource.PROGRAM_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
        assertSystemHeader(result);

        // Verify atomic operation order: lock -> save -> unlock
        InOrder inOrder = inOrder(adtClient);
        inOrder.verify(adtClient).lockObject(any(), eq("ZTEST_PROGRAM"), eq("program"), any(), any(), any());
        inOrder.verify(adtClient).saveObject(any(), eq("ZTEST_PROGRAM"), eq("program"),
                eq(SampleAbapSource.PROGRAM_SOURCE), eq(TEST_LOCK_HANDLE), eq(TEST_TRANSPORT), any(), any(), any());
        inOrder.verify(adtClient).unlockObject(any(), eq("ZTEST_PROGRAM"), eq("program"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveProgram_saveFailure_unlocksObject() throws Exception {
        // Arrange
        LockResponse lockResponse = createMockLockResponse();
        setupExecuteInContext();

        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        doThrow(new RuntimeException("Save failed"))
                .when(adtClient).saveObject(any(), anyString(), anyString(), anyString(),
                        anyString(), anyString(), any(), any(), any());
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("program_name", "ZTEST_PROGRAM")
                .arg("source_code", SampleAbapSource.PROGRAM_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Save failed");

        // CRITICAL: Verify unlock was still called after save failure
        verify(adtClient).unlockObject(any(), eq("ZTEST_PROGRAM"), eq("program"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveProgram_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("program_name", "ZTEST_PROGRAM")
                .arg("source_code", SampleAbapSource.PROGRAM_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveProgram_missingProgramName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_code", SampleAbapSource.PROGRAM_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "program_name is required");
    }

    @Test
    void saveProgram_missingSourceCode_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("program_name", "ZTEST_PROGRAM")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }

    @Test
    void saveProgram_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("program_name", "ZTEST_PROGRAM")
                .arg("source_code", SampleAbapSource.PROGRAM_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void saveProgram_responseContainsRawXml() throws Exception {
        // Arrange
        LockResponse lockResponse = createMockLockResponse();
        setupExecuteInContext();

        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn("<response/>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("program_name", "ZTEST_PROGRAM")
                .arg("source_code", SampleAbapSource.PROGRAM_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response/>");  // Raw XML response
    }

    /**
     * Helper to setup executeInContext mock that invokes the operation.
     */
    @SuppressWarnings("unchecked")
    private void setupExecuteInContext() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenAnswer(invocation -> {
                    JcoOperation<Object> operation = invocation.getArgument(1);
                    return operation.execute(jcoDestination, mockSession);
                });
    }
}
