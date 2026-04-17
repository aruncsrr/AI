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
 * Unit tests for SaveInterfaceHandler.
 * Tests atomic lock/save/unlock operations for interface objects.
 */
class SaveInterfaceHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveInterfaceHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void saveInterface_success_atomicOperation() throws Exception {
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
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("source_code", SampleAbapSource.INTERFACE_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response/>");  // Raw XML response
        assertSystemHeader(result);

        // Verify atomic operation order: lock -> save -> unlock
        InOrder inOrder = inOrder(adtClient);
        inOrder.verify(adtClient).lockObject(any(), eq("ZIF_TEST_INTERFACE"), eq("interface"), any(), any(), any());
        inOrder.verify(adtClient).saveObject(any(), eq("ZIF_TEST_INTERFACE"), eq("interface"),
                eq(SampleAbapSource.INTERFACE_DEFINITION), eq(TEST_LOCK_HANDLE), eq(TEST_TRANSPORT), any(), any(), any());
        inOrder.verify(adtClient).unlockObject(any(), eq("ZIF_TEST_INTERFACE"), eq("interface"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveInterface_saveFailure_unlocksObject() throws Exception {
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
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("source_code", SampleAbapSource.INTERFACE_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Save failed");

        // CRITICAL: Verify unlock was still called after save failure
        verify(adtClient).unlockObject(any(), eq("ZIF_TEST_INTERFACE"), eq("interface"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveInterface_withTransportNumber_usesProvidedTransport() throws Exception {
        // Arrange
        String userTransport = "DEVK900999";
        LockResponse lockResponse = createMockLockResponse(TEST_LOCK_HANDLE, TEST_TRANSPORT);
        setupExecuteInContext();

        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn("<response/>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("source_code", SampleAbapSource.INTERFACE_DEFINITION)
                .arg("transport_number", userTransport)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response/>");  // Raw XML response

        // Verify user-provided transport was used, not lock response transport
        verify(adtClient).saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), eq(userTransport), any(), any(), any());
    }

    @Test
    void saveInterface_localObject_noTransport() throws Exception {
        // Arrange
        LockResponse lockResponse = createMockLockResponse(TEST_LOCK_HANDLE, null);
        setupExecuteInContext();

        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), isNull(), any(), any(), any())).thenReturn("<response/>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("interface_name", "ZIF_LOCAL_INTERFACE")
                .arg("source_code", SampleAbapSource.INTERFACE_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertNotContains(result, "Transport:");  // No transport in output
    }

    @Test
    void saveInterface_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("source_code", SampleAbapSource.INTERFACE_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveInterface_missingInterfaceName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_code", SampleAbapSource.INTERFACE_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "interface_name is required");
    }

    @Test
    void saveInterface_missingSourceCode_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }

    @Test
    void saveInterface_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("source_code", SampleAbapSource.INTERFACE_DEFINITION)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void saveInterface_lockFailure_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("Object already locked by another user"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("source_code", SampleAbapSource.INTERFACE_DEFINITION)
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
    void saveInterface_responseContainsRawXml() throws Exception {
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
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("source_code", SampleAbapSource.INTERFACE_DEFINITION)
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
