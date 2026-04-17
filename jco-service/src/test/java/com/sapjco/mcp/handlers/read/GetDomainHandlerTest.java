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
 * Unit tests for GetDomainHandler.
 * Tests ABAP domain definition retrieval.
 */
class GetDomainHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetDomainHandler handler;

    private JcoSession mockTempSession;

    private static final String DOMAIN_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <blueSource:blueSource xmlns:blueSource="http://www.sap.com/wbobj/blue"
                                   xmlns:adtcore="http://www.sap.com/adt/core"
                                   adtcore:name="S_CONN_ID"
                                   adtcore:description="Connection ID"
                                   adtcore:type="DOMA/DD">
            </blueSource:blueSource>
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
    void getDomain_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(DOMAIN_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("domain_name", "S_CONN_ID")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "S_CONN_ID");
        assertSystemHeader(result);

        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getDomain_withInactiveVersion() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(DOMAIN_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("domain_name", "S_CONN_ID")
                .arg("version", "inactive")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getDomain_returnsFilePath() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(DOMAIN_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("domain_name", "S_CONN_ID")
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
    void getDomain_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(DOMAIN_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("domain_name", "S_CONN_ID")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getDomain_missingName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "domain_name is required");
    }

    @Test
    void getDomain_emptyName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("domain_name", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "domain_name is required");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getDomain_notFound_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Domain ZNONEXISTENT not found"));

        CallToolRequest request = requestBuilder()
                .arg("domain_name", "ZNONEXISTENT")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "not found");
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
