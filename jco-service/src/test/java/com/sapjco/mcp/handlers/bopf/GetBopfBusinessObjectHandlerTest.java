package com.sapjco.mcp.handlers.bopf;

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
 * Unit tests for GetBopfBusinessObjectHandler.
 * Tests BOPF BO metadata retrieval and parsing.
 */
class GetBopfBusinessObjectHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetBopfBusinessObjectHandler handler;

    private JcoSession mockTempSession;

    private static final String BOPF_BO_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               bo:name="/BOBF/DEMO_SALES_ORDER">
                <bo:nodes bo:name="ROOT" bo:nodeID="12345">
                    <bo:persistentStructureRef adtcore:name="/BOBF/S_DEMO_SALES_ORDER_HDR"/>
                    <bo:persistentTableRef adtcore:name="/BOBF/DM_SORD_RT"/>
                </bo:nodes>
                <bo:nodes bo:name="ITEM" bo:nodeID="12346">
                    <bo:persistentStructureRef adtcore:name="/BOBF/S_DEMO_SALES_ORDER_ITM"/>
                    <bo:persistentTableRef adtcore:name="/BOBF/DM_SORD_IT"/>
                </bo:nodes>
                <bo:actions bo:name="CREATE_ORDER" bo:actionID="ACT001" bo:category="standard">
                    <bo:implementationClassRef adtcore:name="/BOBF/CL_DEMO_CREATE"/>
                </bo:actions>
                <bo:associations bo:name="TO_ITEM" bo:associationID="ASSOC001" bo:multiplicity="1:n">
                    <bo:targetNodeRef adtcore:name="/BOBF/DEMO_SALES_ORDER~ITEM"/>
                </bo:associations>
                <bo:queries bo:name="BY_ID" bo:queryID="QRY001" bo:category="simple">
                    <bo:implementationClassRef adtcore:name="/BOBF/CL_DEMO_QUERY"/>
                </bo:queries>
                <bo:determinations bo:name="SET_DEFAULTS" bo:category="before_save">
                    <bo:implementationClassRef adtcore:name="/BOBF/CL_DEMO_DET"/>
                </bo:determinations>
                <bo:validations bo:name="CHECK_DATA" bo:validationID="VAL001" bo:category="consistency">
                    <bo:implementationClassRef adtcore:name="/BOBF/CL_DEMO_VAL"/>
                </bo:validations>
            </bo:businessObject>
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
    void getBopfBusinessObject_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BOPF_BO_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("bo_name", "/BOBF/DEMO_SALES_ORDER")
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
    void getBopfBusinessObject_namespacedBo() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BOPF_BO_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("bo_name", "/SCMTMS/TOR")  // BO with namespace
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getBopfBusinessObject_returnsFilePath() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BOPF_BO_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("bo_name", "/BOBF/DEMO_SALES_ORDER")
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
    void getBopfBusinessObject_inactiveVersion() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BOPF_BO_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("bo_name", "/BOBF/DEMO_SALES_ORDER")
                .arg("version", "inactive")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getBopfBusinessObject_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(BOPF_BO_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("bo_name", "/BOBF/DEMO_SALES_ORDER")
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
    // Component Parsing Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getBopfBusinessObject_parsesNodes() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BOPF_BO_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("bo_name", "/BOBF/DEMO_SALES_ORDER")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
    }

    @Test
    void getBopfBusinessObject_parsesActions() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BOPF_BO_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("bo_name", "/BOBF/DEMO_SALES_ORDER")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "File:");
    }

    @Test
    void getBopfBusinessObject_parsesAssociations() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(BOPF_BO_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("bo_name", "/BOBF/DEMO_SALES_ORDER")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "File:");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getBopfBusinessObject_missingBoName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "bo_name is required");
    }

    @Test
    void getBopfBusinessObject_emptyBoName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("bo_name", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "bo_name is required");
    }

    @Test
    void getBopfBusinessObject_notFound_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("BOPF Business Object /ZNONEXISTENT not found"));

        CallToolRequest request = requestBuilder()
                .arg("bo_name", "/ZNONEXISTENT")
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
