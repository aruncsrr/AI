package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GetUsageSnippetsHandler.
 * Tests fetching source code snippets for where-used results (lazy loading phase 2).
 */
class GetUsageSnippetsHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetUsageSnippetsHandler handler;

    private JcoSession mockTempSession;

    private static final String SNIPPETS_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <usageSnippets xmlns="http://www.sap.com/adt/ris/usageReferences"
                           xmlns:adtcore="http://www.sap.com/adt/core">
                <codeSnippet adtcore:uri="/sap/bc/adt/oo/classes/ZCL_CONSUMER/source/main">
                    <match line="42" column="10"/>
                    <content>    lo_instance = NEW zcl_my_class( ).
        lo_instance->do_something( ).</content>
                </codeSnippet>
                <codeSnippet adtcore:uri="/sap/bc/adt/programs/programs/ZTEST_PROGRAM/source/main">
                    <match line="15" column="5"/>
                    <content>  DATA(lo_obj) = NEW zcl_my_class( ).</content>
                </codeSnippet>
            </usageSnippets>
            """;

    private static final String EMPTY_SNIPPETS_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <usageSnippets xmlns:adtcore="http://www.sap.com/adt/core">
            </usageSnippets>
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
    void getUsageSnippets_success() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextPost(SNIPPETS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", Arrays.asList("IDENT1", "IDENT2"))
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Metadata summary is returned (XML written to file)
        assertContains(result, "File:");
        assertContains(result, "bytes");
        assertSystemHeader(result);

        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getUsageSnippets_singleIdentifier() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextPost(SNIPPETS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", Arrays.asList("IDENT1"))
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getUsageSnippets_withDefinitions() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextPost(SNIPPETS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", Arrays.asList("IDENT1"))
                .arg("definitions", true)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getUsageSnippets_withIndirectReferences() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextPost(SNIPPETS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", Arrays.asList("IDENT1"))
                .arg("indirect_references", true)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
    }

    @Test
    void getUsageSnippets_returnsFilePath() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextPost(SNIPPETS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", Arrays.asList("IDENT1"))
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
    void getUsageSnippets_emptyResult() throws Exception {
        // Arrange
        setupTempSession();
        setupExecuteInContextPost(EMPTY_SNIPPETS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", Arrays.asList("IDENT1"))
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        // Returns file path even for empty results
        assertContains(result, "File:");
    }

    @Test
    void getUsageSnippets_withProvidedSession() throws Exception {
        // Arrange
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(SNIPPETS_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", Arrays.asList("IDENT1"))
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
    void getUsageSnippets_missingIdentifiers_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_identifiers is required");
    }

    @Test
    void getUsageSnippets_emptyIdentifiers_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", Arrays.asList())
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_identifiers is required");
    }

    @Test
    void getUsageSnippets_tooManyIdentifiers_returnsError() {
        // Arrange - Create a list with more than 50 identifiers
        List<String> identifiers = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            identifiers.add("IDENT" + i);
        }

        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", identifiers)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Maximum 50");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void getUsageSnippets_serverError_returnsError() throws Exception {
        // Arrange
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Server error fetching snippets"));

        CallToolRequest request = requestBuilder()
                .arg("object_identifiers", Arrays.asList("IDENT1"))
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Server error");
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

    private void setupExecuteInContextPost(String returnValue) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(returnValue);
    }
}
