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

class UnlockObjectHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private UnlockObjectHandler handler;

    private JcoSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockSession = createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
        mockGetSession(TEST_SESSION_ID);
    }

    @Test
    void unlockObject_success() throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any())).thenReturn(null);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("lock_handle", "LOCK123")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void unlockObject_missingSessionId_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .arg("lock_handle", "LOCK123")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "session_id is required");
    }

    @Test
    void unlockObject_missingLockHandle_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .arg("object_name", "ZCL_TEST")
                .arg("object_type", "class")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "lock_handle is required");
    }
}
