package com.sapjco.mcp.handlers.debug;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

class DebugDeleteBreakpointHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private DebugDeleteBreakpointHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
    }

    @Test
    void deleteBreakpoint_success() throws Exception {
        CallToolRequest request = requestBuilder()
                .arg("terminal_id", "TERM123")
                .arg("ide_id", "IDE456")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "Breakpoints Deleted");
    }

    @Test
    void deleteBreakpoint_missingTerminalId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("ide_id", "IDE456")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "terminal_id is required");
    }

    @Test
    void deleteBreakpoint_missingIdeId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("terminal_id", "TERM123")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "ide_id is required");
    }
}
