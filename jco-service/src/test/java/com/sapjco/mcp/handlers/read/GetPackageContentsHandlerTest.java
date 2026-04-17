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
 * Unit tests for GetPackageContentsHandler.
 * Tests package contents retrieval and filtering.
 */
class GetPackageContentsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetPackageContentsHandler handler;

    private JcoSession mockTempSession;

    private static final String PACKAGE_CONTENTS_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <SEU_ADT_REPOSITORY_OBJ_NODE>
                        <OBJECT_TYPE>CLAS/OC</OBJECT_TYPE>
                        <OBJECT_NAME>ZCL_TEST_CLASS</OBJECT_NAME>
                        <DESCRIPTION>Test Class</DESCRIPTION>
                        <OBJECT_URI>/sap/bc/adt/oo/classes/zcl_test_class</OBJECT_URI>
                    </SEU_ADT_REPOSITORY_OBJ_NODE>
                    <SEU_ADT_REPOSITORY_OBJ_NODE>
                        <OBJECT_TYPE>INTF/OI</OBJECT_TYPE>
                        <OBJECT_NAME>ZIF_TEST_INTERFACE</OBJECT_NAME>
                        <DESCRIPTION>Test Interface</DESCRIPTION>
                        <OBJECT_URI>/sap/bc/adt/oo/interfaces/zif_test_interface</OBJECT_URI>
                    </SEU_ADT_REPOSITORY_OBJ_NODE>
                    <SEU_ADT_REPOSITORY_OBJ_NODE>
                        <OBJECT_TYPE>PROG/P</OBJECT_TYPE>
                        <OBJECT_NAME>ZTEST_PROGRAM</OBJECT_NAME>
                        <DESCRIPTION>Test Program</DESCRIPTION>
                        <OBJECT_URI>/sap/bc/adt/programs/programs/ztest_program</OBJECT_URI>
                    </SEU_ADT_REPOSITORY_OBJ_NODE>
                </asx:values>
            </asx:abap>
            """;

    private static final String EMPTY_PACKAGE_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                </asx:values>
            </asx:abap>
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
    void getPackageContents_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(PACKAGE_CONTENTS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("package_name", "ZTEST_PACKAGE")
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
    void getPackageContents_withMaxResults() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(PACKAGE_CONTENTS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("package_name", "ZTEST_PACKAGE")
                .arg("max_results", 100)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getPackageContents_withObjectTypeFilter() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(PACKAGE_CONTENTS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("package_name", "ZTEST_PACKAGE")
                .arg("object_type", "CLAS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getPackageContents_withNamePattern() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(PACKAGE_CONTENTS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("package_name", "ZTEST_PACKAGE")
                .arg("name_pattern", "ZCL_*")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getPackageContents_rawResponse() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(PACKAGE_CONTENTS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("package_name", "ZTEST_PACKAGE")
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
    void getPackageContents_lowercasePackageName() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(PACKAGE_CONTENTS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("package_name", "ztest_package")  // lowercase
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Package name should be uppercased internally
    }

    @Test
    void getPackageContents_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(PACKAGE_CONTENTS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("package_name", "ZTEST_PACKAGE")
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
    // Empty Package
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getPackageContents_emptyPackage() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContext(EMPTY_PACKAGE_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("package_name", "ZEMPTY_PACKAGE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "0");  // 0 objects
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getPackageContents_missingPackageName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder().build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "package_name is required");
    }

    @Test
    void getPackageContents_emptyPackageName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("package_name", "")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "package_name is required");
    }

    @Test
    void getPackageContents_packageNotFound_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Package ZNONEXISTENT not found"));

        CallToolRequest request = requestBuilder()
                .arg("package_name", "ZNONEXISTENT")
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
