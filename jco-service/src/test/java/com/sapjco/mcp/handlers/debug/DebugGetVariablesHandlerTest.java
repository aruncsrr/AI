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

class DebugGetVariablesHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugGetVariablesHandler handler;

    private JcoSession mockSession;

    private static final String VARIABLES_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <STPDA_ADT_VARIABLES>
                <STPDA_ADT_VARIABLE>
                    <ID>LV_VALUE</ID>
                    <NAME>LV_VALUE</NAME>
                    <DECLARED_TYPE_NAME>STRING</DECLARED_TYPE_NAME>
                    <ACTUAL_TYPE_NAME>STRING</ACTUAL_TYPE_NAME>
                    <VALUE>Hello World</VALUE>
                    <HEX_VALUE>48656C6C6F</HEX_VALUE>
                    <META_TYPE>simple</META_TYPE>
                    <LENGTH>11</LENGTH>
                    <TABLE_LINES>0</TABLE_LINES>
                </STPDA_ADT_VARIABLE>
                <STPDA_ADT_VARIABLE>
                    <ID>LT_DATA</ID>
                    <NAME>LT_DATA</NAME>
                    <DECLARED_TYPE_NAME>TT_DATA</DECLARED_TYPE_NAME>
                    <ACTUAL_TYPE_NAME>TT_DATA</ACTUAL_TYPE_NAME>
                    <VALUE></VALUE>
                    <HEX_VALUE></HEX_VALUE>
                    <META_TYPE>table</META_TYPE>
                    <LENGTH>0</LENGTH>
                    <TABLE_LINES>5</TABLE_LINES>
                </STPDA_ADT_VARIABLE>
            </STPDA_ADT_VARIABLES>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void getVariables_success() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(VARIABLES_XML);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // File path is returned in metadata (XML written to file)
        assertContains(result, "File:");
        assertContains(result, "bytes");
    }

    @Test
    void getVariables_withLocalsParent() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(VARIABLES_XML);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("parent_id", "@LOCALS")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void getVariables_rawXmlResponse() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(VARIABLES_XML);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "File:");
    }

    @Test
    void getVariables_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void getVariables_sessionNotFound_returnsError() {
        when(jcoSessionManager.getSession("invalid-session"))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", "invalid-session")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void getVariables_executionError_returnsError() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Debug session error"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
    }
}
