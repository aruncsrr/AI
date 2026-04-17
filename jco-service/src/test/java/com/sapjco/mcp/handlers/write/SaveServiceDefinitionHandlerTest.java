package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.model.LockResponse;
import com.sapjco.mcp.service.JcoSessionManager.JcoOperation;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;

import java.nio.file.Paths;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SaveServiceDefinitionHandler.
 * Tests atomic lock/save/unlock operations for SRVD objects.
 */
class SaveServiceDefinitionHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveServiceDefinitionHandler handler;

    private JcoSession mockSession;

    private static final String SRVD_SOURCE = """
            @EndUserText.label: 'Travel Service Definition'
            define service ZTEST_SRVD {
              expose ZI_TRAVEL as Travel;
              expose ZI_BOOKING as Booking;
            }
            """;

    private static final String TEST_SOURCE_FILE = Paths.get(
            System.getProperty("java.io.tmpdir"), "sap-mcp", "dev_100",
            "service_definition", "ZTEST_SRVD.abap").toString();

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void saveServiceDefinition_success_atomicOperation() throws Exception {
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
                .arg("srvd_name", "ZTEST_SRVD")
                .arg("source_code", SRVD_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "<response>saved</response>");
        assertSystemHeader(result);

        // Verify atomic operation order: lock -> save -> unlock
        InOrder inOrder = inOrder(adtClient);
        inOrder.verify(adtClient).lockObject(any(), eq("ZTEST_SRVD"), eq("SERVICE_DEFINITION"), any(), any(), any());
        inOrder.verify(adtClient).saveObject(any(), eq("ZTEST_SRVD"), eq("SERVICE_DEFINITION"),
                eq(SRVD_SOURCE), eq(TEST_LOCK_HANDLE), eq(TEST_TRANSPORT), any(), any(), any());
        inOrder.verify(adtClient).unlockObject(any(), eq("ZTEST_SRVD"), eq("SERVICE_DEFINITION"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveServiceDefinition_saveFailure_unlocksObject() throws Exception {
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
                .arg("srvd_name", "ZTEST_SRVD")
                .arg("source_code", SRVD_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Save failed");

        verify(adtClient).unlockObject(any(), eq("ZTEST_SRVD"), eq("SERVICE_DEFINITION"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveServiceDefinition_withTransportNumber() throws Exception {
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
                .arg("srvd_name", "ZTEST_SRVD")
                .arg("source_code", SRVD_SOURCE)
                .arg("transport_number", userTransport)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        verify(adtClient).saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), eq(userTransport), any(), any(), any());
    }

    @Test
    void saveServiceDefinition_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("srvd_name", "ZTEST_SRVD")
                .arg("source_code", SRVD_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveServiceDefinition_missingSrvdName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_code", SRVD_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "srvd_name is required");
    }

    @Test
    void saveServiceDefinition_missingSourceCodeAndFile_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("srvd_name", "ZTEST_SRVD")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }

    @Test
    void saveServiceDefinition_withSourceFile_readsFromFile() throws Exception {
        LockResponse lockResponse = createMockLockResponse();
        setupExecuteInContext();

        when(fileStorageService.readSource(TEST_SOURCE_FILE))
                .thenReturn(SRVD_SOURCE);
        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn("<response>saved</response>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("srvd_name", "ZTEST_SRVD")
                .arg("source_file", TEST_SOURCE_FILE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        verify(fileStorageService).readSource(TEST_SOURCE_FILE);
    }

    @Test
    void saveServiceDefinition_bothSourceCodeAndFile_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("srvd_name", "ZTEST_SRVD")
                .arg("source_code", SRVD_SOURCE)
                .arg("source_file", TEST_SOURCE_FILE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Provide source_code OR source_file, not both");
    }

    @SuppressWarnings("unchecked")
    private void setupExecuteInContext() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenAnswer(invocation -> {
                    JcoOperation<Object> operation = invocation.getArgument(1);
                    return operation.execute(jcoDestination, mockSession);
                });
    }
}
