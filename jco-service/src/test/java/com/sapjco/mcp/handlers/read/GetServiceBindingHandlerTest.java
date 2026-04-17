package com.sapjco.mcp.handlers.read;

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

class GetServiceBindingHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetServiceBindingHandler handler;

    private JcoSession mockTempSession;

    private static final String SRVB_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <businessservices:serviceBinding xmlns:businessservices="http://www.sap.com/adt/businessservices"
              adtcore:name="ZTEST_SRVB" adtcore:type="SRVB/SVB"
              xmlns:adtcore="http://www.sap.com/adt/core">
              <businessservices:serviceBindingProperties bindingType="ODATA" serviceVersion="V2"/>
              <businessservices:services>
                <businessservices:content>
                  <businessservices:serviceDefinition adtcore:name="ZTEST_SRVD"/>
                </businessservices:content>
              </businessservices:services>
            </businessservices:serviceBinding>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getServiceBinding_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(SRVB_XML);

        CallToolRequest request = requestBuilder()
                .arg("srvb_name", "ZTEST_SRVB")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "ZTEST_SRVB");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getServiceBinding_withInactiveVersion() throws Exception {
        setupTempSession();
        setupExecuteInContext(SRVB_XML);

        CallToolRequest request = requestBuilder()
                .arg("srvb_name", "ZTEST_SRVB")
                .arg("version", "inactive")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getServiceBinding_missingName_returnsError() {
        CallToolRequest request = requestBuilder().build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "srvb_name is required");
    }

    @Test
    void getServiceBinding_notFound_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Service binding not found"));

        CallToolRequest request = requestBuilder()
                .arg("srvb_name", "ZNONEXISTENT")
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
