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
 * Unit tests for GetStructureFieldsHandler.
 * Tests ABAP structure component definitions retrieval via element info API.
 */
class GetStructureFieldsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetStructureFieldsHandler handler;

    private JcoSession mockTempSession;

    private static final String STRUCTURE_FIELDS_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <elementInfo xmlns:abapsource="http://www.sap.com/adt/abapsource"
                         name="BAPI0002_1" type="TABL/DS" uri="/sap/bc/adt/ddic/structures/bapi0002_1">
                <elementInfo name="STREET" type="DTEL" uri="/sap/bc/adt/ddic/dataelements/bapiad1vl">
                    <property name="ddicDataElement" value="BAPIAD1VL"/>
                    <property name="ddicDataType" value="CHAR"/>
                    <property name="ddicLength" value="60"/>
                    <documentation>Street and House Number</documentation>
                </elementInfo>
                <elementInfo name="CITY" type="DTEL" uri="/sap/bc/adt/ddic/dataelements/bapiad1vl">
                    <property name="ddicDataElement" value="BAPIAD1VL"/>
                    <property name="ddicDataType" value="CHAR"/>
                    <property name="ddicLength" value="40"/>
                    <documentation>City</documentation>
                </elementInfo>
                <elementInfo name="POSTL_COD1" type="DTEL" uri="/sap/bc/adt/ddic/dataelements/bapiad1vl">
                    <property name="ddicDataElement" value="BAPIAD1PC"/>
                    <property name="ddicDataType" value="CHAR"/>
                    <property name="ddicLength" value="10"/>
                    <documentation>Postal Code</documentation>
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
    void getStructureFields_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(STRUCTURE_FIELDS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "BAPI0002_1")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "BAPI0002_1");
        assertSystemHeader(result);

        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getStructureFields_rawResponse() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(STRUCTURE_FIELDS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "BAPI0002_1")
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
    void getStructureFields_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STRUCTURE_FIELDS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "BAPI0002_1")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
    }

    @Test
    void getStructureFields_namespacedStructure() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(STRUCTURE_FIELDS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "/SCMTMS/S_TOR_ROOT")
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
    void getStructureFields_missingName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "structure_name is required");
    }

    @Test
    void getStructureFields_emptyName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("structure_name", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "structure_name is required");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getStructureFields_notFound_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Structure ZNONEXISTENT not found"));

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "ZNONEXISTENT")
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
