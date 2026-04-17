package com.sapjco.mcp.handlers;

import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Abstract base class for read-only MCP tool handlers.
 * Provides automatic temporary session management for handlers that don't require
 * a pre-existing session.
 *
 * <p>This class handles the common pattern:
 * <ol>
 *   <li>Resolve system configuration</li>
 *   <li>Create a temporary session if none provided</li>
 *   <li>Execute the read operation</li>
 *   <li>Clean up the temporary session</li>
 * </ol>
 *
 * <p>Subclasses should use {@link #withTempSession} to execute operations that may
 * need a temporary session, or override {@link #doHandle} for full control.
 */
@Slf4j
public abstract class AbstractReadHandler extends AbstractToolHandler {

    /**
     * Functional interface for operations that execute within a session context.
     *
     * @param <T> Return type of the operation
     */
    @FunctionalInterface
    protected interface SessionOperation<T> {
        /**
         * Execute the operation within the session context.
         *
         * @param sessionId The session ID to use (either provided or temporary)
         * @param resolved The resolved system configuration
         * @return The operation result
         * @throws Exception if the operation fails
         */
        T execute(String sessionId, ResolvedSystem resolved) throws Exception;
    }

    /**
     * Execute an operation with automatic temporary session management.
     * If sessionId is null, creates a temporary session for the duration of the operation.
     *
     * @param args Request arguments (should contain optional "session_id" and "system_id")
     * @param operation The operation to execute
     * @param <T> Return type of the operation
     * @return The operation result
     * @throws Exception if the operation fails
     */
    protected <T> T withTempSession(Map<String, Object> args, SessionOperation<T> operation) throws Exception {
        String sessionId = optionalString(args, "session_id");
        String systemId = optionalString(args, "system_id");

        return withTempSession(sessionId, systemId, operation);
    }

    /**
     * Execute an operation with automatic temporary session management.
     * If sessionId is null, creates a temporary session for the duration of the operation.
     *
     * @param sessionId Provided session ID (may be null)
     * @param systemId Provided system ID (may be null to use default)
     * @param operation The operation to execute
     * @param <T> Return type of the operation
     * @return The operation result
     * @throws Exception if the operation fails
     */
    protected <T> T withTempSession(String sessionId, String systemId, SessionOperation<T> operation) throws Exception {
        String tempSessionId = null;

        try {
            // Resolve system configuration
            ResolvedSystem resolved = systemConfigLoader.getSystem(systemId);

            // Create temp session if not provided
            if (sessionId == null) {
                CreateSessionRequest sessionRequest = CreateSessionRequest.fromResolvedSystem(resolved);
                tempSessionId = jcoSessionManager.createSession(sessionRequest);
                sessionId = tempSessionId;
                log.debug("Created temp session: {}", tempSessionId);
            }

            // Execute the operation
            return operation.execute(sessionId, resolved);

        } finally {
            // Clean up temp session
            if (tempSessionId != null) {
                destroyTempSession(tempSessionId);
            }
        }
    }

    /**
     * Safely destroy a temporary session.
     *
     * @param tempSessionId The temporary session ID to destroy
     */
    protected void destroyTempSession(String tempSessionId) {
        try {
            jcoSessionManager.destroySession(tempSessionId);
            log.debug("Destroyed temp session: {}", tempSessionId);
        } catch (Exception e) {
            log.warn("Failed to destroy temp session {}: {}", tempSessionId, e.getMessage());
        }
    }

    /**
     * Get the JcoSession object for a session ID.
     * Useful when you need session details like context ID or HTTP client.
     *
     * @param sessionId Session ID
     * @return The JcoSession object
     */
    protected JcoSession getSession(String sessionId) {
        return jcoSessionManager.getSession(sessionId);
    }

    /**
     * Execute a JCo operation within the session context.
     * Wraps {@link com.sapjco.mcp.service.JcoSessionManager#executeInContext}.
     *
     * @param sessionId Session ID
     * @param operation The JCo operation to execute
     * @param <T> Return type
     * @return The operation result
     * @throws Exception if the operation fails
     */
    protected <T> T executeInContext(String sessionId,
                                      com.sapjco.mcp.service.JcoSessionManager.JcoOperation<T> operation)
            throws Exception {
        return jcoSessionManager.executeInContext(sessionId, operation);
    }

    /**
     * Helper to handle common read handler flow with error handling.
     * Creates a standardized error response on exception.
     *
     * @param args Request arguments
     * @param objectName Object name for logging
     * @param operation The operation to execute
     * @return CallToolResult from operation or error result on exception
     */
    protected CallToolResult executeWithErrorHandling(Map<String, Object> args, String objectName,
                                                       SessionOperation<CallToolResult> operation) {
        try {
            return withTempSession(args, operation);
        } catch (IllegalArgumentException e) {
            log.error("Validation error for {}: {}", objectName, e.getMessage());
            return error(e.getMessage());
        } catch (Exception e) {
            log.error("Operation failed for {}", objectName, e);
            return error(e);
        }
    }
}
