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

class DebugStepToLineHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugStepToLineHandler handler;

    private JcoSession mockSession;

    private static final String STEP_TO_LINE_RESPONSE = "Stepped to line 42";

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void stepToLine_success_defaultRunToLine() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STEP_TO_LINE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_uri", "/sap/bc/adt/oo/classes/zclass/includes/testclasses#start=42")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "stepRunToLine");
    }

    @Test
    void stepToLine_success_jumpToLine() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STEP_TO_LINE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_uri", "/sap/bc/adt/oo/classes/zclass/includes/testclasses#start=42")
                .arg("step_type", "stepJumpToLine")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "stepJumpToLine");
    }

    @Test
    void stepToLine_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("source_uri", "/sap/bc/adt/oo/classes/zclass/includes/testclasses#start=42")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void stepToLine_missingSourceUri_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "source_uri is required");
    }

    @Test
    void stepToLine_sessionNotFound_returnsError() {
        when(jcoSessionManager.getSession("invalid-session"))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", "invalid-session")
                .arg("source_uri", "/sap/bc/adt/oo/classes/zclass/includes/testclasses#start=42")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Session not found");
    }
}
