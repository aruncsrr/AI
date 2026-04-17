package com.sapjco.mcp.handlers.debug;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DebugSetVariableHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugSetVariableHandler handler;

    private JcoSession mockSession;

    private static final String SET_VAR_RESPONSE = "Variable LV_VALUE set to 'Hello'";

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void setVariable_success() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(SET_VAR_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("variable_name", "LV_VALUE")
                .arg("new_value", "Hello")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "LV_VALUE");
    }

    @Test
    void setVariable_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("variable_name", "LV_VALUE")
                .arg("new_value", "Hello")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void setVariable_missingVariableName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("new_value", "Hello")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "variable_name is required");
    }

    @Test
    void setVariable_missingNewValue_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("variable_name", "LV_VALUE")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "new_value is required");
    }

    @Test
    void setVariable_sessionNotFound_returnsError() {
        when(jcoSessionManager.getSession("invalid-session"))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", "invalid-session")
                .arg("variable_name", "LV_VALUE")
                .arg("new_value", "Hello")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Session not found");
    }
}
