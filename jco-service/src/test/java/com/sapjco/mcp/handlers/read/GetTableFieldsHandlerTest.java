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
 * Unit tests for GetTableFieldsHandler.
 * Tests ABAP table field definitions retrieval via element info API.
 */
class GetTableFieldsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetTableFieldsHandler handler;

    private JcoSession mockTempSession;

    private static final String TABLE_FIELDS_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <elementInfo xmlns:abapsource="http://www.sap.com/adt/abapsource"
                         name="SFLIGHT" type="TABL/DT" uri="/sap/bc/adt/ddic/tables/sflight">
                <elementInfo name="MANDT" type="DTEL" uri="/sap/bc/adt/ddic/dataelements/mandt">
                    <property name="ddicIsKey" value="true"/>
                    <property name="ddicDataElement" value="MANDT"/>
                    <property name="ddicDataType" value="CLNT"/>
                    <property name="ddicLength" value="3"/>
                    <documentation>Client</documentation>
                </elementInfo>
                <elementInfo name="CARRID" type="DTEL" uri="/sap/bc/adt/ddic/dataelements/carrid">
                    <property name="ddicIsKey" value="true"/>
                    <property name="ddicDataElement" value="CARRID"/>
                    <property name="ddicDataType" value="CHAR"/>
                    <property name="ddicLength" value="3"/>
                    <documentation>Airline Code</documentation>
                </elementInfo>
                <elementInfo name="PRICE" type="DTEL" uri="/sap/bc/adt/ddic/dataelements/price">
                    <property name="ddicIsKey" value="false"/>
                    <property name="ddicDataElement" value="S_PRICE"/>
                    <property name="ddicDataType" value="CURR"/>
                    <property name="ddicLength" value="15"/>
                    <property name="ddicDecimals" value="2"/>
                    <documentation>Flight Price</documentation>
                </elementInfo>
            </elementInfo>
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
    void getTableFields_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(TABLE_FIELDS_RESPONSE);

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
    void getTableFields_returnsFilePath() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(TABLE_FIELDS_RESPONSE);

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
    void getTableFields_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(TABLE_FIELDS_RESPONSE);

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

    @Test
    void getTableFields_namespacedTable() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(TABLE_FIELDS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("table_name", "/SCMTMS/D_TORROT")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getTableFields_missingName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "table_name is required");
    }

    @Test
    void getTableFields_emptyName_returnsError() {
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
    void getTableFields_notFound_returnsError() throws Exception {
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
