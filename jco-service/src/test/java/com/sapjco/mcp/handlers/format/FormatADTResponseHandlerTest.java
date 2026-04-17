package com.sapjco.mcp.handlers.format;

import com.sapjco.mcp.formatters.ADTFormatterRegistry;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FormatADTResponseHandler.
 * Tests file-based and inline response formatting.
 */
class FormatADTResponseHandlerTest extends BaseHandlerTest {

    @Mock
    private ADTFormatterRegistry formatterRegistry;

    @InjectMocks
    private FormatADTResponseHandler handler;

    private static final String TEST_RESPONSE_FILE = "/tmp/sap-mcp/dev_100/where_used/ZCL_TEST.xml";
    private static final String TEST_TOOL_NAME = "GetWhereUsed";
    private static final String TEST_RAW_XML = "<xml>raw response</xml>";
    private static final String TEST_FORMATTED_TEXT = "Formatted: 3 usages found";

    @BeforeEach
    void setUp() {
        lenient().when(formatterRegistry.getRegisteredTools()).thenReturn(Set.of(TEST_TOOL_NAME, "RunAbapUnit", "SaveClass"));
    }

    // ========================= File-Based Input Tests =========================

    @Test
    void formatResponse_withResponseFile_readsFromFile() throws Exception {
        // Arrange
        when(fileStorageService.readSource(TEST_RESPONSE_FILE)).thenReturn(TEST_RAW_XML);
        when(formatterRegistry.hasFormatter(TEST_TOOL_NAME)).thenReturn(true);
        when(formatterRegistry.format(TEST_TOOL_NAME, TEST_RAW_XML)).thenReturn(TEST_FORMATTED_TEXT);

        CallToolRequest request = requestBuilder()
                .arg("tool_name", TEST_TOOL_NAME)
                .arg("response_file", TEST_RESPONSE_FILE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, TEST_FORMATTED_TEXT);
        verify(fileStorageService).readSource(TEST_RESPONSE_FILE);
        verify(formatterRegistry).format(TEST_TOOL_NAME, TEST_RAW_XML);
    }

    @Test
    void formatResponse_bothFileAndInline_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("tool_name", TEST_TOOL_NAME)
                .arg("response_file", TEST_RESPONSE_FILE)
                .arg("raw_response", TEST_RAW_XML)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Provide response_file OR raw_response, not both");
    }

    @Test
    void formatResponse_fileNotFound_returnsError() throws Exception {
        // Arrange
        String nonExistentFile = "/tmp/nonexistent.xml";
        when(fileStorageService.readSource(nonExistentFile))
                .thenThrow(new IllegalArgumentException("File not found: " + nonExistentFile));

        CallToolRequest request = requestBuilder()
                .arg("tool_name", TEST_TOOL_NAME)
                .arg("response_file", nonExistentFile)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "File not found");
    }

    @Test
    void formatResponse_emptyFile_returnsError() throws Exception {
        // Arrange
        String emptyFile = "/tmp/empty.xml";
        when(fileStorageService.readSource(emptyFile))
                .thenThrow(new IllegalArgumentException("File is empty: " + emptyFile));

        CallToolRequest request = requestBuilder()
                .arg("tool_name", TEST_TOOL_NAME)
                .arg("response_file", emptyFile)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "File is empty");
    }

    @Test
    void formatResponse_fileReadIOException_returnsError() throws Exception {
        // Arrange
        when(fileStorageService.readSource(TEST_RESPONSE_FILE))
                .thenThrow(new IOException("Permission denied"));

        CallToolRequest request = requestBuilder()
                .arg("tool_name", TEST_TOOL_NAME)
                .arg("response_file", TEST_RESPONSE_FILE)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Failed to read response file", "Permission denied");
    }

    @Test
    void formatResponse_neitherProvided_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("tool_name", TEST_TOOL_NAME)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Must provide response_file or raw_response");
    }

    // ========================= Inline Input Tests (Backward Compatibility) =========================

    @Test
    void formatResponse_withRawResponse_formatsInline() {
        // Arrange
        when(formatterRegistry.hasFormatter(TEST_TOOL_NAME)).thenReturn(true);
        when(formatterRegistry.format(TEST_TOOL_NAME, TEST_RAW_XML)).thenReturn(TEST_FORMATTED_TEXT);

        CallToolRequest request = requestBuilder()
                .arg("tool_name", TEST_TOOL_NAME)
                .arg("raw_response", TEST_RAW_XML)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, TEST_FORMATTED_TEXT);
        verify(formatterRegistry).format(TEST_TOOL_NAME, TEST_RAW_XML);
    }

    @Test
    void formatResponse_missingToolName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("raw_response", TEST_RAW_XML)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "tool_name is required");
    }

    @Test
    void formatResponse_emptyToolName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("tool_name", "   ")
                .arg("raw_response", TEST_RAW_XML)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "tool_name is required");
    }

    @Test
    void formatResponse_unknownFormatter_returnsError() {
        // Arrange
        String unknownTool = "UnknownTool";
        when(formatterRegistry.hasFormatter(unknownTool)).thenReturn(false);

        CallToolRequest request = requestBuilder()
                .arg("tool_name", unknownTool)
                .arg("raw_response", TEST_RAW_XML)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "No formatter registered for tool", unknownTool);
    }

    @Test
    void formatResponse_formatterThrowsException_returnsError() {
        // Arrange
        when(formatterRegistry.hasFormatter(TEST_TOOL_NAME)).thenReturn(true);
        when(formatterRegistry.format(TEST_TOOL_NAME, TEST_RAW_XML))
                .thenThrow(new FormattingException("Invalid XML structure"));

        CallToolRequest request = requestBuilder()
                .arg("tool_name", TEST_TOOL_NAME)
                .arg("raw_response", TEST_RAW_XML)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Invalid XML structure");
    }

    @Test
    void formatResponse_unexpectedException_returnsError() {
        // Arrange
        when(formatterRegistry.hasFormatter(TEST_TOOL_NAME)).thenReturn(true);
        when(formatterRegistry.format(TEST_TOOL_NAME, TEST_RAW_XML))
                .thenThrow(new RuntimeException("Unexpected error"));

        CallToolRequest request = requestBuilder()
                .arg("tool_name", TEST_TOOL_NAME)
                .arg("raw_response", TEST_RAW_XML)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "Unexpected error");
    }

    // ========================= Tool Definition Tests =========================

    @Test
    void getToolDefinition_includesResponseFileParameter() {
        // Act
        var tool = handler.getToolDefinition();

        // Assert
        assertNotNull(tool);
        assertEquals("FormatADTResponse", tool.name());
        assertTrue(tool.description().contains("Supported tools:"));
    }
}
