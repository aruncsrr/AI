package com.sapjco.mcp.handlers.transport;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetTransportContentsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetTransportContentsHandler handler;

    private JcoSession mockTempSession;

    private static final String TRANSPORT_CONTENTS_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <tm:request xmlns:tm="http://www.sap.com/cts/adt/transportorganizer" number="NPLK900001">
                <tm:task number="NPLK900002" owner="DEVELOPER" desc="Task 1">
                    <tm:abap_object pgmid="R3TR" type="CLAS" name="ZCL_TEST" obj_desc="Test class"/>
                </tm:task>
                <tm:abap_object pgmid="R3TR" type="PROG" name="ZTEST_PROG" obj_desc="Test program"/>
            </tm:request>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getTransportContents_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(TRANSPORT_CONTENTS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("transport_id", "NPLK900001")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // Metadata summary is returned (XML written to file)
        assertContains(result, "NPLK900001");
        assertContains(result, "File:");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getTransportContents_returnsFilePath() throws Exception {
        setupTempSession();
        setupExecuteInContext(TRANSPORT_CONTENTS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("transport_id", "NPLK900001")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
        assertContains(result, "bytes");
    }

    @Test
    void getTransportContents_missingTransportId_returnsError() {
        CallToolRequest request = requestBuilder()
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "transport_id is required");
    }

    @Test
    void getTransportContents_executionError_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Transport not found"));

        CallToolRequest request = requestBuilder()
                .arg("transport_id", "INVALID123")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
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
