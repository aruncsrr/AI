package com.sapjco.mcp.handlers;

import com.sap.conn.jco.JCoDestination;
import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.config.SystemConfigLoader.SystemConfig;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.model.LockResponse;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.JcoSessionManager;
import com.sapjco.mcp.service.MetadataExtractorService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Base test class for handler unit tests.
 * Provides common mocks, fixtures, and utility methods.
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseHandlerTest {

    // Standard mocks
    @Mock
    protected SystemConfigLoader systemConfigLoader;

    @Mock
    protected JcoSessionManager jcoSessionManager;

    @Mock
    protected AdtClient adtClient;

    @Mock
    protected FileStorageService fileStorageService;

    @Mock
    protected MetadataExtractorService metadataExtractorService;

    @Mock
    protected McpSyncServerExchange exchange;

    @Mock
    protected JCoDestination jcoDestination;

    // Standard fixtures
    protected static final String TEST_SESSION_ID = "test-session-12345";
    protected static final String TEST_TEMP_SESSION_ID = "temp-session-67890";
    protected static final String TEST_SYSTEM_ID = "dev";
    protected static final String TEST_HOST = "sap.example.com";
    protected static final String TEST_CLIENT = "100";
    protected static final String TEST_LOCK_HANDLE = "lock-handle-abc";
    protected static final String TEST_TRANSPORT = "DEVK900001";
    protected static final String TEST_DESTINATION_NAME = "dest-dev";

    /**
     * Base setup that runs before each test in all subclasses.
     * Sets up common mock behaviors that most tests need.
     */
    @BeforeEach
    void baseSetUp() throws Exception {
        mockFileStorageService();
        mockMetadataExtractorService();
    }

    /**
     * Create a mock JcoSession with standard test values.
     */
    protected JcoSession createMockSession() {
        return createMockSession(TEST_SESSION_ID, TEST_SYSTEM_ID);
    }

    /**
     * Create a mock JcoSession with specified values.
     */
    protected JcoSession createMockSession(String sessionId, String systemId) {
        JcoSession session = mock(JcoSession.class);
        lenient().when(session.getSessionId()).thenReturn(sessionId);
        lenient().when(session.getSystemId()).thenReturn(systemId);
        lenient().when(session.getDestinationName()).thenReturn(TEST_DESTINATION_NAME);
        lenient().when(session.getHttpClient()).thenReturn(mock(OkHttpClient.class));
        lenient().when(session.getCsrfTokenCache()).thenReturn(new ConcurrentHashMap<>());
        lenient().when(session.getCreatedAt()).thenReturn(LocalDateTime.now());
        lenient().when(session.getLastAccessedAt()).thenReturn(LocalDateTime.now());
        return session;
    }

    /**
     * Create a mock ResolvedSystem with standard test values.
     */
    protected ResolvedSystem createMockResolvedSystem() {
        return createMockResolvedSystem(TEST_SYSTEM_ID, TEST_HOST, TEST_CLIENT);
    }

    /**
     * Create a mock ResolvedSystem with specified values.
     */
    protected ResolvedSystem createMockResolvedSystem(String systemId, String host, String client) {
        SystemConfig config = mock(SystemConfig.class);
        lenient().when(config.getEffectiveHost()).thenReturn(host);
        lenient().when(config.getClient()).thenReturn(client);
        lenient().when(config.getSysnr()).thenReturn("00");
        lenient().when(config.getAuthType()).thenReturn(SystemConfigLoader.AuthType.snc);

        ResolvedSystem resolved = mock(ResolvedSystem.class);
        lenient().when(resolved.getSystemId()).thenReturn(systemId);
        lenient().when(resolved.getConfig()).thenReturn(config);

        return resolved;
    }

    /**
     * Create a mock LockResponse.
     */
    protected LockResponse createMockLockResponse() {
        return createMockLockResponse(TEST_LOCK_HANDLE, TEST_TRANSPORT);
    }

    /**
     * Create a mock LockResponse with specified values.
     */
    protected LockResponse createMockLockResponse(String lockHandle, String transportNumber) {
        LockResponse lock = mock(LockResponse.class);
        lenient().when(lock.getLockHandle()).thenReturn(lockHandle);
        lenient().when(lock.getTransportNumber()).thenReturn(transportNumber);
        return lock;
    }

    /**
     * Setup common mocks for getSystem().
     * Uses lenient() to avoid UnnecessaryStubbingException when not all tests use the stub.
     */
    protected void mockGetSystem() throws Exception {
        mockGetSystem(TEST_SYSTEM_ID);
    }

    /**
     * Setup mocks for getSystem() with specific system ID.
     * Uses lenient() to avoid UnnecessaryStubbingException when not all tests use the stub.
     */
    protected void mockGetSystem(String systemId) throws Exception {
        ResolvedSystem resolved = createMockResolvedSystem(systemId, TEST_HOST, TEST_CLIENT);
        lenient().when(systemConfigLoader.getSystem(systemId)).thenReturn(resolved);
        lenient().when(systemConfigLoader.getSystem(null)).thenReturn(resolved);
    }

    /**
     * Setup mocks for getSession().
     * Uses lenient() to avoid UnnecessaryStubbingException when not all tests use the stub.
     */
    protected JcoSession mockGetSession() throws Exception {
        return mockGetSession(TEST_SESSION_ID);
    }

    /**
     * Setup mocks for getSession() with specific session ID.
     * Uses lenient() to avoid UnnecessaryStubbingException when not all tests use the stub.
     */
    protected JcoSession mockGetSession(String sessionId) throws Exception {
        JcoSession session = createMockSession(sessionId, TEST_SYSTEM_ID);
        lenient().when(jcoSessionManager.getSession(sessionId)).thenReturn(session);
        return session;
    }

    /**
     * Setup mocks for temporary session pattern (create + cleanup).
     * Uses lenient() to avoid UnnecessaryStubbingException when not all tests use the stub.
     */
    protected void mockTempSessionPattern() throws Exception {
        lenient().when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_TEMP_SESSION_ID);

        JcoSession tempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
        lenient().when(jcoSessionManager.getSession(TEST_TEMP_SESSION_ID)).thenReturn(tempSession);
    }

    /**
     * Setup default lenient stubs for FileStorageService common methods.
     * These are lenient so they don't cause failures if not used in all tests.
     */
    protected void mockFileStorageService() throws Exception {
        // Mock sanitizeFilename to return uppercase input
        lenient().when(fileStorageService.sanitizeFilename(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).toUpperCase());

        // Mock write methods to return a dummy path (cross-platform)
        java.nio.file.Path dummyPath = java.nio.file.Paths.get(
                System.getProperty("java.io.tmpdir"), "test-file.xml");
        lenient().when(fileStorageService.writeXml(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(dummyPath);
        lenient().when(fileStorageService.writeFile(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(dummyPath);
        lenient().when(fileStorageService.writeSource(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(dummyPath);
        lenient().when(fileStorageService.writeClassIncludeSource(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(dummyPath);
        lenient().when(fileStorageService.writeDiff(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(dummyPath);
        lenient().when(fileStorageService.writeDdl(anyString(), anyString(), anyString()))
                .thenReturn(dummyPath);
        lenient().when(fileStorageService.writeHtml(anyString(), anyString(), anyString()))
                .thenReturn(dummyPath);

        // Mock getByteSize to return a reasonable size
        lenient().when(fileStorageService.getByteSize(any(java.nio.file.Path.class)))
                .thenReturn(1234L);
    }

    /**
     * Setup default lenient stubs for MetadataExtractorService.
     * Returns empty Maps for all extract methods.
     */
    protected void mockMetadataExtractorService() {
        java.util.Map<String, Object> emptyMetadata = java.util.Collections.emptyMap();
        lenient().when(metadataExtractorService.extract(anyString(), anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractWhereUsedMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractAbapUnitMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractAtcMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractPackageMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractSearchMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractDataMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractTransportMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractDiffMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractCoverageMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractVersionHistoryMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractBopfMetadata(anyString())).thenReturn(emptyMetadata);
        lenient().when(metadataExtractorService.extractSyntaxCheckMetadata(anyString())).thenReturn(emptyMetadata);
    }

    /**
     * Setup executeInContext to invoke the operation and return a value.
     * Uses lenient() to avoid UnnecessaryStubbingException when not all tests use the stub.
     */
    @SuppressWarnings("unchecked")
    protected <T> void mockExecuteInContext(T returnValue) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(anyString(), any()))
                .thenAnswer((Answer<T>) invocation -> {
                    JcoSessionManager.JcoOperation<T> operation =
                            (JcoSessionManager.JcoOperation<T>) invocation.getArgument(1);
                    return operation.execute(jcoDestination, createMockSession());
                });
    }

    /**
     * Setup executeInContext to invoke the operation without return value.
     */
    protected void mockExecuteInContextVoid() throws Exception {
        mockExecuteInContext(null);
    }

    /**
     * Setup executeInContext to throw an exception.
     * Uses lenient() to avoid UnnecessaryStubbingException when not all tests use the stub.
     */
    protected void mockExecuteInContextThrows(Exception exception) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(anyString(), any()))
                .thenThrow(exception);
    }

    /**
     * Create a CallToolRequest with the given arguments.
     */
    protected CallToolRequest createRequest(Map<String, Object> args) {
        return new CallToolRequest("tool", args);
    }

    /**
     * Create a CallToolRequest with single argument.
     */
    protected CallToolRequest createRequest(String key, Object value) {
        Map<String, Object> args = new HashMap<>();
        args.put(key, value);
        return createRequest(args);
    }

    /**
     * Create a CallToolRequest with arguments builder pattern.
     */
    protected RequestBuilder requestBuilder() {
        return new RequestBuilder();
    }

    /**
     * Fluent builder for CallToolRequest.
     */
    protected static class RequestBuilder {
        private final Map<String, Object> args = new HashMap<>();

        public RequestBuilder arg(String key, Object value) {
            if (value != null) {
                args.put(key, value);
            }
            return this;
        }

        public CallToolRequest build() {
            return new CallToolRequest("tool", args);
        }
    }

    // ==================== Assertion Helpers ====================

    /**
     * Assert that the result is successful (isError = null or false).
     */
    protected void assertSuccess(CallToolResult result) {
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError() == null || !result.isError(),
                "Result should be successful, but was error: " + getResultText(result));
    }

    /**
     * Assert that the result is an error.
     */
    protected void assertError(CallToolResult result) {
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError() != null && result.isError(),
                "Result should be an error");
    }

    /**
     * Assert that the result contains the expected text.
     */
    protected void assertContains(CallToolResult result, String... expectedTexts) {
        String text = getResultText(result);
        for (String expected : expectedTexts) {
            assertTrue(text.contains(expected),
                    String.format("Expected result to contain '%s', but was: %s", expected, text));
        }
    }

    /**
     * Assert that the result does not contain the expected text.
     */
    protected void assertNotContains(CallToolResult result, String... unexpectedTexts) {
        String text = getResultText(result);
        for (String unexpected : unexpectedTexts) {
            assertFalse(text.contains(unexpected),
                    String.format("Expected result to NOT contain '%s', but was: %s", unexpected, text));
        }
    }

    /**
     * Extract text content from result.
     */
    protected String getResultText(CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (var content : result.content()) {
            if (content instanceof TextContent tc) {
                sb.append(tc.text());
            }
        }
        return sb.toString();
    }

    /**
     * Assert that the result contains the system header.
     */
    protected void assertSystemHeader(CallToolResult result) {
        assertContains(result, "[" + TEST_SYSTEM_ID, TEST_HOST, TEST_CLIENT + "]");
    }

    /**
     * Verify that temporary session was created and destroyed.
     */
    protected void verifyTempSessionLifecycle() throws Exception {
        verify(jcoSessionManager).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    /**
     * Verify that no temporary session was created.
     */
    protected void verifyNoTempSession() throws Exception {
        verify(jcoSessionManager, never()).createSession(any(CreateSessionRequest.class));
        verify(jcoSessionManager, never()).destroySession(anyString());
    }
}
