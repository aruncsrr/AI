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

class GetTransportRequestsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetTransportRequestsHandler handler;

    private JcoSession mockTempSession;

    private static final String TRANSPORT_CHECK_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml" version="1.0">
                <asx:values>
                    <DATA>
                        <REQUESTS>
                            <CTS_REQUEST>
                                <REQ_HEADER>
                                    <TRKORR>NPLK900001</TRKORR>
                                    <TRFUNCTION>K</TRFUNCTION>
                                    <TRSTATUS>D</TRSTATUS>
                                    <AS4USER>DEVELOPER</AS4USER>
                                    <AS4TEXT>Development transport</AS4TEXT>
                                </REQ_HEADER>
                            </CTS_REQUEST>
                        </REQUESTS>
                    </DATA>
                </asx:values>
            </asx:abap>
            """;

    private static final String OBJECT_METADATA_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <class:abapClass xmlns:class="http://www.sap.com/adt/oo/classes" 
                    adtcore:packageName="ZTEST_PKG" xmlns:adtcore="http://www.sap.com/adt/core">
            </class:abapClass>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getTransportRequests_success() throws Exception {
        setupTempSession();
        // First call: fetch object metadata, second call: transport check
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(OBJECT_METADATA_RESPONSE)
                .thenReturn(TRANSPORT_CHECK_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // Raw XML response is returned
        assertContains(result, "asx:abap");
        assertContains(result, "NPLK900001");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getTransportRequests_withDevclass() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(TRANSPORT_CHECK_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("devclass", "ZTEST_PKG")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getTransportRequests_missingObjectName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("object_type", "class")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void getTransportRequests_missingObjectType_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void getTransportRequests_executionError_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Transport check failed"));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("devclass", "ZTEST_PKG")
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
}
