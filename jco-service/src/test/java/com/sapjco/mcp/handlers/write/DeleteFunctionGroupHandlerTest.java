package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.DeleteResponse;
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
 * Unit tests for DeleteFunctionGroupHandler.
 * Tests ABAP function group deletion via ADT REST API.
 */
class DeleteFunctionGroupHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DeleteFunctionGroupHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void deleteFunctionGroup_success() throws Exception {
        // Arrange
        DeleteResponse deleteResponse = DeleteResponse.builder()
                .objectName("ZTEST_FG")
                .objectType("function_group")
                .httpStatus(200)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.deleteObject(any(), eq("ZTEST_FG"), eq("function_group"),
                isNull(), any(), any(), any()))
                .thenReturn(deleteResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZTEST_FG")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Function group deleted successfully");
        assertContains(result, "ZTEST_FG");
        assertSystemHeader(result);
    }

    @Test
    void deleteFunctionGroup_withTransport_success() throws Exception {
        // Arrange
        DeleteResponse deleteResponse = DeleteResponse.builder()
                .objectName("ZTEST_FG")
                .objectType("function_group")
                .httpStatus(200)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.deleteObject(any(), eq("ZTEST_FG"), eq("function_group"),
                eq("DEVK900001"), any(), any(), any()))
                .thenReturn(deleteResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZTEST_FG")
                .arg("transport_number", "DEVK900001")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Function group deleted successfully");
    }

    @Test
    void deleteFunctionGroup_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("group_name", "ZTEST_FG")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void deleteFunctionGroup_missingGroupName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "group_name is required");
    }

    @Test
    void deleteFunctionGroup_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("group_name", "ZTEST_FG")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void deleteFunctionGroup_deletionFails_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.deleteObject(any(), anyString(), anyString(),
                isNull(), any(), any(), any()))
                .thenThrow(new RuntimeException("Delete failed: 409 - Object is locked"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZTEST_FG_LOCKED")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Object is locked");
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
