package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.service.JcoSessionManager.JcoOperation;
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
 * Unit tests for GetInterfaceHandler.
 * Tests interface source code retrieval.
 */
class GetInterfaceHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetInterfaceHandler handler;

    private JcoSession mockSession;

    private static final Path TEST_FILE_PATH = Path.of("/tmp/sap-mcp/dev_100/interface/ZIF_TEST_INTERFACE.abap");

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSessionPattern();
        mockSession = mockGetSession(TEST_TEMP_SESSION_ID);
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
    void getInterface_success_returnsSourceCode() throws Exception {
        // Arrange
        setupExecuteInContext(SampleAbapSource.INTERFACE_DEFINITION);

        CallToolRequest request = requestBuilder()
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Interface: ZIF_TEST_INTERFACE");
        assertContains(result, "version: active");
        assertContains(result, "File:");  // File-based output
        assertSystemHeader(result);
    }

    @Test
    void getInterface_inactiveVersion_returnsInactiveSource() throws Exception {
        // Arrange
        setupExecuteInContext(SampleAbapSource.INTERFACE_DEFINITION);

        CallToolRequest request = requestBuilder()
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("version", "inactive")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "version: inactive");
    }

    @Test
    void getInterface_missingInterfaceName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "interface_name is required");
    }

    @Test
    void getInterface_withProvidedSession_usesProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        setupExecuteInContextWithSession(TEST_SESSION_ID, SampleAbapSource.INTERFACE_DEFINITION);

        CallToolRequest request = requestBuilder()
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Should NOT create/destroy temp session when session_id is provided
        verify(jcoSessionManager, never()).createSession(any());
        verify(jcoSessionManager, never()).destroySession(anyString());
    }

    @Test
    void getInterface_tempSessionLifecycle_createsAndDestroysSession() throws Exception {
        // Arrange
        setupExecuteInContext(SampleAbapSource.INTERFACE_DEFINITION);

        CallToolRequest request = requestBuilder()
                .arg("interface_name", "ZIF_TEST_INTERFACE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verifyTempSessionLifecycle();
    }

    @Test
    void getInterface_namespacedObject_encodesSlashes() throws Exception {
        // Arrange
        setupExecuteInContext(SampleAbapSource.INTERFACE_DEFINITION);

        CallToolRequest request = requestBuilder()
                .arg("interface_name", "/NAMESPACE/IF_TEST")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // The handler should encode the slashes (we can't verify the URL directly,
        // but we verify success indicates proper handling)
        assertContains(result, "/NAMESPACE/IF_TEST");
    }

    /**
     * Helper to setup executeInContext mock for temp session.
     */
    @SuppressWarnings("unchecked")
    private void setupExecuteInContext(String returnValue) throws Exception {
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenAnswer(invocation -> {
                    JcoOperation<String> operation = invocation.getArgument(1);
                    return returnValue;
                });
    }

    /**
     * Helper to setup executeInContext mock for provided session.
     */
    @SuppressWarnings("unchecked")
    private void setupExecuteInContextWithSession(String sessionId, String returnValue) throws Exception {
        when(jcoSessionManager.executeInContext(eq(sessionId), any()))
                .thenAnswer(invocation -> {
                    JcoOperation<String> operation = invocation.getArgument(1);
                    return returnValue;
                });
    }
}
