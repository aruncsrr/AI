package com.sapjco.mcp.handlers.testing;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.HttpProxyResponse;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RunATCHandler.
 * Tests ATC (ABAP Test Cockpit) check execution.
 *
 * Note: RunATCHandler has a complex multi-step workflow:
 * 1. Get system default check variant (statelessGetViaRfc)
 * 2. Create worklist (statelessPostViaRfc -> HttpProxyResponse)
 * 3. Submit ATC run (statelessPostViaRfc -> HttpProxyResponse)
 * 4. Poll status (statelessGetViaRfc -> String)
 * 5. Get findings (statelessGetViaRfc -> String)
 */
class RunATCHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private RunATCHandler handler;

    private JcoSession mockSession;

    // Sample ATC worklist ID (32-character GUID)
    private static final String TEST_WORKLIST_ID = "12345678901234567890123456789012";
    private static final String TEST_RUN_ID = "run-abc-123";

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSessionPattern();
        mockSession = mockGetSession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void runATC_missingObjectName_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_type", "class")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_name is required");
    }

    @Test
    void runATC_missingObjectType_returnsError() {
        // Arrange
        CallToolRequest request = requestBuilder()
                .arg("object_name", "ZCL_TEST_CLASS")
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "object_type is required");
    }
}
