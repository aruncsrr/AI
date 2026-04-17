package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.model.LockResponse;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.JcoSessionManager.JcoOperation;
import com.sapjco.mcp.testdata.SampleAbapSource;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SaveClassHandler.
 * Tests atomic lock/save/unlock operations.
 */
class SaveClassHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveClassHandler handler;

    private JcoSession mockSession;

    private static final String TEST_SOURCE_FILE = "/tmp/sap-mcp/dev_100/class/ZCL_TEST_CLASS.abap";

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
        // Note: mockGetSession() via createMockSession() already sets up httpClient and csrfTokenCache with lenient()
    }

    @Test
    void saveClass_success_atomicOperation() throws Exception {
        // Arrange
        LockResponse lockResponse = createMockLockResponse();

        // Mock the executeInContext to actually call the operation
        setupExecuteInContext();

        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn("<response>saved</response>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
        assertSystemHeader(result);

        // Verify atomic operation order: lock -> save -> unlock
        InOrder inOrder = inOrder(adtClient);
        inOrder.verify(adtClient).lockObject(any(), eq("ZCL_TEST_CLASS"), eq("CLASS"), any(), any(), any());
        inOrder.verify(adtClient).saveObject(any(), eq("ZCL_TEST_CLASS"), eq("CLASS"),
                eq(SampleAbapSource.CLASS_DEFINITION), eq(TEST_LOCK_HANDLE), eq(TEST_TRANSPORT), any(), any(), any());
        inOrder.verify(adtClient).unlockObject(any(), eq("ZCL_TEST_CLASS"), eq("CLASS"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveClass_saveFailure_unlocksObject() throws Exception {
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
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Save failed");

        // CRITICAL: Verify unlock was still called after save failure
        verify(adtClient).unlockObject(any(), eq("ZCL_TEST_CLASS"), eq("CLASS"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveClass_withTransportNumber_usesProvidedTransport() throws Exception {
        // Arrange
        String userTransport = "DEVK900999";
        LockResponse lockResponse = createMockLockResponse(TEST_LOCK_HANDLE, TEST_TRANSPORT);
        setupExecuteInContext();

        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn("<response>saved</response>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .arg("transport_number", userTransport)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);

        // Verify user-provided transport was used, not lock response transport
        verify(adtClient).saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), eq(userTransport), any(), any(), any());
    }

    @Test
    void saveClass_localObject_noTransport() throws Exception {
        // Arrange
        LockResponse lockResponse = createMockLockResponse(TEST_LOCK_HANDLE, null);
        setupExecuteInContext();

        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), isNull(), any(), any(), any())).thenReturn("<response>saved</response>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_LOCAL_CLASS")
                .arg("source_code", SampleAbapSource.MINIMAL_CLASS)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void saveClass_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveClass_missingClassName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "class_name is required");
    }

    @Test
    void saveClass_missingSourceCodeAndFile_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }

    @Test
    void saveClass_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void saveClass_lockFailure_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("Object already locked by another user"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Object already locked");

        // Verify save was never called
        verify(adtClient, never()).saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any());
        // Verify unlock was never called (no lock to release)
        verify(adtClient, never()).unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    void saveClass_returnsRawResponse() throws Exception {
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
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw response returned
    }

    @Test
    void saveClass_verifyAtomicOperationOrder() throws Exception {
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
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void saveClass_passesLowercaseClassName() throws Exception {
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
                .arg("class_name", "zcl_lowercase")
                .arg("source_code", SampleAbapSource.MINIMAL_CLASS)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    // ========================= File-Based Source Tests =========================

    @Test
    void saveClass_withSourceFile_readsFromFile() throws Exception {
        // Arrange
        LockResponse lockResponse = createMockLockResponse();
        setupExecuteInContext();

        when(fileStorageService.readSource(TEST_SOURCE_FILE))
                .thenReturn(SampleAbapSource.CLASS_DEFINITION);
        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn("<response>saved</response>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_file", TEST_SOURCE_FILE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verify(fileStorageService).readSource(TEST_SOURCE_FILE);
        verify(adtClient).saveObject(any(), eq("ZCL_TEST_CLASS"), eq("CLASS"),
                eq(SampleAbapSource.CLASS_DEFINITION), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void saveClass_bothSourceCodeAndFile_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_code", SampleAbapSource.CLASS_DEFINITION)
                .arg("source_file", TEST_SOURCE_FILE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Provide source_code OR source_file, not both");
    }

    @Test
    void saveClass_fileNotFound_returnsError() throws Exception {
        // Arrange
        String nonExistentFile = "/tmp/nonexistent.abap";
        when(fileStorageService.readSource(nonExistentFile))
                .thenThrow(new IllegalArgumentException("File not found: " + nonExistentFile));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_file", nonExistentFile)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "File not found");
    }

    @Test
    void saveClass_emptyFile_returnsError() throws Exception {
        // Arrange
        String emptyFile = "/tmp/empty.abap";
        when(fileStorageService.readSource(emptyFile))
                .thenThrow(new IllegalArgumentException("File is empty: " + emptyFile));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("source_file", emptyFile)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "File is empty");
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
