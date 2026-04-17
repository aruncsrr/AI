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

class GetServiceDefinitionHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetServiceDefinitionHandler handler;

    private JcoSession mockTempSession;

    private static final String SRVD_SOURCE = """
            @EndUserText.label: 'Travel Service Definition'
            define service ZTEST_SRVD {
              expose ZI_TRAVEL as Travel;
              expose ZI_BOOKING as Booking;
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getServiceDefinition_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(SRVD_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("srvd_name", "ZTEST_SRVD")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "ZTEST_SRVD");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getServiceDefinition_withInactiveVersion() throws Exception {
        setupTempSession();
        setupExecuteInContext(SRVD_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("srvd_name", "ZTEST_SRVD")
                .arg("version", "inactive")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getServiceDefinition_missingName_returnsError() {
        CallToolRequest request = requestBuilder().build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "srvd_name is required");
    }

    @Test
    void getServiceDefinition_notFound_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Service definition not found"));

        CallToolRequest request = requestBuilder()
                .arg("srvd_name", "ZNONEXISTENT")
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
