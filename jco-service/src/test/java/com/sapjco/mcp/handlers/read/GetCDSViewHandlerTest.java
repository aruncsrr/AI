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

class GetCDSViewHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetCDSViewHandler handler;

    private JcoSession mockTempSession;

    private static final String CDS_VIEW_SOURCE = """
            @AbapCatalog.sqlViewName: 'ZIFLIGHT'
            @AccessControl.authorizationCheck: #NOT_REQUIRED
            define view I_FLIGHT as select from sflight {
              key carrid,
              key connid,
              key fldate,
              price,
              currency
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getCDSView_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(CDS_VIEW_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("cds_view_name", "I_FLIGHT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "I_FLIGHT");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getCDSView_withInactiveVersion() throws Exception {
        setupTempSession();
        setupExecuteInContext(CDS_VIEW_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("cds_view_name", "I_FLIGHT")
                .arg("version", "inactive")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getCDSView_missingName_returnsError() {
        CallToolRequest request = requestBuilder().build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "cds_view_name is required");
    }

    @Test
    void getCDSView_notFound_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("CDS view not found"));

        CallToolRequest request = requestBuilder()
                .arg("cds_view_name", "ZNONEXISTENT")
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
