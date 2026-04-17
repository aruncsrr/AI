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
 * Unit tests for GetWhereUsedHandler.
 * Tests where-used query functionality and result parsing.
 */
class GetWhereUsedHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetWhereUsedHandler handler;

    private JcoSession mockTempSession;

    private static final String WHERE_USED_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <usageReferences xmlns="http://www.sap.com/adt/ris/whereused"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <referencedObject uri="/sap/bc/adt/oo/classes/zcl_consumer/source/main">
                    <objectIdentifier>CLAS/OC ZCL_CONSUMER</objectIdentifier>
                    <adtObject adtcore:name="ZCL_CONSUMER" adtcore:type="CLAS/OC">
                        <packageRef adtcore:name="ZTEST_PACKAGE"/>
                    </adtObject>
                </referencedObject>
                <referencedObject uri="/sap/bc/adt/programs/programs/ztest_program/source/main">
                    <objectIdentifier>PROG/P ZTEST_PROGRAM</objectIdentifier>
                    <adtObject adtcore:name="ZTEST_PROGRAM" adtcore:type="PROG/P">
                        <packageRef adtcore:name="ZTEST_PACKAGE"/>
                    </adtObject>
                </referencedObject>
            </usageReferences>
            """;

    private static final String EMPTY_WHERE_USED_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <usageReferences xmlns="http://www.sap.com/adt/ris/whereused">
            </usageReferences>
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
    void getWhereUsed_success_class() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(WHERE_USED_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
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
    void getWhereUsed_success_interface() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(WHERE_USED_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZIF_MY_INTERFACE")
                .arg("object_type", "interface")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getWhereUsed_success_dataElement() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(WHERE_USED_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZDATA_ELEMENT")
                .arg("object_type", "data_element")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getWhereUsed_withMaxResults() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(WHERE_USED_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
                .arg("object_type", "class")
                .arg("max_results", 50)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getWhereUsed_positionBased() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(WHERE_USED_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
                .arg("object_type", "class")
                .arg("line", 42)
                .arg("column", 10)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getWhereUsed_rawResponse() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(WHERE_USED_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
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
    void getWhereUsed_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(WHERE_USED_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
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
    void getWhereUsed_noUsages() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(EMPTY_WHERE_USED_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_UNUSED_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "0");  // 0 usages
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getWhereUsed_missingObjectName_returnsError() {
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
    void getWhereUsed_emptyObjectName_returnsError() {
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
    void getWhereUsed_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_MY_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void getWhereUsed_objectNotFound_returnsError() throws Exception {
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
