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
 * Unit tests for DeleteInterfaceHandler.
 * Tests ABAP interface deletion via ADT REST API.
 */
class DeleteInterfaceHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DeleteInterfaceHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void deleteInterface_success() throws Exception {
        // Arrange
        DeleteResponse deleteResponse = DeleteResponse.builder()
                .objectName("ZIF_TEST_INTERFACE")
                .objectType("interface")
                .httpStatus(200)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.deleteObject(any(), eq("ZIF_TEST_INTERFACE"), eq("interface"),
                isNull(), any(), any(), any()))
                .thenReturn(deleteResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Interface deleted successfully");
        assertContains(result, "ZIF_TEST_INTERFACE");
        assertSystemHeader(result);
    }

    @Test
    void deleteInterface_withTransport_success() throws Exception {
        // Arrange
        DeleteResponse deleteResponse = DeleteResponse.builder()
                .objectName("ZIF_TEST_INTERFACE")
                .objectType("interface")
                .httpStatus(200)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.deleteObject(any(), eq("ZIF_TEST_INTERFACE"), eq("interface"),
                eq("DEVK900001"), any(), any(), any()))
                .thenReturn(deleteResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("transport_number", "DEVK900001")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Interface deleted successfully");
    }

    @Test
    void deleteInterface_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void deleteInterface_missingInterfaceName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "interface_name is required");
    }

    @Test
    void deleteInterface_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void deleteInterface_deletionFails_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.deleteObject(any(), anyString(), anyString(),
                isNull(), any(), any(), any()))
                .thenThrow(new RuntimeException("Delete failed: 409 - Object is locked"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("interface_name", "ZIF_LOCKED")
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
