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

class GetBehaviorDefinitionHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetBehaviorDefinitionHandler handler;

    private JcoSession mockTempSession;

    private static final String BDEF_SOURCE = """
            managed implementation in class zbp_r_travel unique;
            strict ( 2 );

            define behavior for R_TravelTP alias Travel
            persistent table ztrav
            lock master
            authorization master ( instance )
            {
              create;
              update;
              delete;
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getBehaviorDefinition_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(BDEF_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("bdef_name", "R_TRAVELPROCTP")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "R_TRAVELPROCTP");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getBehaviorDefinition_withInactiveVersion() throws Exception {
        setupTempSession();
        setupExecuteInContext(BDEF_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("bdef_name", "R_TRAVELPROCTP")
                .arg("version", "inactive")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getBehaviorDefinition_missingName_returnsError() {
        CallToolRequest request = requestBuilder().build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "bdef_name is required");
    }

    @Test
    void getBehaviorDefinition_notFound_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Behavior definition not found"));

        CallToolRequest request = requestBuilder()
                .arg("bdef_name", "ZNONEXISTENT")
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
