package com.sapjco.mcp.handlers.write;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.model.LockResponse;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LockObjectHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private LockObjectHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void lockObject_success() throws Exception {
        LockResponse lockResponse = mock(LockResponse.class);
        when(lockResponse.getLockHandle()).thenReturn("LOCK123");
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn(lockResponse);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void lockObject_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void lockObject_missingObjectName_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_type", "class")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void lockObject_missingObjectType_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "object_type is required");
    }
}
