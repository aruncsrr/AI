package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateResponse;
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
 * Unit tests for CreateFunctionGroupHandler.
 * Tests ABAP function group creation via ADT REST API.
 */
class CreateFunctionGroupHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private CreateFunctionGroupHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void createFunctionGroup_success_minimalParameters() throws Exception {
        // Arrange
        CreateResponse createResponse = CreateResponse.builder()
                .objectUri("/sap/bc/adt/functions/groups/ZTEST_FG")
                .objectName("ZTEST_FG")
                .objectType("function_group")
                .httpStatus(201)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.createFunctionGroup(any(), anyString(), anyString(), anyString(),
                isNull(), any(), any(), any()))
                .thenReturn(createResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZTEST_FG")
                .arg("description", "Test function group")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Function group created successfully");
        assertContains(result, "ZTEST_FG");
        assertContains(result, "$TMP");
        assertContains(result, "INACTIVE");
        assertSystemHeader(result);
    }

    @Test
    void createFunctionGroup_success_withTransport() throws Exception {
        // Arrange
        CreateResponse createResponse = CreateResponse.builder()
                .objectUri("/sap/bc/adt/functions/groups/ZTEST_FG_TR")
                .objectName("ZTEST_FG_TR")
                .objectType("function_group")
                .httpStatus(201)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.createFunctionGroup(any(), eq("ZTEST_FG_TR"), anyString(), anyString(),
                eq("DEVK900001"), any(), any(), any()))
                .thenReturn(createResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZTEST_FG_TR")
                .arg("description", "Function group with transport")
                .arg("package_name", "ZTEST_PKG")
                .arg("transport_number", "DEVK900001")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Function group created successfully");
        assertContains(result, "ZTEST_FG_TR");
    }

    @Test
    void createFunctionGroup_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("group_name", "ZTEST_FG")
                .arg("description", "Test function group")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void createFunctionGroup_missingGroupName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("description", "Test function group")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "group_name is required");
    }

    @Test
    void createFunctionGroup_missingDescription_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZTEST_FG")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "description is required");
    }

    @Test
    void createFunctionGroup_missingPackageName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZTEST_FG")
                .arg("description", "Test function group")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "package_name is required");
    }

    @Test
    void createFunctionGroup_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("group_name", "ZTEST_FG")
                .arg("description", "Test function group")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void createFunctionGroup_creationFails_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.createFunctionGroup(any(), anyString(), anyString(), anyString(),
                isNull(), any(), any(), any()))
                .thenThrow(new RuntimeException("Function group already exists"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZEXISTING_FG")
                .arg("description", "Test function group")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Function group already exists");
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
