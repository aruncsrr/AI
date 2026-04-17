package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.model.LockResponse;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SaveClassIncludeHandler.
 * Tests ABAP class include saving with atomic lock/save/unlock operations.
 */
class SaveClassIncludeHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveClassIncludeHandler handler;

    private JcoSession mockSession;

    private static final String TEST_DEFINITIONS_SOURCE = """
            CLASS zcl_test DEFINITION PUBLIC CREATE PUBLIC.
              PUBLIC SECTION.
                METHODS get_value RETURNING VALUE(rv_value) TYPE i.
              PRIVATE SECTION.
                DATA mv_value TYPE i.
            ENDCLASS.
            """;

    private static final String TEST_IMPLEMENTATIONS_SOURCE = """
            CLASS zcl_test IMPLEMENTATION.
              METHOD get_value.
                rv_value = mv_value.
              ENDMETHOD.
            ENDCLASS.
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Success Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void saveClassInclude_definitions_success() throws Exception {
        // Arrange
        setupSuccessfulSave();

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "definitions")
                .arg("source_code", TEST_DEFINITIONS_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
        assertSystemHeader(result);
    }

    @Test
    void saveClassInclude_implementations_success() throws Exception {
        // Arrange
        setupSuccessfulSave();

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "implementations")
                .arg("source_code", TEST_IMPLEMENTATIONS_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
    }

    @Test
    void saveClassInclude_testClasses_success() throws Exception {
        // Arrange
        setupSuccessfulSave();

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "testClasses")
                .arg("source_code", "CLASS ltcl_test DEFINITION FOR TESTING.")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
    }

    @Test
    void saveClassInclude_macros_success() throws Exception {
        // Arrange
        setupSuccessfulSave();

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "macros")
                .arg("source_code", "DEFINE my_macro. END-OF-DEFINITION.")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
    }

    @Test
    void saveClassInclude_withTransportNumber() throws Exception {
        // Arrange
        setupSuccessfulSave();

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "definitions")
                .arg("source_code", TEST_DEFINITIONS_SOURCE)
                .arg("transport_number", "NPLK900001")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void saveClassInclude_namespacedClass() throws Exception {
        // Arrange
        setupSuccessfulSave();

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "/SCMTMS/CL_TOR")
                .arg("include_type", "definitions")
                .arg("source_code", TEST_DEFINITIONS_SOURCE)
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
    void saveClassInclude_missingSessionId_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "definitions")
                .arg("source_code", TEST_DEFINITIONS_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveClassInclude_missingClassName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("include_type", "definitions")
                .arg("source_code", TEST_DEFINITIONS_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "class_name is required");
    }

    @Test
    void saveClassInclude_missingIncludeType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("source_code", TEST_DEFINITIONS_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "include_type is required");
    }

    @Test
    void saveClassInclude_missingSourceCode_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "definitions")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }

    @Test
    void saveClassInclude_invalidIncludeType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "invalid_type")
                .arg("source_code", TEST_DEFINITIONS_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "include_type must be one of");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void saveClassInclude_lockFailed_returnsError() throws Exception {
        // Arrange
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Lock failed: Object already locked by another user"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "definitions")
                .arg("source_code", TEST_DEFINITIONS_SOURCE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Lock failed");
    }

    @Test
    void saveClassInclude_syntaxError_returnsError() throws Exception {
        // Arrange
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Syntax error at line 5"));

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "definitions")
                .arg("source_code", "INVALID ABAP CODE")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Syntax error");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private void setupSuccessfulSave() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn("<response>saved</response>"); // Save operations return raw XML response
    }
}
