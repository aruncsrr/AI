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

class DebugStepHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugStepHandler handler;

    private JcoSession mockSession;

    private static final String STEP_RESPONSE_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <dbg:debuggee xmlns:dbg="http://www.sap.com/adt/debugger"
                    processId="1234" serverName="sapserver_DEV_00"
                    isSteppingPossible="true" isTerminationPossible="true">
            </dbg:debuggee>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void step_success_defaultStepOver() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STEP_RESPONSE_XML);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // Raw XML response is returned
        assertContains(result, "debuggee");
    }

    @Test
    void step_success_stepInto() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STEP_RESPONSE_XML);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("operation", "stepInto")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // Raw XML response is returned
        assertContains(result, "debuggee");
    }

    @Test
    void step_success_stepReturn() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STEP_RESPONSE_XML);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("operation", "stepReturn")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // Raw XML response is returned
        assertContains(result, "debuggee");
    }

    @Test
    void step_rawXmlResponse() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STEP_RESPONSE_XML);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "debuggee");
    }

    @Test
    void step_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void step_sessionNotFound_returnsError() {
        when(jcoSessionManager.getSession("invalid-session"))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", "invalid-session")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Session not found");
    }
}
