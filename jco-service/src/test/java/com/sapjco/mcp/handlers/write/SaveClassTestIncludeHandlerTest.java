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

class SaveClassTestIncludeHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SaveClassTestIncludeHandler handler;

    private JcoSession mockSession;

    private static final String TEST_SOURCE = """
            CLASS ltcl_test DEFINITION FOR TESTING RISK LEVEL HARMLESS.
              PRIVATE SECTION.
                METHODS test_something FOR TESTING.
            ENDCLASS.
            CLASS ltcl_test IMPLEMENTATION.
              METHOD test_something.
                cl_abap_unit_assert=>assert_true( abap_true ).
              ENDMETHOD.
            ENDCLASS.
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void saveClassTestInclude_success() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn("<response>saved</response>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("source_code", TEST_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "<response>saved</response>");  // Raw XML response
    }

    @Test
    void saveClassTestInclude_withTransport() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn("<response>saved</response>");

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .arg("source_code", TEST_SOURCE)
                .arg("transport_number", "NPLK900001")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void saveClassTestInclude_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST")
                .arg("source_code", TEST_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void saveClassTestInclude_missingClassName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("source_code", TEST_SOURCE)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "class_name is required");
    }

    @Test
    void saveClassTestInclude_missingSourceCode_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("class_name", "ZCL_TEST")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "Must provide source_code or source_file");
    }
}
