package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.testdata.SampleXmlResponses;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GetObjectStatusHandler.
 * Tests object metadata retrieval and activation status detection.
 */
class GetObjectStatusHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetObjectStatusHandler handler;

    private JcoSession mockTempSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Success Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getObjectStatus_activeVersion_returnsRawXml() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.CLASS_METADATA_ACTIVE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Raw XML contains the version attribute
        assertContains(result, "ZCL_TEST_CLASS");
        assertContains(result, "version=\"active\"");
        assertSystemHeader(result);
    }

    @Test
    void getObjectStatus_activeWithInactiveVersion_returnsRawXml() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.CLASS_METADATA_WITH_INACTIVE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "version=\"activeWithInactiveVersion\"");
    }

    @Test
    void getObjectStatus_inactiveVersion_returnsRawXml() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.CLASS_METADATA_INACTIVE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_NEW_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "version=\"inactive\"");
    }

    @Test
    void getObjectStatus_partlyActive_returnsRawXml() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.PROGRAM_METADATA_PARTLY_ACTIVE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZTEST_PROGRAM")
                .arg("object_type", "program")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "version=\"partlyActive\"");
    }

    @Test
    void getObjectStatus_interface_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.INTERFACE_METADATA_ACTIVE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZIF_TEST_INTERFACE")
                .arg("object_type", "interface")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "ZIF_TEST_INTERFACE");
        assertContains(result, "intf:abapInterface");
        assertContains(result, "active");
    }

    @Test
    void getObjectStatus_rawXmlContainsMetadata() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleXmlResponses.CLASS_METADATA_ACTIVE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<?xml");
        assertContains(result, "class:abapClass");
    }

    @Test
    void getObjectStatus_withProvidedSession_noTempSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(SampleXmlResponses.CLASS_METADATA_ACTIVE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager, never()).destroySession(anyString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getObjectStatus_missingObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void getObjectStatus_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void getObjectStatus_emptyObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getObjectStatus_notFound_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Object ZCL_NONEXISTENT not found"));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_NONEXISTENT")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "not found");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Static Helper Method Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void hasInactiveChanges_activeVersion_returnsFalse() {
        assertFalse(GetObjectStatusHandler.hasInactiveChanges("active"));
    }

    @Test
    void hasInactiveChanges_activeWithInactiveVersion_returnsTrue() {
        assertTrue(GetObjectStatusHandler.hasInactiveChanges("activeWithInactiveVersion"));
    }

    @Test
    void hasInactiveChanges_inactive_returnsTrue() {
        assertTrue(GetObjectStatusHandler.hasInactiveChanges("inactive"));
    }

    @Test
    void hasInactiveChanges_partlyActive_returnsTrue() {
        assertTrue(GetObjectStatusHandler.hasInactiveChanges("partlyActive"));
    }

    @Test
    void hasInactiveChanges_null_returnsFalse() {
        assertFalse(GetObjectStatusHandler.hasInactiveChanges(null));
    }

    @Test
    void hasInactiveChanges_unknownValue_returnsFalse() {
        assertFalse(GetObjectStatusHandler.hasInactiveChanges("unknown"));
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
