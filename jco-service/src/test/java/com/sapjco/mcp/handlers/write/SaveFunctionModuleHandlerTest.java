package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaveFunctionModuleHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveFunctionModuleHandler handler;

    private JcoSession mockSession;

    private static final String FM_SOURCE = """
            FUNCTION sadt_rest_rfc_endpoint.
            *"----------------------------------------------------------------------
            *"*"Local Interface:
            *"  IMPORTING
            *"     VALUE(IV_URI) TYPE  STRING
            *"----------------------------------------------------------------------
              DATA: lv_result TYPE string.
              " Updated function module implementation
            ENDFUNCTION.
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void saveFunctionModule_success() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn("<response>saved</response>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .arg("source_code", FM_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
    }

    @Test
    void saveFunctionModule_withTransportNumber() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn("<response>saved</response>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .arg("source_code", FM_SOURCE)
                .arg("transport_number", TEST_TRANSPORT)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void saveFunctionModule_withSourceFile() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn("<response>saved</response>");
        lenient().when(fileStorageService.readSource(anyString())).thenReturn(FM_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .arg("source_file", "/tmp/sap-mcp/dev_100/function_module/SADT_REST.SADT_REST_RFC_ENDPOINT.abap")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        verify(fileStorageService).readSource("/tmp/sap-mcp/dev_100/function_module/SADT_REST.SADT_REST_RFC_ENDPOINT.abap");
    }

    @Test
    void saveFunctionModule_namespacedObjects() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn("<response>saved</response>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "/SCMTMS/TOR_HELPER")
                .arg("fmodule_name", "/SCMTMS/TOR_HELPER_GET")
                .arg("source_code", FM_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void saveFunctionModule_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .arg("source_code", FM_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveFunctionModule_missingGroupName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .arg("source_code", FM_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "group_name is required");
    }

    @Test
    void saveFunctionModule_missingModuleName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "SADT_REST")
                .arg("source_code", FM_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "fmodule_name is required");
    }

    @Test
    void saveFunctionModule_missingSourceCode_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }

    @Test
    void saveFunctionModule_bothSourceCodeAndFile_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .arg("source_code", FM_SOURCE)
                .arg("source_file", "/tmp/some/file.abap")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "source_code OR source_file");
    }

    @Test
    void saveFunctionModule_sessionNotFound_returnsError() {
        lenient().when(jcoSessionManager.getSession("nonexistent-session")).thenReturn(null);

        CallToolRequest request = requestBuilder()
                .arg("session_id", "nonexistent-session")
                .arg("group_name", "SADT_REST")
                .arg("fmodule_name", "SADT_REST_RFC_ENDPOINT")
                .arg("source_code", FM_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Session not found");
    }
}
