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
 * Unit tests for CreateIncludeHandler.
 * Tests ABAP include program creation via ADT REST API.
 */
class CreateIncludeHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private CreateIncludeHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void createInclude_success_minimalParameters() throws Exception {
        // Arrange
        CreateResponse createResponse = CreateResponse.builder()
                .objectUri("/sap/bc/adt/programs/includes/ZTEST_INCLUDE")
                .objectName("ZTEST_INCLUDE")
                .objectType("include")
                .httpStatus(201)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.createInclude(any(), anyString(), anyString(), anyString(),
                isNull(), any(), any(), any()))
                .thenReturn(createResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("include_name", "ZTEST_INCLUDE")
                .arg("description", "Test include program")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Include created successfully");
        assertContains(result, "ZTEST_INCLUDE");
        assertContains(result, "$TMP");
        assertContains(result, "INACTIVE");
        assertSystemHeader(result);
    }

    @Test
    void createInclude_success_withTransport() throws Exception {
        // Arrange
        CreateResponse createResponse = CreateResponse.builder()
                .objectUri("/sap/bc/adt/programs/includes/ZTEST_INC_TR")
                .objectName("ZTEST_INC_TR")
                .objectType("include")
                .httpStatus(201)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.createInclude(any(), eq("ZTEST_INC_TR"), anyString(), anyString(),
                eq("DEVK900001"), any(), any(), any()))
                .thenReturn(createResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("include_name", "ZTEST_INC_TR")
                .arg("description", "Include with transport")
                .arg("package_name", "ZTEST_PKG")
                .arg("transport_number", "DEVK900001")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Include created successfully");
        assertContains(result, "ZTEST_INC_TR");
    }

    @Test
    void createInclude_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("include_name", "ZTEST_INCLUDE")
                .arg("description", "Test include program")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void createInclude_missingIncludeName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("description", "Test include program")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "include_name is required");
    }

    @Test
    void createInclude_missingDescription_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("include_name", "ZTEST_INCLUDE")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "description is required");
    }

    @Test
    void createInclude_missingPackageName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("include_name", "ZTEST_INCLUDE")
                .arg("description", "Test include program")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "package_name is required");
    }

    @Test
    void createInclude_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("include_name", "ZTEST_INCLUDE")
                .arg("description", "Test include program")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void createInclude_creationFails_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.createInclude(any(), anyString(), anyString(), anyString(),
                isNull(), any(), any(), any()))
                .thenThrow(new RuntimeException("Include already exists"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("include_name", "ZEXISTING_INC")
                .arg("description", "Test include program")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Include already exists");
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
