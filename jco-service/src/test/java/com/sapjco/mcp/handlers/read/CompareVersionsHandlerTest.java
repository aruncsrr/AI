package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.invocation.InvocationOnMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CompareVersionsHandler.
 * Tests comparing two versions of an ABAP object and returning unified diff.
 */
class CompareVersionsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private CompareVersionsHandler handler;

    private JcoSession mockTempSession;

    private static final String VERSION_HISTORY_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <atom:feed xmlns:atom="http://www.w3.org/2005/Atom">
                <atom:entry>
                    <atom:id>00000</atom:id>
                    <atom:content type="text/plain" src="/sap/bc/adt/oo/classes/ZCL_TEST/includes/main/versions/00000/source"/>
                </atom:entry>
                <atom:entry>
                    <atom:id>00001</atom:id>
                    <atom:content type="text/plain" src="/sap/bc/adt/oo/classes/ZCL_TEST/includes/main/versions/00001/source"/>
                </atom:entry>
                <atom:entry>
                    <atom:id>00002</atom:id>
                    <atom:content type="text/plain" src="/sap/bc/adt/oo/classes/ZCL_TEST/includes/main/versions/00002/source"/>
                </atom:entry>
            </atom:feed>
            """;

    private static final String SOURCE_VERSION_A = """
            CLASS zcl_test DEFINITION PUBLIC.
              PUBLIC SECTION.
                METHODS get_value RETURNING VALUE(rv_value) TYPE i.
            ENDCLASS.

            CLASS zcl_test IMPLEMENTATION.
              METHOD get_value.
                rv_value = 1.
              ENDMETHOD.
            ENDCLASS.
            """;

    private static final String SOURCE_VERSION_B = """
            CLASS zcl_test DEFINITION PUBLIC.
              PUBLIC SECTION.
                METHODS get_value RETURNING VALUE(rv_value) TYPE i.
                METHODS set_value IMPORTING iv_value TYPE i.
            ENDCLASS.

            CLASS zcl_test IMPLEMENTATION.
              METHOD get_value.
                rv_value = mv_value.
              ENDMETHOD.
              METHOD set_value.
                mv_value = iv_value.
              ENDMETHOD.
            ENDCLASS.
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
    void compareVersions_success() throws Exception {
        // Arrange
        setupTempSession();
        setupMultipleExecuteInContext();

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Comparing: ZCL_TEST");
        assertContains(result, "class");
        assertSystemHeader(result);

        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void compareVersions_activeVersion() throws Exception {
        // Arrange
        setupTempSession();
        setupMultipleExecuteInContext();

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "active")
                .arg("version_b", "00001")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Active (00000)");
    }

    @Test
    void compareVersions_inactiveVersion() throws Exception {
        // Arrange
        setupTempSession();
        // Modify history to include inactive version
        String historyWithInactive = VERSION_HISTORY_RESPONSE.replace(
                "<atom:id>00002</atom:id>", "<atom:id>99999</atom:id>")
                .replace("versions/00002/source", "versions/99999/source");
        setupMultipleExecuteInContextWithHistory(historyWithInactive);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "00001")
                .arg("version_b", "inactive")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Inactive (99999)");
    }

    @Test
    void compareVersions_withIncludeType() throws Exception {
        // Arrange
        setupTempSession();
        setupMultipleExecuteInContext();

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
                .arg("include_type", "implementations")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "implementations");
    }

    @Test
    void compareVersions_withContextLines() throws Exception {
        // Arrange
        setupTempSession();
        setupMultipleExecuteInContext();

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
                .arg("context_lines", 5)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void compareVersions_interface() throws Exception {
        // Arrange
        setupTempSession();
        setupMultipleExecuteInContext();

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZIF_TEST")
                .arg("object_type", "interface")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void compareVersions_program() throws Exception {
        // Arrange
        setupTempSession();
        setupMultipleExecuteInContext();

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZTEST_PROGRAM")
                .arg("object_type", "program")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void compareVersions_noDifferences() throws Exception {
        // Arrange
        setupTempSession();
        // Return same source for both versions
        AtomicInteger callCount = new AtomicInteger(0);
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    if (count == 1) {
                        return VERSION_HISTORY_RESPONSE;
                    }
                    return SOURCE_VERSION_A; // Same source for both
                });

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // "No differences found" is expected when versions are identical
        assertContains(result, "File:");
    }

    @Test
    void compareVersions_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        AtomicInteger callCount = new AtomicInteger(0);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    if (count == 1) {
                        return VERSION_HISTORY_RESPONSE;
                    } else if (count == 2) {
                        return SOURCE_VERSION_A;
                    }
                    return SOURCE_VERSION_B;
                });

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
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
    void compareVersions_missingObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_type", "class")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void compareVersions_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void compareVersions_missingVersionA_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_b", "00002")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "version_a is required");
    }

    @Test
    void compareVersions_missingVersionB_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "00001")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "version_b is required");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void compareVersions_versionNotFound_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(VERSION_HISTORY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "00099") // Non-existent version
                .arg("version_b", "00002")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "not found");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void compareVersions_serverError_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Server error"));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_a", "00001")
                .arg("version_b", "00002")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
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

    private void setupMultipleExecuteInContext() throws Exception {
        setupMultipleExecuteInContextWithHistory(VERSION_HISTORY_RESPONSE);
    }

    private void setupMultipleExecuteInContextWithHistory(String historyResponse) throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    if (count == 1) {
                        return historyResponse;
                    } else if (count == 2) {
                        return SOURCE_VERSION_A;
                    }
                    return SOURCE_VERSION_B;
                });
    }
}
