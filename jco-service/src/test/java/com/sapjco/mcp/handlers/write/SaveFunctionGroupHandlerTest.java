package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaveFunctionGroupHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveFunctionGroupHandler handler;

    private JcoSession mockSession;

    private static final String FG_SOURCE = """
            FUNCTION-POOL ztest_fg.
            DATA: gv_test TYPE string.
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void saveFunctionGroup_success() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn("<response>saved</response>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZTEST_FG")
                .arg("source_code", FG_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
    }

    @Test
    void saveFunctionGroup_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("group_name", "ZTEST_FG")
                .arg("source_code", FG_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveFunctionGroup_missingGroupName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_code", FG_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "group_name is required");
    }

    @Test
    void saveFunctionGroup_missingSourceCode_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("group_name", "ZTEST_FG")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }
}
