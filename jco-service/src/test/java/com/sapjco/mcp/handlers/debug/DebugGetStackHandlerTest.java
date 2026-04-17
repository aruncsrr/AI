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

class DebugGetStackHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugGetStackHandler handler;

    private JcoSession mockSession;

    private static final String STACK_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <dbg:debuggeeStack xmlns:dbg="http://www.sap.com/adt/debugger"
                    xmlns:adtcore="http://www.sap.com/adt/core">
                <dbg:stackEntry stackPosition="1" stackType="ABAP"
                        programName="ZCL_TEST_CLASS=================CP"
                        includeName="ZCL_TEST_CLASS=================CCAU"
                        line="42" eventType="METHOD" eventName="TEST_METHOD"
                        adtcore:uri="/sap/bc/adt/oo/classes/ZCL_TEST_CLASS/source/main"
                        isActive="true" systemProgram="false"/>
                <dbg:stackEntry stackPosition="2" stackType="ABAP"
                        programName="CL_ABAP_UNIT=================CP"
                        includeName="CL_ABAP_UNIT=================CM001"
                        line="100" eventType="METHOD" eventName="RUN"
                        adtcore:uri="/sap/bc/adt/oo/classes/CL_ABAP_UNIT/source/main"
                        isActive="false" systemProgram="true"/>
            </dbg:debuggeeStack>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void getStack_success() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STACK_XML);

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
    void getStack_rawXmlResponse() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STACK_XML);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "File:");
    }

    @Test
    void getStack_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void getStack_sessionNotFound_returnsError() {
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
    void getStack_executionError_returnsError() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Debug session error"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
    }
}
