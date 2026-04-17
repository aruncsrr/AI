package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetVersionContentHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetVersionContentHandler handler;

    private JcoSession mockTempSession;

    private static final String VERSION_HISTORY = """
            <?xml version="1.0" encoding="utf-8"?>
            <atom:feed xmlns:atom="http://www.w3.org/2005/Atom">
                <atom:entry>
                    <atom:id>00000</atom:id>
                    <atom:content type="text/plain" src="/sap/bc/adt/oo/classes/ZCL_TEST/versions/00000/source"/>
                </atom:entry>
                <atom:entry>
                    <atom:id>00001</atom:id>
                    <atom:content type="text/plain" src="/sap/bc/adt/oo/classes/ZCL_TEST/versions/00001/source"/>
                </atom:entry>
            </atom:feed>
            """;

    private static final String SOURCE_CODE = "CLASS zcl_test DEFINITION PUBLIC. ENDCLASS.";

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getVersionContent_success() throws Exception {
        setupTempSession();
        setupMultipleExecuteInContext();

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_id", "00001")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertSystemHeader(result);
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getVersionContent_activeVersion() throws Exception {
        setupTempSession();
        setupMultipleExecuteInContext();

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_id", "active")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getVersionContent_withIncludeType() throws Exception {
        setupTempSession();
        setupMultipleExecuteInContext();

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_id", "00001")
                .arg("include_type", "definitions")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getVersionContent_missingObjectName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("object_type", "class")
                .arg("version_id", "00001")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void getVersionContent_missingObjectType_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("version_id", "00001")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "object_type is required");
    }

    @Test
    void getVersionContent_missingVersionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "version_id is required");
    }

    @Test
    void getVersionContent_versionNotFound_returnsError() throws Exception {
        setupTempSession();
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(VERSION_HISTORY);

        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("version_id", "99998")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "not found");
    }

    private void setupTempSession() throws Exception {
        lenient().when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_TEMP_SESSION_ID);
        lenient().when(jcoSessionManager.getSession(TEST_TEMP_SESSION_ID)).thenReturn(mockTempSession);
    }

    private void setupMultipleExecuteInContext() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    return count == 1 ? VERSION_HISTORY : SOURCE_CODE;
                });
    }
}
