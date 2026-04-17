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
 * Unit tests for DeleteClassHandler.
 * Tests ABAP class deletion via ADT REST API.
 */
class DeleteClassHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DeleteClassHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void deleteClass_success() throws Exception {
        // Arrange
        DeleteResponse deleteResponse = DeleteResponse.builder()
                .objectName("ZCL_TEST_CLASS")
                .objectType("class")
                .httpStatus(200)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.deleteObject(any(), eq("ZCL_TEST_CLASS"), eq("class"),
                isNull(), any(), any(), any()))
                .thenReturn(deleteResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Class deleted successfully");
        assertContains(result, "ZCL_TEST_CLASS");
        assertSystemHeader(result);
    }

    @Test
    void deleteClass_withTransport_success() throws Exception {
        // Arrange
        DeleteResponse deleteResponse = DeleteResponse.builder()
                .objectName("ZCL_TEST_CLASS")
                .objectType("class")
                .httpStatus(200)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.deleteObject(any(), eq("ZCL_TEST_CLASS"), eq("class"),
                eq("DEVK900001"), any(), any(), any()))
                .thenReturn(deleteResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("transport_number", "DEVK900001")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Class deleted successfully");
    }

    @Test
    void deleteClass_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void deleteClass_missingClassName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "class_name is required");
    }

    @Test
    void deleteClass_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("class_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void deleteClass_deletionFails_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.deleteObject(any(), anyString(), anyString(),
                isNull(), any(), any(), any()))
                .thenThrow(new RuntimeException("Delete failed: 409 - Object is locked"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_LOCKED")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Object is locked");
    }

    @Test
    void deleteClass_objectNotFound_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.deleteObject(any(), anyString(), anyString(),
                isNull(), any(), any(), any()))
                .thenThrow(new RuntimeException("Delete failed: 404 - Object not found: ZCL_MISSING"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_MISSING")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Object not found");
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
