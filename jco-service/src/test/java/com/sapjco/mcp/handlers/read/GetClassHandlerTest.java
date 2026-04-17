package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.testdata.SampleAbapSource;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GetClassHandler.
 * Tests read operations with temp session pattern.
 */
class GetClassHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetClassHandler handler;

    private JcoSession mockTempSession;

    private static final Path TEST_FILE_PATH = Path.of("/tmp/sap-mcp/dev_100/class/ZCL_TEST_CLASS.abap");

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
        setupFileStorageService();
    }

    /**
     * Setup FileStorageService mock with specific test values.
     */
    private void setupFileStorageService() throws Exception {
        lenient().when(fileStorageService.writeSource(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(TEST_FILE_PATH);
        lenient().when(fileStorageService.getLineCount(any(Path.class))).thenReturn(10L);
        lenient().when(fileStorageService.getByteSize(any(Path.class))).thenReturn(500L);
    }

    @Test
    void getClass_success_withTempSession() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleAbapSource.CLASS_DEFINITION);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "ZCL_TEST_CLASS");
        assertContains(result, "active");  // Default version
        assertSystemHeader(result);

        // Verify temp session lifecycle
        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getClass_withProvidedSession_noTempSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        setupExecuteInContextForSession(TEST_SESSION_ID, SampleAbapSource.CLASS_DEFINITION);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);

        // Verify NO temp session was created/destroyed
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager, never()).destroySession(anyString());
    }

    @Test
    void getClass_inactiveVersion() throws Exception {
        // Arrange
        String inactiveSource = "* Inactive version\n" + SampleAbapSource.CLASS_DEFINITION;
        setupTempSession();
        setupExecuteInContext(inactiveSource);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("version", "inactive")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "inactive");
    }

    @Test
    void getClass_namespacedClass() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleAbapSource.NAMESPACED_CLASS);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "/NAMESPACE/CL_TEST")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "/NAMESPACE/CL_TEST");
    }

    @Test
    void getClass_specificSystem() throws Exception {
        // Arrange
        String prodSystem = "prod";
        mockGetSystem(prodSystem);

        setupTempSession();
        setupExecuteInContext(SampleAbapSource.CLASS_DEFINITION);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST_CLASS")
                .arg("system_id", prodSystem)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verify(systemConfigLoader).getSystem(prodSystem);
    }

    @Test
    void getClass_missingClassName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "class_name is required");
    }

    @Test
    void getClass_emptyClassName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("class_name", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "class_name is required");
    }

    @Test
    void getClass_adtError_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Class not found"));

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_NONEXISTENT")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Class not found");

        // Verify temp session was still destroyed (cleanup in finally)
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getClass_tempSessionCleanupOnException() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("ADT error"));

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert - even on error, temp session should be destroyed
        assertError(result);
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getClass_returnsFileBasedOutput() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(SampleAbapSource.CLASS_DEFINITION);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Class: ZCL_TEST_CLASS");
        assertContains(result, "Lines:");  // File metadata
        assertContains(result, "Size:");   // File metadata
        assertContains(result, "File:");   // File path
        assertContains(result, "sap-mcp");  // File path contains sap-mcp (cross-platform)
    }

    /**
     * Helper to setup temp session pattern with lenient stubbing.
     * Uses lenient() to avoid UnnecessaryStubbingException when tests don't reach this code.
     */
    private void setupTempSession() throws Exception {
        lenient().when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_TEMP_SESSION_ID);
        lenient().when(jcoSessionManager.getSession(TEST_TEMP_SESSION_ID)).thenReturn(mockTempSession);
    }

    /**
     * Helper to setup executeInContext mock that returns source code.
     * Uses lenient() to avoid UnnecessaryStubbingException when tests don't reach this code.
     */
    private void setupExecuteInContext(String returnValue) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenAnswer(invocation -> returnValue);
    }

    /**
     * Helper to setup executeInContext for specific session.
     * Uses lenient() to avoid UnnecessaryStubbingException when tests don't reach this code.
     */
    private void setupExecuteInContextForSession(String sessionId, String returnValue) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(sessionId), any()))
                .thenAnswer(invocation -> returnValue);
    }
}
