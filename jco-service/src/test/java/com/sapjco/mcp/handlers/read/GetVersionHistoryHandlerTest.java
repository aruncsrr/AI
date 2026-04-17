package com.sapjco.mcp.handlers.read;

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
 * Unit tests for GetVersionHistoryHandler.
 * Tests version history retrieval for ABAP objects.
 */
class GetVersionHistoryHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetVersionHistoryHandler handler;

    private JcoSession mockTempSession;

    private static final String VERSION_HISTORY_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Versions of ZCL_TEST_CLASS</title>
                <entry>
                    <id>00001</id>
                    <title>Initial version</title>
                    <updated>2024-01-15T10:30:45Z</updated>
                    <author><name>D052860</name></author>
                    <versionNumber>1</versionNumber>
                </entry>
                <entry>
                    <id>00002</id>
                    <title>Bug fix</title>
                    <updated>2024-01-16T14:20:00Z</updated>
                    <author><name>D052860</name></author>
                    <versionNumber>2</versionNumber>
                </entry>
                <entry>
                    <id>00000</id>
                    <title>Current active version</title>
                    <updated>2024-01-17T09:00:00Z</updated>
                    <author><name>D052860</name></author>
                    <versionNumber>3</versionNumber>
                </entry>
            </feed>
            """;

    private static final String EMPTY_VERSION_HISTORY_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Versions</title>
            </feed>
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
    void getVersionHistory_class_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(VERSION_HISTORY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata (XML written to file)
        assertContains(result, "File:");
        assertContains(result, "bytes");
        assertSystemHeader(result);

        // Verify temp session lifecycle
        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getVersionHistory_interface_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(VERSION_HISTORY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZIF_TEST_INTERFACE")
                .arg("object_type", "interface")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "File:");
    }

    @Test
    void getVersionHistory_program_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(VERSION_HISTORY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZTEST_PROGRAM")
                .arg("object_type", "program")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getVersionHistory_classWithIncludeType() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(VERSION_HISTORY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("include_type", "testClasses")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void getVersionHistory_rawXmlResponse() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(VERSION_HISTORY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
        assertContains(result, "bytes");
    }

    @Test
    void getVersionHistory_withDeliveryInfo() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(VERSION_HISTORY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("add_delivery_info", true)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getVersionHistory_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(VERSION_HISTORY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);

        // Should NOT create temp session when session_id is provided
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Empty Results
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getVersionHistory_noVersions() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(EMPTY_VERSION_HISTORY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_NEW_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Empty response still returns file path
        assertContains(result, "File:");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getVersionHistory_missingObjectName_returnsError() {
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
    void getVersionHistory_emptyObjectName_returnsError() {
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

    @Test
    void getVersionHistory_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void getVersionHistory_emptyObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getVersionHistory_objectNotFound_returnsError() throws Exception {
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
