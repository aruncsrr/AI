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

class SaveIncludeHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveIncludeHandler handler;

    private JcoSession mockSession;

    private static final String INCLUDE_SOURCE = """
            *----------------------------------------------------------------------*
            * Include ZTEST_INCLUDE
            *----------------------------------------------------------------------*
            DATA: gv_test TYPE string.
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void saveInclude_success() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn("<response>saved</response>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("include_name", "ZTEST_INCLUDE")
                .arg("source_code", INCLUDE_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
    }

    @Test
    void saveInclude_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("include_name", "ZTEST_INCLUDE")
                .arg("source_code", INCLUDE_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveInclude_missingIncludeName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_code", INCLUDE_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "include_name is required");
    }

    @Test
    void saveInclude_missingSourceCode_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("include_name", "ZTEST_INCLUDE")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }

    @Test
    void saveInclude_withFunctionGroupName_success() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn("<response>saved</response>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("include_name", "LZFG_TESTF01")
                .arg("function_group_name", "ZFG_TEST")
                .arg("source_code", INCLUDE_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "<response>saved</response>");
    }

    @Test
    void saveInclude_withFunctionGroupName_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("include_name", "LZFG_TESTF01")
                .arg("function_group_name", "ZFG_TEST")
                .arg("source_code", INCLUDE_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }
}
