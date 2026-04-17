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
 * Unit tests for SaveBehaviorDefinitionHandler.
 * Tests atomic lock/save/unlock operations for BDEF objects.
 */
class SaveBehaviorDefinitionHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveBehaviorDefinitionHandler handler;

    private JcoSession mockSession;

    private static final String BDEF_SOURCE = """
            managed implementation in class zbp_r_travel unique;
            strict ( 2 );

            define behavior for R_TravelTP alias Travel
            persistent table ztrav
            lock master
            {
              create;
              update;
              delete;
            }
            """;

    private static final String TEST_SOURCE_FILE = Paths.get(
            System.getProperty("java.io.tmpdir"), "sap-mcp", "dev_100",
            "behavior_definition", "R_TRAVELPROCTP.abap").toString();

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void saveBehaviorDefinition_success_atomicOperation() throws Exception {
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
                .arg("bdef_name", "R_TRAVELPROCTP")
                .arg("source_code", BDEF_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "<response>saved</response>");
        assertSystemHeader(result);

        // Verify atomic operation order: lock -> save -> unlock
        InOrder inOrder = inOrder(adtClient);
        inOrder.verify(adtClient).lockObject(any(), eq("R_TRAVELPROCTP"), eq("BEHAVIOR_DEFINITION"), any(), any(), any());
        inOrder.verify(adtClient).saveObject(any(), eq("R_TRAVELPROCTP"), eq("BEHAVIOR_DEFINITION"),
                eq(BDEF_SOURCE), eq(TEST_LOCK_HANDLE), eq(TEST_TRANSPORT), any(), any(), any());
        inOrder.verify(adtClient).unlockObject(any(), eq("R_TRAVELPROCTP"), eq("BEHAVIOR_DEFINITION"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveBehaviorDefinition_saveFailure_unlocksObject() throws Exception {
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
                .arg("bdef_name", "R_TRAVELPROCTP")
                .arg("source_code", BDEF_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Save failed");

        // CRITICAL: Verify unlock was still called after save failure
        verify(adtClient).unlockObject(any(), eq("R_TRAVELPROCTP"), eq("BEHAVIOR_DEFINITION"),
                eq(TEST_LOCK_HANDLE), any(), any(), any());
    }

    @Test
    void saveBehaviorDefinition_withTransportNumber() throws Exception {
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
                .arg("bdef_name", "R_TRAVELPROCTP")
                .arg("source_code", BDEF_SOURCE)
                .arg("transport_number", userTransport)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        verify(adtClient).saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), eq(userTransport), any(), any(), any());
    }

    @Test
    void saveBehaviorDefinition_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("bdef_name", "R_TRAVELPROCTP")
                .arg("source_code", BDEF_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveBehaviorDefinition_missingBdefName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_code", BDEF_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "bdef_name is required");
    }

    @Test
    void saveBehaviorDefinition_missingSourceCodeAndFile_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("bdef_name", "R_TRAVELPROCTP")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }

    @Test
    void saveBehaviorDefinition_withSourceFile_readsFromFile() throws Exception {
        LockResponse lockResponse = createMockLockResponse();
        setupExecuteInContext();

        when(fileStorageService.readSource(TEST_SOURCE_FILE))
                .thenReturn(BDEF_SOURCE);
        when(adtClient.lockObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(lockResponse);
        when(adtClient.saveObject(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any())).thenReturn("<response>saved</response>");
        when(adtClient.unlockObject(any(), anyString(), anyString(), anyString(),
                any(), any(), any())).thenReturn("<unlock/>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("bdef_name", "R_TRAVELPROCTP")
                .arg("source_file", TEST_SOURCE_FILE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        verify(fileStorageService).readSource(TEST_SOURCE_FILE);
    }

    @Test
    void saveBehaviorDefinition_bothSourceCodeAndFile_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("bdef_name", "R_TRAVELPROCTP")
                .arg("source_code", BDEF_SOURCE)
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
