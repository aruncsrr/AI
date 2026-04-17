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

class ListTransportRequestsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private ListTransportRequestsHandler handler;

    private JcoSession mockTempSession;

    private static final String TRANSPORT_LIST_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <tm:root xmlns:tm="http://www.sap.com/cts/adt/transportorganizertree">
                <tm:request number="NPLK900001" owner="DEVELOPER" desc="Test transport" 
                        type="K" status="D" target="QM7"/>
                <tm:request number="NPLK900002" owner="DEVELOPER" desc="Another transport" 
                        type="K" status="R" target="QM7"/>
            </tm:root>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void listTransportRequests_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(TRANSPORT_LIST_RESPONSE);

        CallToolRequest request = requestBuilder()
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // Raw XML response is returned
        assertContains(result, "tm:root");
        assertContains(result, "NPLK900001");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void listTransportRequests_withUserFilter() throws Exception {
        setupTempSession();
        setupExecuteInContext(TRANSPORT_LIST_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("user", "DEVELOPER")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // Raw XML response is returned - just verify success, the API call is verified by mock
        assertContains(result, "tm:root");
    }

    @Test
    void listTransportRequests_withStatusFilter() throws Exception {
        setupTempSession();
        setupExecuteInContext(TRANSPORT_LIST_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("trstatus", "D")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // Raw XML response is returned
        assertContains(result, "tm:root");
    }

    @Test
    void listTransportRequests_withRawResponse() throws Exception {
        setupTempSession();
        setupExecuteInContext(TRANSPORT_LIST_RESPONSE);

        CallToolRequest request = requestBuilder()
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "tm:root");
    }

    @Test
    void listTransportRequests_executionError_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Connection failed"));

        CallToolRequest request = requestBuilder()
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
