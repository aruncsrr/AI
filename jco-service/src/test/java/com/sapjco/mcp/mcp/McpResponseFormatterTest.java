package com.sapjco.mcp.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for McpResponseFormatter.
 */
class McpResponseFormatterTest {

    @Test
    void success_simpleText() {
        // Act
        CallToolResult result = McpResponseFormatter.success("Hello World");

        // Assert
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals(1, result.content().size());

        String text = ((TextContent) result.content().get(0)).text();
        assertEquals("Hello World", text);
    }

    @Test
    void success_withSystemHeader() {
        // Act
        CallToolResult result = McpResponseFormatter.success("[dev | sap.example.com | 100]", "Operation completed");

        // Assert
        assertNotNull(result);
        assertFalse(result.isError());

        String text = ((TextContent) result.content().get(0)).text();
        assertTrue(text.contains("[dev | sap.example.com | 100]"));
        assertTrue(text.contains("Operation completed"));
        // System header should be on first line
        assertTrue(text.startsWith("[dev | sap.example.com | 100]"));
    }

    @Test
    void error_simpleMessage() {
        // Act
        CallToolResult result = McpResponseFormatter.error("Something went wrong");

        // Assert
        assertNotNull(result);
        assertTrue(result.isError());

        String text = ((TextContent) result.content().get(0)).text();
        assertTrue(text.contains("Error:"));
        assertTrue(text.contains("Something went wrong"));
    }

    @Test
    void error_fromException() {
        // Act
        Exception e = new RuntimeException("Connection failed");
        CallToolResult result = McpResponseFormatter.error(e);

        // Assert
        assertNotNull(result);
        assertTrue(result.isError());

        String text = ((TextContent) result.content().get(0)).text();
        assertTrue(text.contains("Connection failed"));
    }

    @Test
    void error_fromExceptionWithNullMessage() {
        // Act
        Exception e = new RuntimeException();  // No message
        CallToolResult result = McpResponseFormatter.error(e);

        // Assert
        assertNotNull(result);
        assertTrue(result.isError());

        String text = ((TextContent) result.content().get(0)).text();
        assertTrue(text.contains("RuntimeException"));
    }

    @Test
    void error_withSystemHeader() {
        // Act
        CallToolResult result = McpResponseFormatter.error("[dev | sap.example.com | 100]", "Save failed");

        // Assert
        assertNotNull(result);
        assertTrue(result.isError());

        String text = ((TextContent) result.content().get(0)).text();
        assertTrue(text.contains("[dev | sap.example.com | 100]"));
        assertTrue(text.contains("Error:"));
        assertTrue(text.contains("Save failed"));
    }

    @Test
    void formatSystemHeader_standard() {
        // Act
        String header = McpResponseFormatter.formatSystemHeader("dev", "sap.example.com", "100");

        // Assert
        assertEquals("[dev | sap.example.com | 100]", header);
    }

    @Test
    void formatSystemHeader_withSpecialCharacters() {
        // Act
        String header = McpResponseFormatter.formatSystemHeader("prod-01", "ldci.wdf.sap.corp", "001");

        // Assert
        assertEquals("[prod-01 | ldci.wdf.sap.corp | 001]", header);
    }

    @Test
    void extractHostFromUrl_standard() {
        // Act
        String host = McpResponseFormatter.extractHostFromUrl("https://sap.example.com:44300/sap/bc/adt");

        // Assert
        assertEquals("sap.example.com", host);
    }

    @Test
    void extractHostFromUrl_withPath() {
        // Act
        String host = McpResponseFormatter.extractHostFromUrl("https://ldciqm7.wdf.sap.corp:44300/path/to/resource");

        // Assert
        assertEquals("ldciqm7.wdf.sap.corp", host);
    }

    @Test
    void extractHostFromUrl_malformedReturnsOriginal() {
        // Act
        String host = McpResponseFormatter.extractHostFromUrl("not-a-valid-url");

        // Assert
        assertEquals("not-a-valid-url", host);
    }
}
