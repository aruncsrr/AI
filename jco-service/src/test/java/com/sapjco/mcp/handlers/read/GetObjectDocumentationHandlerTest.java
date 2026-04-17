package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetObjectDocumentationHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetObjectDocumentationHandler handler;

    private JcoSession mockTempSession;

    private static final String SAPSCRIPT_RESPONSE = "<html><body>Class documentation for ZCL_TEST</body></html>";
    private static final String MESSAGE_LONGTEXT_RESPONSE = "<html><body>Message 001: Error occurred</body></html>";

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getDocumentation_sapscript_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(SAPSCRIPT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("doc_type", "sapscript")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getDocumentation_messageLongtext_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(MESSAGE_LONGTEXT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZMSGCLASS")
                .arg("doc_type", "message_longtext")
                .arg("message_class", "ZMSGCLASS")
                .arg("message_number", "001")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getDocumentation_withLanguage() throws Exception {
        setupTempSession();
        setupExecuteInContext(SAPSCRIPT_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("doc_type", "sapscript")
                .arg("language", "DE")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getDocumentation_missingObjectName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("doc_type", "sapscript")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void getDocumentation_missingDocType_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "doc_type is required");
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
