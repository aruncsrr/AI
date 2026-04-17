package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CheckSyntaxHandler.
 */
class CheckSyntaxHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private CheckSyntaxHandler handler;

    private JcoSession mockSession;

    private static final String SYNTAX_VALID_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkrun:checkRun xmlns:chkrun="http://www.sap.com/adt/checkrun"
                             checkExecuted="true" hasErrors="false" hasWarnings="false">
            </chkrun:checkRun>
            """;

    private static final String SYNTAX_ERROR_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkrun:checkRun xmlns:chkrun="http://www.sap.com/adt/checkrun">
                <chkrun:checkMessage type="E" severity="error">
                    <chkrun:shortText>Syntax error: Unknown statement</chkrun:shortText>
                </chkrun:checkMessage>
            </chkrun:checkRun>
            """;

    private static final String SYNTAX_WARNING_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkrun:checkRun xmlns:chkrun="http://www.sap.com/adt/checkrun" checkExecuted="true">
                <chkrun:checkMessage type="W" severity="warning">
                    <chkrun:shortText>Variable LV_UNUSED is never used</chkrun:shortText>
                </chkrun:checkMessage>
            </chkrun:checkRun>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        // Override base class mocks for syntax-specific behavior
        setupSyntaxMocks();
    }

    private void setupSyntaxMocks() throws Exception {
        Path mockPath = Path.of("/tmp/sap-mcp/test_001/syntax/ZCL_TEST_CLASS_syntax.xml");
        lenient().when(fileStorageService.sanitizeFilename(anyString())).thenAnswer(i -> i.getArgument(0));
        lenient().when(fileStorageService.writeXml(anyString(), eq(FileStorageService.CAT_SYNTAX), anyString(), anyString()))
                .thenReturn(mockPath);
        lenient().when(fileStorageService.getByteSize(any(Path.class))).thenReturn(1234L);
        lenient().when(fileStorageService.formatExcerptSection(anyString(), eq(FileStorageService.EXT_XML)))
                .thenReturn("\n--- Excerpt (100 of 1234 chars) ---\n<?xml...");

        // Default syntax check metadata
        lenient().when(metadataExtractorService.extractSyntaxCheckMetadata(anyString()))
                .thenReturn(java.util.Map.of(
                        "status", "VALID",
                        "errors", 0,
                        "warnings", 0,
                        "checkExecuted", true
                ));
    }

    @Test
    void checkSyntax_success_valid() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(SYNTAX_VALID_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Syntax Check: ZCL_TEST_CLASS (class)");
        assertContains(result, "Status: VALID");
        assertContains(result, "File:");
        assertSystemHeader(result);
    }

    @Test
    void checkSyntax_withErrors() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(SYNTAX_ERROR_RESPONSE);
        when(metadataExtractorService.extractSyntaxCheckMetadata(anyString()))
                .thenReturn(java.util.Map.of(
                        "status", "INVALID",
                        "errors", 1,
                        "warnings", 0,
                        "topErrors", java.util.List.of("Syntax error: Unknown statement")
                ));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Status: INVALID");
        assertContains(result, "Errors: 1");
        assertContains(result, "Top Errors:");
        assertContains(result, "Syntax error: Unknown statement");
    }

    @Test
    void checkSyntax_withWarnings() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(SYNTAX_WARNING_RESPONSE);
        when(metadataExtractorService.extractSyntaxCheckMetadata(anyString()))
                .thenReturn(java.util.Map.of(
                        "status", "VALID (with warnings)",
                        "errors", 0,
                        "warnings", 1,
                        "checkExecuted", true
                ));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Status: VALID (with warnings)");
        assertContains(result, "Warnings: 1");
    }

    @Test
    void checkSyntax_differentObjectTypes() throws Exception {
        // Test interface
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(SYNTAX_VALID_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZIF_TEST")
                .arg("object_type", "interface")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);
        assertSuccess(result);
        assertContains(result, "Syntax Check: ZIF_TEST (interface)");
    }

    @Test
    void checkSyntax_noSession_createsTempSession_success() throws Exception {
        // Arrange - No session_id provided, should create temp session
        mockTempSessionPattern();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(SYNTAX_VALID_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Status: VALID");
        assertContains(result, "File:");

        // Verify temp session lifecycle
        verifyTempSessionLifecycle();
    }

    @Test
    void checkSyntax_noSession_createsTempSession_withErrors() throws Exception {
        // Arrange - No session_id, syntax errors should still be returned
        mockTempSessionPattern();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(SYNTAX_ERROR_RESPONSE);
        when(metadataExtractorService.extractSyntaxCheckMetadata(anyString()))
                .thenReturn(java.util.Map.of(
                        "status", "INVALID",
                        "errors", 1,
                        "warnings", 0,
                        "topErrors", java.util.List.of("Syntax error: Unknown statement")
                ));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_BROKEN_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Syntax error: Unknown statement");

        // Verify temp session lifecycle
        verifyTempSessionLifecycle();
    }

    @Test
    void checkSyntax_noSession_cleanupOnException() throws Exception {
        // Arrange - Temp session should be cleaned up even on failure
        mockTempSessionPattern();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("SAP connection error"));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "SAP connection error");

        // Verify temp session was still cleaned up
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void checkSyntax_missingObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_type", "class")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void checkSyntax_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void checkSyntax_sessionNotFound_returnsError() {
        // Arrange
        when(jcoSessionManager.getSession("invalid-session"))
                .thenThrow(new IllegalArgumentException("Session not found"));

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("session_id", "invalid-session")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Session not found");
    }

    @Test
    void checkSyntax_writesFileToCorrectPath() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(SYNTAX_VALID_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .arg("object_type", "class")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Verify file was written with correct category and naming
        verify(fileStorageService).writeXml(
                eq(TEST_SYSTEM_ID + "_" + TEST_CLIENT),
                eq(FileStorageService.CAT_SYNTAX),
                eq("ZCL_TEST_CLASS_syntax"),
                eq(SYNTAX_VALID_RESPONSE)
        );
    }

    @Test
    void checkSyntax_functionModule_success() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(SYNTAX_VALID_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "Z_FHP_PLN_CLEANUP")
                .arg("object_type", "function_module")
                .arg("group_name", "ZFHP_PLANNING")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Syntax Check: Z_FHP_PLN_CLEANUP (function_module)");
    }

    @Test
    void checkSyntax_functionModule_missingGroupName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "Z_FHP_PLN_CLEANUP")
                .arg("object_type", "function_module")
                .arg("session_id", TEST_SESSION_ID)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "group_name is required when object_type is function_module");
    }
}
