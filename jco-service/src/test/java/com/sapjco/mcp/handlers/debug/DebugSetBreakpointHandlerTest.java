package com.sapjco.mcp.handlers.debug;

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

/**
 * Unit tests for DebugSetBreakpointHandler.
 * Tests setting additional breakpoints during active debug sessions.
 */
class DebugSetBreakpointHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugSetBreakpointHandler handler;

    private JcoSession mockTempSession;

    private static final String BREAKPOINT_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <dbg:breakpoints xmlns:dbg="http://www.sap.com/adt/debugger">
                <breakpoint dbg:id="BP_12345" kind="line" clientId="BP_1234567890"/>
            </dbg:breakpoints>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Success Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void debugSetBreakpoint_success_class() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "TESTUSER")
                .arg("terminal_id", "TERM_12345")
                .arg("ide_id", "IDE_67890")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Additional Breakpoint Set");
        assertContains(result, "ZCL_TEST_CLASS");
        assertContains(result, "Line: 42");
        assertContains(result, "TESTUSER");
        assertSystemHeader(result);

        // Verify temp session lifecycle
        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void debugSetBreakpoint_success_withSessionId() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "TESTUSER")
                .arg("terminal_id", "TERM_12345")
                .arg("ide_id", "IDE_67890")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);

        // Should NOT create temp session when session_id is provided
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
    }

    @Test
    void debugSetBreakpoint_success_implementations() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("line_number", 100)
                .arg("include_type", "implementations")
                .arg("request_user", "TESTUSER")
                .arg("terminal_id", "TERM_12345")
                .arg("ide_id", "IDE_67890")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "class/implementations");
    }

    @Test
    void debugSetBreakpoint_success_program() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZTEST_PROGRAM")
                .arg("object_type", "program")
                .arg("line_number", 50)
                .arg("request_user", "TESTUSER")
                .arg("terminal_id", "TERM_12345")
                .arg("ide_id", "IDE_67890")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "ZTEST_PROGRAM");
        assertContains(result, "program");
    }

    @Test
    void debugSetBreakpoint_success_namespacedClass() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BREAKPOINT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "/SCMTMS/CL_TOR_HELPER")
                .arg("object_type", "class")
                .arg("line_number", 25)
                .arg("request_user", "TESTUSER")
                .arg("terminal_id", "TERM_12345")
                .arg("ide_id", "IDE_67890")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "/SCMTMS/CL_TOR_HELPER");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void debugSetBreakpoint_missingObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "TESTUSER")
                .arg("terminal_id", "TERM_12345")
                .arg("ide_id", "IDE_67890")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void debugSetBreakpoint_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("line_number", 42)
                .arg("request_user", "TESTUSER")
                .arg("terminal_id", "TERM_12345")
                .arg("ide_id", "IDE_67890")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void debugSetBreakpoint_missingRequestUser_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("terminal_id", "TERM_12345")
                .arg("ide_id", "IDE_67890")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "request_user is required");
    }

    @Test
    void debugSetBreakpoint_missingTerminalId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "TESTUSER")
                .arg("ide_id", "IDE_67890")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "terminal_id is required");
    }

    @Test
    void debugSetBreakpoint_missingIdeId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "TESTUSER")
                .arg("terminal_id", "TERM_12345")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "ide_id is required");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void debugSetBreakpoint_adtError_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Debug session not active"));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("line_number", 42)
                .arg("request_user", "TESTUSER")
                .arg("terminal_id", "TERM_12345")
                .arg("ide_id", "IDE_67890")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Debug session not active");

        // Verify temp session cleanup on error
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private void setupTempSession() throws Exception {
        lenient().when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_TEMP_SESSION_ID);
        lenient().when(jcoSessionManager.getSession(TEST_TEMP_SESSION_ID)).thenReturn(mockTempSession);
    }

    private void setupExecuteInContext(String returnValue) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenAnswer(invocation -> returnValue);
    }
}
