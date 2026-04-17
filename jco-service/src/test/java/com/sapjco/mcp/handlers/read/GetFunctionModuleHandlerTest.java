package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetFunctionModuleHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetFunctionModuleHandler handler;

    private JcoSession mockTempSession;

    private static final Path TEST_FILE_PATH = Path.of("/tmp/sap-mcp/dev_100/function_module/SADT_REST.SADT_REST_RFC_ENDPOINT.abap");

    private static final String FM_SOURCE = """
            FUNCTION sadt_rest_rfc_endpoint.
            *"----------------------------------------------------------------------
            *"*"Local Interface:
            *"  IMPORTING
            *"     VALUE(IV_URI) TYPE  STRING
            *"----------------------------------------------------------------------
              DATA: lv_result TYPE string.
              " Function module implementation
            ENDFUNCTION.
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
        setupFileStorageService();
    }

    /**
     * Setup FileStorageService mock with specific test values.
     */
    private void setupFileStorageService() throws Exception {
        lenient().when(fileStorageService.writeSource(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(TEST_FILE_PATH);
        lenient().when(fileStorageService.getLineCount(any(Path.class))).thenReturn(12L);
        lenient().when(fileStorageService.getByteSize(any(Path.class))).thenReturn(350L);
    }

    @Test
    void getFunctionModule_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(FM_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "File:");  // File-based output
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getFunctionModule_withInactiveVersion() throws Exception {
        setupTempSession();
        setupExecuteInContext(FM_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .arg("version", "inactive")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getFunctionModule_withExistingSession() throws Exception {
        JcoSession mockSession = mockGetSession(TEST_SESSION_ID);
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(FM_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // Should NOT create or destroy temp session
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager, never()).destroySession(anyString());
    }

    @Test
    void getFunctionModule_missingGroupName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "group_name is required");
    }

    @Test
    void getFunctionModule_missingModuleName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("group_name", "SADT_REST")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "fmodule_name is required");
    }

    @Test
    void getFunctionModule_notFound_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Function module not found"));

        CallToolRequest request = requestBuilder()
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "ZNONEXISTENT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getFunctionModule_namespacedObjects() throws Exception {
        setupTempSession();
        setupExecuteInContext(FM_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("group_name", "/SCMTMS/TOR_HELPER")
                .arg("fmodule_name", "/SCMTMS/TOR_HELPER_GET")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    private void setupTempSession() throws Exception {
        lenient().when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_TEMP_SESSION_ID);
        lenient().when(jcoSessionManager.getSession(TEST_TEMP_SESSION_ID)).thenReturn(mockTempSession);
    }

    private void setupExecuteInContext(String returnValue) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(returnValue);
    }
}
