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

class GetDiscoveryHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private GetDiscoveryHandler handler;

    private JcoSession mockTempSession;

    private static final String DISCOVERY_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <service xmlns="http://www.w3.org/2007/app" xmlns:atom="http://www.w3.org/2005/Atom">
                <workspace>
                    <atom:title>ADT Services</atom:title>
                    <collection href="/sap/bc/adt/oo/classes">
                        <atom:title>Classes</atom:title>
                    </collection>
                    <collection href="/sap/bc/adt/programs/programs">
                        <atom:title>Programs</atom:title>
                    </collection>
                </workspace>
            </service>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void getDiscovery_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(DISCOVERY_RESPONSE);

        CallToolRequest request = requestBuilder().build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertSystemHeader(result);
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void getDiscovery_withCustomUri() throws Exception {
        setupTempSession();
        setupExecuteInContext(DISCOVERY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("discovery_uri", "/sap/bc/adt/core/discovery")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getDiscovery_withCategoryFilter() throws Exception {
        setupTempSession();
        setupExecuteInContext(DISCOVERY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("category_scheme", "http://www.sap.com/adt/categories/cts")
                .arg("category_term", "transports")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void getDiscovery_rawResponse() throws Exception {
        setupTempSession();
        setupExecuteInContext(DISCOVERY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("raw_response", true)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "<?xml");
    }

    @Test
    void getDiscovery_withProvidedSession() throws Exception {
        mockGetSession(TEST_SESSION_ID);
        when(jcoSessionManager.executeInContext(eq(TEST_SESSION_ID), any()))
                .thenReturn(DISCOVERY_RESPONSE);

        CallToolRequest request = requestBuilder()
                .arg("session_id", TEST_SESSION_ID)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
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
