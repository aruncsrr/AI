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
 * Unit tests for GetStructureHandler.
 * Tests ABAP structure definition retrieval with field components.
 *
 * Note: Handler writes XML to file and returns metadata summary.
 */
class GetStructureHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetStructureHandler handler;

    private JcoSession mockTempSession;

    private static final String STRUCTURE_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <blueSource:blueSource xmlns:blueSource="http://www.sap.com/wbobj/blue"
                                   xmlns:adtcore="http://www.sap.com/adt/core"
                                   adtcore:name="BAPI0002_1"
                                   adtcore:description="Address (Business Address Services)"
                                   adtcore:type="TABL/DS"
                                   adtcore:responsible="SAP"
                                   adtcore:masterLanguage="EN"
                                   adtcore:abapLanguageVersion="standard"
                                   adtcore:changedAt="2024-01-15T10:30:00Z"
                                   adtcore:changedBy="SAP"
                                   adtcore:createdBy="SAP">
                <adtcore:packageRef adtcore:name="SBAPIADDR" adtcore:description="Business Address Services"/>
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
    void getStructure_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(STRUCTURE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "BAPI0002_1")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Metadata summary is returned (XML written to file)
        assertContains(result, "BAPI0002_1");
        assertContains(result, "File:");
        assertContains(result, "bytes");
        assertSystemHeader(result);

        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getStructure_withInactiveVersion() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(STRUCTURE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "BAPI0002_1")
                .arg("version", "inactive")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Version: inactive");
    }

    @Test
    void getStructure_withoutFields() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(STRUCTURE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "BAPI0002_1")
                .arg("with_fields", false)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "With Fields: false");
    }

    @Test
    void getStructure_returnsFilePath() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(STRUCTURE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "BAPI0002_1")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata (cross-platform temp directory)
        assertContains(result, "File:");
        assertContains(result, System.getProperty("java.io.tmpdir"));
    }

    @Test
    void getStructure_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(STRUCTURE_RESPONSE);

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
    void getStructure_namespacedStructure() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(STRUCTURE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "/SCMTMS/S_TOR_ROOT")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "/SCMTMS/S_TOR_ROOT");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getStructure_returnsByteSize() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(STRUCTURE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("structure_name", "BAPI0002_1")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Size is returned in metadata
        assertContains(result, "Size:");
        assertContains(result, "bytes");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getStructure_missingName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "structure_name is required");
    }

    @Test
    void getStructure_emptyName_returnsError() {
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
    void getStructure_notFound_returnsError() throws Exception {
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
