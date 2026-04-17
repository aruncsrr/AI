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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateClassHandler.
 * Tests ABAP class creation via ADT REST API.
 */
class CreateClassHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private CreateClassHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = mockGetSession();
    }

    @Test
    void createClass_success_minimalParameters() throws Exception {
        // Arrange
        CreateResponse createResponse = CreateResponse.builder()
                .objectUri("/sap/bc/adt/oo/classes/ZCL_TEST_CLASS")
                .objectName("ZCL_TEST_CLASS")
                .objectType("class")
                .httpStatus(201)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.createClass(any(), anyString(), anyString(), anyString(),
                isNull(), anyString(), anyBoolean(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(createResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("description", "Test class")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Class created successfully");
        assertContains(result, "ZCL_TEST_CLASS");
        assertContains(result, "$TMP");
        assertContains(result, "INACTIVE");
        assertSystemHeader(result);
    }

    @Test
    void createClass_success_allParameters() throws Exception {
        // Arrange
        CreateResponse createResponse = CreateResponse.builder()
                .objectUri("/sap/bc/adt/oo/classes/ZCL_FULL_CLASS")
                .objectName("ZCL_FULL_CLASS")
                .objectType("class")
                .httpStatus(201)
                .rawResponse("")
                .build();

        setupExecuteInContext();
        when(adtClient.createClass(any(), eq("ZCL_FULL_CLASS"), anyString(), anyString(),
                eq("DEVK900001"), eq("PUBLIC"), eq(false), eq("ZCL_SUPER"), any(), any(), any(), any()))
                .thenReturn(createResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_FULL_CLASS")
                .arg("description", "Full test class")
                .arg("package_name", "ZTEST_PKG")
                .arg("transport_number", "DEVK900001")
                .arg("visibility", "PUBLIC")
                .arg("final", false)
                .arg("super_class", "ZCL_SUPER")
                .arg("interfaces", List.of("ZIF_INTERFACE1", "ZIF_INTERFACE2"))
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Class created successfully");
        assertContains(result, "ZCL_FULL_CLASS");
    }

    @Test
    void createClass_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("description", "Test class")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void createClass_missingClassName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("description", "Test class")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "class_name is required");
    }

    @Test
    void createClass_missingDescription_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "description is required");
    }

    @Test
    void createClass_missingPackageName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("description", "Test class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "package_name is required");
    }

    @Test
    void createClass_sessionNotFound_returnsError() {
        // Arrange
        String unknownSession = "unknown-session";
        when(jcoSessionManager.getSession(unknownSession))
                .thenThrow(new IllegalArgumentException("Session not found: " + unknownSession));

        CallToolRequest request = requestBuilder()
                .arg("session_id", unknownSession)
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("description", "Test class")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void createClass_creationFails_returnsError() throws Exception {
        // Arrange
        setupExecuteInContext();
        when(adtClient.createClass(any(), anyString(), anyString(), anyString(),
                isNull(), anyString(), anyBoolean(), isNull(), isNull(), any(), any(), any()))
                .thenThrow(new RuntimeException("Class already exists"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_EXISTING")
                .arg("description", "Test class")
                .arg("package_name", "$TMP")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Class already exists");
    }

    @Test
    void createClass_defaultsToFinalTrue() throws Exception {
        // Arrange
        CreateResponse createResponse = CreateResponse.builder()
                .objectUri("/sap/bc/adt/oo/classes/ZCL_FINAL")
                .objectName("ZCL_FINAL")
                .objectType("class")
                .httpStatus(201)
                .build();

        setupExecuteInContext();
        when(adtClient.createClass(any(), anyString(), anyString(), anyString(),
                isNull(), anyString(), eq(true), isNull(), isNull(), any(), any(), any()))
                .thenReturn(createResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_FINAL")
                .arg("description", "Test class")
                .arg("package_name", "$TMP")
                .build();

        // Act
        handler.handle(exchange, request);

        // Assert - verify final=true (default) was passed
        verify(adtClient).createClass(any(), anyString(), anyString(), anyString(),
                isNull(), anyString(), eq(true), isNull(), isNull(), any(), any(), any());
    }

    @Test
    void createClass_defaultsToPublicVisibility() throws Exception {
        // Arrange
        CreateResponse createResponse = CreateResponse.builder()
                .objectUri("/sap/bc/adt/oo/classes/ZCL_PUBLIC")
                .objectName("ZCL_PUBLIC")
                .objectType("class")
                .httpStatus(201)
                .build();

        setupExecuteInContext();
        when(adtClient.createClass(any(), anyString(), anyString(), anyString(),
                isNull(), eq("PUBLIC"), anyBoolean(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(createResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_PUBLIC")
                .arg("description", "Test class")
                .arg("package_name", "$TMP")
                .build();

        // Act
        handler.handle(exchange, request);

        // Assert - verify visibility=PUBLIC (default) was passed
        verify(adtClient).createClass(any(), anyString(), anyString(), anyString(),
                isNull(), eq("PUBLIC"), anyBoolean(), isNull(), isNull(), any(), any(), any());
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
