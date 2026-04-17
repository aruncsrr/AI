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
 * Unit tests for GetTableHandler.
 * Tests ABAP database table definition retrieval.
 */
class GetTableHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetTableHandler handler;

    private JcoSession mockTempSession;

    private static final String TABLE_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <blueSource:blueSource xmlns:blueSource="http://www.sap.com/wbobj/blue"
                                   xmlns:adtcore="http://www.sap.com/adt/core"
                                   adtcore:name="SFLIGHT"
                                   adtcore:description="Flight booking"
                                   adtcore:type="TABL/DT"
                                   adtcore:changedAt="2024-01-15"
                                   adtcore:changedBy="SAP">
                <adtcore:packageRef adtcore:name="$TMP"/>
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
    void getTable_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(TABLE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("table_name", "SFLIGHT")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "SFLIGHT");
        assertSystemHeader(result);

        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getTable_withInactiveVersion() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(TABLE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("table_name", "SFLIGHT")
                .arg("version", "inactive")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getTable_returnsFilePath() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(TABLE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("table_name", "SFLIGHT")
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
    void getTable_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(TABLE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("table_name", "SFLIGHT")
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
    void getTable_missingTableName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "table_name is required");
    }

    @Test
    void getTable_emptyTableName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("table_name", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "table_name is required");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getTable_tableNotFound_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Table ZNONEXISTENT not found"));

        CallToolRequest request = requestBuilder()
                .arg("table_name", "ZNONEXISTENT")
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
