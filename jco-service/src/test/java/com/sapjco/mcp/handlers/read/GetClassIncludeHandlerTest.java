package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetClassIncludeHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetClassIncludeHandler handler;

    private JcoSession mockTempSession;

    private static final Path TEST_FILE_PATH = Path.of("/tmp/sap-mcp/dev_100/class_include/ZCL_TEST.definitions.abap");

    private static final String DEFINITIONS_SOURCE = "CLASS zcl_test DEFINITION PUBLIC. ENDCLASS.";
    private static final String IMPLEMENTATIONS_SOURCE = "CLASS zcl_test IMPLEMENTATION. ENDCLASS.";
    private static final String TESTCLASSES_SOURCE = "CLASS ltcl_test DEFINITION FOR TESTING. ENDCLASS.";

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
        setupFileStorageService();
    }

    /**
     * Setup FileStorageService mock with specific test values.
     */
    private void setupFileStorageService() throws Exception {
        lenient().when(fileStorageService.writeClassIncludeSource(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(TEST_FILE_PATH);
        lenient().when(fileStorageService.getLineCount(any(Path.class))).thenReturn(10L);
        lenient().when(fileStorageService.getByteSize(any(Path.class))).thenReturn(500L);
    }

    @Test
    void getClassInclude_definitions_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(DEFINITIONS_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "definitions")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "File:");  // File-based output
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getClassInclude_implementations_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(IMPLEMENTATIONS_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "implementations")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "File:");  // File-based output
    }

    @Test
    void getClassInclude_testClasses_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(TESTCLASSES_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "testClasses")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "File:");  // File-based output
    }

    @Test
    void getClassInclude_withInactiveVersion() throws Exception {
        setupTempSession();
        setupExecuteInContext(DEFINITIONS_SOURCE);

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST")
                .arg("include_type", "definitions")
                .arg("version", "inactive")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getClassInclude_missingClassName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("include_type", "definitions")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "class_name is required");
    }

    @Test
    void getClassInclude_missingIncludeType_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZCL_TEST")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "include_type is required");
    }

    @Test
    void getClassInclude_notFound_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Class not found"));

        CallToolRequest request = requestBuilder()
                .arg("class_name", "ZNONEXISTENT")
                .arg("include_type", "definitions")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    private void setupTempSession() throws Exception {
        lenient().when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_TEMP_SESSION_ID);
        lenient().when(jcoSessionManager.getSession(TEST_TEMP_SESSION_ID)).thenReturn(mockTempSession);
    }

    private void setupExecuteInContext(String returnValue) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(returnValue);
    }
}
