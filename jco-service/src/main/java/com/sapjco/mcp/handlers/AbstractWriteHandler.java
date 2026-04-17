package com.sapjco.mcp.handlers;

import com.sapjco.mcp.config.SystemConfigLoader.ResolvedSystem;
import com.sapjco.mcp.model.JcoSession;
import com.sapjco.mcp.model.LockResponse;
import com.sapjco.mcp.util.AdtUrlBuilder;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Abstract base class for write MCP tool handlers.
 * Provides atomic lock/save/unlock pattern for ABAP object modifications.
 *
 * <p>This class handles the common pattern:
 * <ol>
 *   <li>Validate session exists</li>
 *   <li>Lock the object</li>
 *   <li>Save the source code</li>
 *   <li>Unlock the object (even on error)</li>
 * </ol>
 *
 * <p>Subclasses should use {@link #atomicSave} for standard save operations.
 */
@Slf4j
public abstract class AbstractWriteHandler extends AbstractToolHandler {

    /**
     * Functional interface for save operations within a locked context.
     */
    @FunctionalInterface
    protected interface SaveOperation {
        /**
         * Execute the save operation while the object is locked.
         *
         * @param lockHandle Lock handle from the lock operation
         * @param transportNumber Transport number to use (from user or lock response)
         * @return Raw response from the save operation
         * @throws Exception if the save fails
         */
        String execute(String lockHandle, String transportNumber) throws Exception;
    }

    /**
     * Execute an atomic lock/save/unlock operation.
     * Ensures the object is always unlocked, even if the save fails.
     *
     * @param sessionId Session ID (required)
     * @param objectName Object name
     * @param objectType Object type (CLASS, INTERFACE, etc.)
     * @param transportNumber Transport number (optional, uses lock response if null)
     * @param saveOperation The save operation to execute
     * @return The raw response from the save operation
     * @throws Exception if any step fails
     */
    protected String atomicSave(String sessionId, String objectName, String objectType,
                                String transportNumber, SaveOperation saveOperation) throws Exception {

        return jcoSessionManager.executeInContext(sessionId, (destination, session) -> {
            String lockHandle = null;
            try {
                // Step 1: Lock the object
                log.debug("Locking {}: {}", objectType, objectName);
                LockResponse lock = adtClient.lockObject(
                        destination,
                        objectName,
                        objectType,
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                );
                lockHandle = lock.getLockHandle();
                log.debug("Locked with handle: {}", lockHandle);

                // Step 2: Determine transport to use
                String transportToUse = transportNumber;
                if (transportToUse == null || transportToUse.isEmpty()) {
                    transportToUse = lock.getTransportNumber();
                }
                log.debug("Using transport: {}", transportToUse);

                // Step 3: Execute the save operation
                String response = saveOperation.execute(lockHandle, transportToUse);
                log.debug("Saved successfully");

                // Step 4: Unlock
                log.debug("Unlocking...");
                adtClient.unlockObject(
                        destination,
                        objectName,
                        objectType,
                        lockHandle,
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                );
                log.debug("Unlocked");

                return response;

            } catch (Exception e) {
                // If we got a lock, try to release it
                if (lockHandle != null) {
                    tryUnlock(session, objectName, objectType, lockHandle);
                }
                throw e;
            }
        });
    }

    /**
     * Execute a standard save operation with the common lock/save/unlock pattern.
     *
     * @param sessionId Session ID (required)
     * @param objectName Object name
     * @param objectType Object type (CLASS, INTERFACE, etc.)
     * @param sourceCode Source code to save
     * @param transportNumber Transport number (optional)
     * @return The raw response from the save operation
     * @throws Exception if any step fails
     */
    protected String atomicSaveObject(String sessionId, String objectName, String objectType,
                                       String sourceCode, String transportNumber) throws Exception {
        return atomicSave(sessionId, objectName, objectType, transportNumber,
                (lockHandle, transport) -> {
                    JcoSession session = jcoSessionManager.getSession(sessionId);
                    return adtClient.saveObject(
                            jcoSessionManager.getDestination(sessionId),
                            objectName,
                            objectType,
                            sourceCode,
                            lockHandle,
                            transport,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                    );
                });
    }

    /**
     * Execute a save class include operation with the common lock/save/unlock pattern.
     * Locks the class, saves the include, and unlocks.
     *
     * @param sessionId Session ID (required)
     * @param className Class name
     * @param includeType Include type (definitions, implementations, macros, testClasses)
     * @param sourceCode Source code to save
     * @param transportNumber Transport number (optional)
     * @return The raw response from the save operation
     * @throws Exception if any step fails
     */
    protected String atomicSaveClassInclude(String sessionId, String className, String includeType,
                                             String sourceCode, String transportNumber) throws Exception {
        return atomicSave(sessionId, className, "CLASS", transportNumber,
                (lockHandle, transport) -> {
                    JcoSession session = jcoSessionManager.getSession(sessionId);
                    return adtClient.saveClassInclude(
                            jcoSessionManager.getDestination(sessionId),
                            className,
                            includeType,
                            sourceCode,
                            lockHandle,
                            transport,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                    );
                });
    }

    /**
     * Execute a save class test include operation with the common lock/save/unlock pattern.
     * Locks the class, saves the test include, and unlocks.
     *
     * @param sessionId Session ID (required)
     * @param className Class name
     * @param sourceCode Source code to save
     * @param transportNumber Transport number (optional)
     * @return The raw response from the save operation
     * @throws Exception if any step fails
     */
    protected String atomicSaveClassTestInclude(String sessionId, String className,
                                                 String sourceCode, String transportNumber) throws Exception {
        return atomicSave(sessionId, className, "CLASS", transportNumber,
                (lockHandle, transport) -> {
                    JcoSession session = jcoSessionManager.getSession(sessionId);
                    return adtClient.saveClassTestInclude(
                            jcoSessionManager.getDestination(sessionId),
                            className,
                            sourceCode,
                            lockHandle,
                            transport,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                    );
                });
    }

    /**
     * Execute a save function module operation with the common lock/save/unlock pattern.
     * Locks the function module directly (not the function group parent) to match
     * the resource that the save operation writes to.
     *
     * @param sessionId Session ID (required)
     * @param groupName Function group name
     * @param moduleName Function module name
     * @param sourceCode Source code to save
     * @param transportNumber Transport number (optional)
     * @return The raw response from the save operation
     * @throws Exception if any step fails
     */
    protected String atomicSaveFunctionModule(String sessionId, String groupName, String moduleName,
                                               String sourceCode, String transportNumber) throws Exception {

        String fmLockUri = AdtUrlBuilder.buildFunctionModuleUrl(groupName, moduleName);
        String displayName = groupName + "." + moduleName;

        return jcoSessionManager.executeInContext(sessionId, (destination, session) -> {
            String lockHandle = null;
            try {
                // Step 1: Lock the function module directly (not the function group)
                log.debug("Locking function module: {} at {}", displayName, fmLockUri);
                LockResponse lock = adtClient.lockObjectByUri(
                        destination,
                        displayName,
                        fmLockUri,
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                );
                lockHandle = lock.getLockHandle();
                log.debug("Locked with handle: {}", lockHandle);

                // Step 2: Determine transport to use
                String transportToUse = transportNumber;
                if (transportToUse == null || transportToUse.isEmpty()) {
                    transportToUse = lock.getTransportNumber();
                }
                log.debug("Using transport: {}", transportToUse);

                // Step 3: Save the function module source
                String response = adtClient.saveFunctionModule(
                        destination,
                        groupName,
                        moduleName,
                        sourceCode,
                        lockHandle,
                        transportToUse,
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                );
                log.debug("Saved successfully");

                // Step 4: Unlock the function module
                log.debug("Unlocking...");
                adtClient.unlockObjectByUri(
                        destination,
                        displayName,
                        fmLockUri,
                        lockHandle,
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                );
                log.debug("Unlocked");

                return response;

            } catch (Exception e) {
                // If we got a lock, try to release it
                if (lockHandle != null) {
                    tryUnlockByUri(session, displayName, fmLockUri, lockHandle);
                }
                throw e;
            }
        });
    }

    /**
     * Execute a save function group include operation with the common lock/save/unlock pattern.
     * Locks the include directly (by URI) and saves to the include source path.
     *
     * @param sessionId Session ID (required)
     * @param groupName Function group name
     * @param includeName Include name (e.g., "LZFG_TESTF01")
     * @param sourceCode Source code to save
     * @param transportNumber Transport number (optional)
     * @return The raw response from the save operation
     * @throws Exception if any step fails
     */
    protected String atomicSaveFunctionGroupInclude(String sessionId, String groupName, String includeName,
                                                     String sourceCode, String transportNumber) throws Exception {

        String lockUri = AdtUrlBuilder.buildFunctionGroupIncludeUrl(groupName, includeName);
        String displayName = groupName + "." + includeName;

        return jcoSessionManager.executeInContext(sessionId, (destination, session) -> {
            String lockHandle = null;
            try {
                // Step 1: Lock the function group include directly
                log.debug("Locking function group include: {} at {}", displayName, lockUri);
                LockResponse lock = adtClient.lockObjectByUri(
                        destination,
                        displayName,
                        lockUri,
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                );
                lockHandle = lock.getLockHandle();
                log.debug("Locked with handle: {}", lockHandle);

                // Step 2: Determine transport to use
                String transportToUse = transportNumber;
                if (transportToUse == null || transportToUse.isEmpty()) {
                    transportToUse = lock.getTransportNumber();
                }
                log.debug("Using transport: {}", transportToUse);

                // Step 3: Save the function group include source
                String response = adtClient.saveFunctionGroupInclude(
                        destination,
                        groupName,
                        includeName,
                        sourceCode,
                        lockHandle,
                        transportToUse,
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                );
                log.debug("Saved successfully");

                // Step 4: Unlock the function group include
                log.debug("Unlocking...");
                adtClient.unlockObjectByUri(
                        destination,
                        displayName,
                        lockUri,
                        lockHandle,
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                );
                log.debug("Unlocked");

                return response;

            } catch (Exception e) {
                // If we got a lock, try to release it
                if (lockHandle != null) {
                    tryUnlockByUri(session, displayName, lockUri, lockHandle);
                }
                throw e;
            }
        });
    }

    /**
     * Attempt to unlock an object, logging any errors but not throwing.
     *
     * @param session JCo session
     * @param objectName Object name
     * @param objectType Object type
     * @param lockHandle Lock handle
     */
    protected void tryUnlock(JcoSession session, String objectName, String objectType, String lockHandle) {
        try {
            log.warn("Attempting to release lock after error");
            adtClient.unlockObject(
                    null, // destination not needed for unlock via session
                    objectName,
                    objectType,
                    lockHandle,
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
            );
            log.warn("Released lock after error");
        } catch (Exception unlockError) {
            log.error("Failed to unlock after error: {}", unlockError.getMessage());
        }
    }

    /**
     * Attempt to unlock an object by URI, logging any errors but not throwing.
     *
     * @param session JCo session
     * @param objectName Object name (for logging)
     * @param lockUri Pre-built lock URI
     * @param lockHandle Lock handle
     */
    protected void tryUnlockByUri(JcoSession session, String objectName, String lockUri, String lockHandle) {
        try {
            log.warn("Attempting to release URI lock after error");
            adtClient.unlockObjectByUri(
                    null,
                    objectName,
                    lockUri,
                    lockHandle,
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
            );
            log.warn("Released URI lock after error");
        } catch (Exception unlockError) {
            log.error("Failed to unlock by URI after error: {}", unlockError.getMessage());
        }
    }

    /**
     * Read source code from either inline parameter or file.
     * Validates that exactly one is provided.
     *
     * @param sourceCode Inline source code (may be null)
     * @param sourceFile Path to source file (may be null)
     * @return The effective source code
     * @throws IOException if file cannot be read
     * @throws IllegalArgumentException if validation fails
     */
    protected String resolveSourceCode(String sourceCode, String sourceFile) throws IOException {
        boolean hasSourceCode = sourceCode != null && !sourceCode.isEmpty();
        boolean hasSourceFile = sourceFile != null && !sourceFile.isEmpty();

        if (hasSourceCode && hasSourceFile) {
            throw new IllegalArgumentException("Provide source_code OR source_file, not both");
        }
        if (!hasSourceCode && !hasSourceFile) {
            throw new IllegalArgumentException("Must provide source_code or source_file");
        }

        if (hasSourceFile) {
            String content = fileStorageService.readSource(sourceFile);
            log.info("Read source from file: {} ({} bytes)", sourceFile, content.length());
            return content;
        }

        return sourceCode;
    }

    /**
     * Get the JcoSession for a session ID with validation.
     *
     * @param sessionId Session ID (required)
     * @return The JcoSession
     * @throws IllegalArgumentException if session not found
     */
    protected JcoSession requireSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("session_id is required");
        }
        JcoSession session = jcoSessionManager.getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        return session;
    }

    /**
     * Format a successful save response.
     *
     * @param resolved The resolved system
     * @param rawResponse The raw response from the save operation
     * @return Success CallToolResult
     */
    protected CallToolResult formatSaveResponse(ResolvedSystem resolved, String rawResponse) {
        return success(resolved, rawResponse);
    }

    /**
     * Standard error handling for save operations.
     *
     * @param objectName Object name for logging
     * @param e The exception
     * @return Error CallToolResult
     */
    protected CallToolResult handleSaveError(String objectName, Exception e) {
        if (e instanceof IllegalArgumentException) {
            log.error("Validation error for {}: {}", objectName, e.getMessage());
            return error(e.getMessage());
        }
        log.error("Save failed for {}", objectName, e);
        return error(e);
    }
}
