package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.service.JcoSessionManager.JcoOperation;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ActivateObjectHandler.
 * Tests object activation via ADT REST API.
 */
class ActivateObjectHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private ActivateObjectHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void activateObject_withSession_success() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.activateObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn("<activation>success</activation>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<activation>success</activation>");  // Raw XML response
        assertSystemHeader(result);

        // Verify activation was called
        verify(adtClient).activateObject(any(), eq("ZCL_TEST_CLASS"), eq("CLASS"), any(), any(), any());
    }

    @Test
    void activateObject_interface_success() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.activateObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn("<activation>success</activation>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZIF_TEST_INTERFACE")
                .arg("object_type", "interface")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<activation>success</activation>");
    }

    @Test
    void activateObject_program_success() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.activateObject(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn("<activation>success</activation>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZTEST_PROGRAM")
                .arg("object_type", "program")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<activation>success</activation>");
    }

    @Test
    void activateObject_withoutSession_returnsError() {
        // Arrange - no session_id provided
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert - handler returns error when no session provided (current behavior)
        assertError(result);
        assertContains(result, "session_id is recommended");
    }

    @Test
    void activateObject_missingObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void activateObject_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void activateObject_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void activateObject_activationFailure_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        doThrow(new RuntimeException("Syntax error in line 10"))
                .when(adtClient).activateObject(any(), anyString(), anyString(), any(), any(), any());

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_BROKEN_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Syntax error");
    }

    @Test
    void activateObject_functionModule_success() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.activateFunctionModule(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn("<activation>success</activation>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "Z_FHP_PLN_CLEANUP")
                .arg("object_type", "function_module")
                .arg("group_name", "ZFHP_PLANNING")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<activation>success</activation>");
        assertSystemHeader(result);

        // Verify activateFunctionModule was called (not activateObject)
        verify(adtClient).activateFunctionModule(any(), eq("ZFHP_PLANNING"), eq("Z_FHP_PLN_CLEANUP"), any(), any(), any());
        verify(adtClient, never()).activateObject(any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void activateObject_functionModule_missingGroupName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "Z_FHP_PLN_CLEANUP")
                .arg("object_type", "function_module")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "group_name is required when object_type is function_module");
    }

    /**
     * Helper to setup executeInContext mock that invokes the operation.
     */
    @SuppressWarnings("unchecked")
    private void setupExecuteInContext() throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenAnswer(invocation -> {
                    JcoOperation<Object> operation = invocation.getArgument(1);
                    return operation.execute(jcoDestination, mockSession);
                });
    }
}
