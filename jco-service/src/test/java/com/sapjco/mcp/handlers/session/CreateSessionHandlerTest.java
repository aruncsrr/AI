package com.sapjco.mcp.handlers.session;

import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.config.SystemConfigLoader.SystemConfig;
import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.service.JcoSessionManager;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateSessionHandler.
 */
class CreateSessionHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private CreateSessionHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        // Default mock setup
        mockGetSystem();
    }

    @Test
    void createSession_defaultSystem_success() throws Exception {
        // Arrange
        when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_SESSION_ID);

        CallToolRequest request = createRequest(null);

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Session created successfully");
        assertContains(result, TEST_SESSION_ID);
        assertSystemHeader(result);

        // Verify session was created with correct parameters
        ArgumentCaptor<CreateSessionRequest> captor = ArgumentCaptor.forClass(CreateSessionRequest.class);
        verify(jcoSessionManager).createSession(captor.capture());
        assertEquals(TEST_SYSTEM_ID, captor.getValue().getSystemId());
    }

    @Test
    void createSession_specificSystem_success() throws Exception {
        // Arrange
        String prodSystemId = "prod";
        String prodHost = "prod-sap.example.com";
        String prodClient = "200";

        ResolvedSystem prodResolved = createMockResolvedSystem(prodSystemId, prodHost, prodClient);
        when(systemConfigLoader.getSystem(prodSystemId)).thenReturn(prodResolved);
        when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn("prod-session-123");

        CallToolRequest request = requestBuilder()
                .arg("system_id", prodSystemId)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "Session created successfully");
        assertContains(result, "prod-session-123");
        assertContains(result, prodSystemId);

        ArgumentCaptor<CreateSessionRequest> captor = ArgumentCaptor.forClass(CreateSessionRequest.class);
        verify(jcoSessionManager).createSession(captor.capture());
        assertEquals(prodSystemId, captor.getValue().getSystemId());
    }

    @Test
    void createSession_sncAuth_enablesHttpSso() throws Exception {
        // Arrange
        SystemConfig config = mock(SystemConfig.class);
        when(config.getEffectiveHost()).thenReturn(TEST_HOST);
        when(config.getClient()).thenReturn(TEST_CLIENT);
        when(config.getSysnr()).thenReturn("00");
        when(config.getAuthType()).thenReturn(SystemConfigLoader.AuthType.snc);

        SystemConfigLoader.SncConfig sncConfig = mock(SystemConfigLoader.SncConfig.class);
        when(sncConfig.getPartnername()).thenReturn("p/secude:CN=DEV, O=SAP-AG, C=DE");
        when(sncConfig.getQop()).thenReturn(9);
        when(config.getSnc()).thenReturn(sncConfig);

        ResolvedSystem resolved = mock(ResolvedSystem.class);
        when(resolved.getSystemId()).thenReturn(TEST_SYSTEM_ID);
        when(resolved.getConfig()).thenReturn(config);
        when(systemConfigLoader.getSystem(any())).thenReturn(resolved);

        when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_SESSION_ID);

        CallToolRequest request = createRequest(null);

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "snc");  // Auth type shown

        ArgumentCaptor<CreateSessionRequest> captor = ArgumentCaptor.forClass(CreateSessionRequest.class);
        verify(jcoSessionManager).createSession(captor.capture());
        assertTrue(captor.getValue().isEnableHttpSso());
    }

    @Test
    void createSession_systemNotFound_returnsError() throws Exception {
        // Arrange
        String unknownSystem = "unknown";
        when(systemConfigLoader.getSystem(unknownSystem))
                .thenThrow(new IllegalArgumentException("System not found: " + unknownSystem));

        CallToolRequest request = requestBuilder()
                .arg("system_id", unknownSystem)
                .build();

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "System not found");
    }

    @Test
    void createSession_jcoError_returnsError() throws Exception {
        // Arrange
        when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenThrow(new RuntimeException("JCo connection failed"));

        CallToolRequest request = createRequest(null);

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertError(result);
        assertContains(result, "JCo connection failed");
    }

    @Test
    void createSession_nullArguments_usesDefaultSystem() throws Exception {
        // Arrange
        when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_SESSION_ID);

        // Create request with null arguments map
        CallToolRequest request = new CallToolRequest("CreateSession", (Map<String, Object>) null);

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        verify(systemConfigLoader).getSystem(null);  // Should call with null for default
    }

    @Test
    void createSession_responseContainsWorkflowHints() throws Exception {
        // Arrange
        when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_SESSION_ID);

        CallToolRequest request = createRequest(null);

        // Act
        CallToolResult result = handler.handle(exchange, request);

        // Assert
        assertSuccess(result);
        assertContains(result, "SaveClass");
        assertContains(result, "ActivateObject");
        assertContains(result, "DestroySession");
    }
}
