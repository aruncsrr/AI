package com.sapjco.mcp.controller;

import com.sapjco.mcp.model.*;
import com.sapjco.mcp.service.AdtClient;
import com.sapjco.mcp.service.JcoSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for JCo operations.
 *
 * Exposes endpoints that Node.js MCP server can call to execute
 * ADT operations within stateful JCo sessions.
 */
@Slf4j
@RestController
@RequestMapping("/jco")
@RequiredArgsConstructor
public class JcoController {

    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    /**
     * Create a new JCo session.
     *
     * POST /jco/session/create
     *
     * For multi-system support, the request body can optionally include:
     * - systemId: System identifier (e.g., "dev", "prod")
     * - host, sysnr, client, username, password: SAP connection details
     *
     * If no system config is provided, uses the default system from environment.
     *
     * @param request Optional request body with system configuration
     * @return Session ID
     */
    @PostMapping("/session/create")
    public ResponseEntity<SessionResponse> createSession(@RequestBody(required = false) CreateSessionRequest request) {
        try {
            String systemInfo = (request != null && request.hasSystemConfig())
                ? request.getSystemId()
                : "default";
            log.info("Received request to create JCo session for system: {}", systemInfo);

            String sessionId = sessionManager.createSession(request);

            log.info("✓ Session created: {} (system: {})", sessionId, systemInfo);
            return ResponseEntity.ok(new SessionResponse(sessionId));

        } catch (Exception e) {
            log.error("Failed to create session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    /**
     * Destroy a JCo session.
     *
     * DELETE /jco/session/{sessionId}
     *
     * @param sessionId Session ID to destroy
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> destroySession(@PathVariable String sessionId) {
        try {
            log.info("Received request to destroy session: {}", sessionId);

            sessionManager.destroySession(sessionId);

            log.info("✓ Session destroyed: {}", sessionId);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", sessionId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Failed to destroy session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Lock an ABAP object.
     *
     * POST /jco/lock
     *
     * @param request Lock request
     * @return Lock handle and transport number
     */
    @PostMapping("/lock")
    public ResponseEntity<LockResponse> lockObject(@RequestBody LockRequest request) {
        try {
            log.info("Received lock request - Session: {}, Object: {} ({})",
                     request.getSessionId(), request.getObjectName(), request.getObjectType());

            LockResponse response = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.lockObject(
                    destination,
                    request.getObjectName(),
                    request.getObjectType(),
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session  // NEW - pass full session object
                )
            );

            log.info("✓ Lock successful - Handle: {}", response.getLockHandle());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Lock failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Save ABAP object source code.
     *
     * PUT /jco/save
     *
     * @param request Save request
     * @deprecated Use /jco/save-class for atomic lock+save+unlock operation
     */
    @Deprecated
    @PutMapping("/save")
    public ResponseEntity<?> saveObject(@RequestBody SaveRequest request) {
        try {
            log.info("Received save request - Session: {}, Object: {} ({}), Lock: {}",
                     request.getSessionId(), request.getObjectName(),
                     request.getObjectType(), request.getLockHandle());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    adtClient.saveObject(
                        destination,
                        request.getObjectName(),
                        request.getObjectType(),
                        request.getSourceCode(),
                        request.getLockHandle(),
                        request.getTransportNumber(),
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session  // NEW - pass full session object
                    );
                    return null;
                }
            );

            log.info("✓ Save successful");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            ErrorResponse error = new ErrorResponse("SESSION_NOT_FOUND",
                "Session not found: " + request.getSessionId(), null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            log.error("Save failed", e);
            ErrorResponse error = new ErrorResponse("SAVE_FAILED",
                e.getMessage(),
                e.getCause() != null ? e.getCause().getMessage() : null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Save ABAP class source code (atomic operation).
     *
     * This performs lock, save, and unlock in a single atomic operation
     * using the same HTTP client to maintain session state.
     *
     * POST /jco/save-class
     *
     * @param request Save class request
     */
    @PostMapping("/save-class")
    public ResponseEntity<?> saveClass(@RequestBody SaveClassRequest request) {
        try {
            log.info("Atomic save-class - Session: {}, Class: {}",
                     request.getSessionId(), request.getClassName());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    String lockHandle = null;
                    try {
                        // Step 1: Lock (same HTTP client)
                        log.debug("Locking class: {}", request.getClassName());
                        LockResponse lock = adtClient.lockObject(
                            destination,
                            request.getClassName(),
                            "CLASS",
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session  // NEW - pass full session object
                        );
                        lockHandle = lock.getLockHandle();
                        log.debug("✓ Locked with handle: {}", lockHandle);

                        // Step 2: Save (SAME HTTP client = same session!)
                        // Use user-provided transport if specified, otherwise fall back to lock response
                        String transportToUse = request.getTransportNumber();
                        if (transportToUse == null || transportToUse.isEmpty()) {
                            transportToUse = lock.getTransportNumber();
                        }
                        log.debug("Saving source code with transport: {}", transportToUse);
                        adtClient.saveObject(
                            destination,
                            request.getClassName(),
                            "CLASS",
                            request.getSourceCode(),
                            lockHandle,
                            transportToUse,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session  // NEW - pass full session object
                        );
                        log.debug("✓ Saved successfully");

                        // Step 3: Unlock (SAME HTTP client)
                        log.debug("Unlocking...");
                        adtClient.unlockObject(
                            destination,
                            request.getClassName(),
                            "CLASS",
                            lockHandle,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session  // NEW - pass full session object
                        );
                        log.debug("✓ Unlocked");

                        return null;

                    } catch (Exception e) {
                        // If we got a lock, try to release it
                        if (lockHandle != null) {
                            try {
                                log.warn("Attempting to release lock after error");
                                adtClient.unlockObject(
                                    destination,
                                    request.getClassName(),
                                    "CLASS",
                                    lockHandle,
                                    session.getHttpClient(),
                                    session.getCsrfTokenCache(),
                                    session  // NEW - pass full session object
                                );
                                log.warn("✓ Released lock after error");
                            } catch (Exception unlockError) {
                                log.error("Failed to unlock after error", unlockError);
                            }
                        }
                        throw e;
                    }
                }
            );

            log.info("✓ Atomic save-class successful");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SESSION_NOT_FOUND", e.getMessage(), null));

        } catch (Exception e) {
            log.error("Atomic save-class failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SAVE_FAILED", e.getMessage(),
                      e.getCause() != null ? e.getCause().getMessage() : null));
        }
    }

    /**
     * Save ABAP program source code (atomic operation).
     *
     * This performs lock, save, and unlock in a single atomic operation
     * using the same HTTP client to maintain session state.
     *
     * POST /jco/save-program
     *
     * @param request Save program request
     */
    @PostMapping("/save-program")
    public ResponseEntity<?> saveProgram(@RequestBody SaveProgramRequest request) {
        try {
            log.info("Atomic save-program - Session: {}, Program: {}",
                     request.getSessionId(), request.getProgramName());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    String lockHandle = null;
                    try {
                        // Step 1: Lock (same HTTP client)
                        log.debug("Locking program: {}", request.getProgramName());
                        LockResponse lock = adtClient.lockObject(
                            destination,
                            request.getProgramName(),
                            "program",
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        lockHandle = lock.getLockHandle();
                        log.debug("Locked with handle: {}", lockHandle);

                        // Step 2: Save (SAME HTTP client = same session!)
                        log.debug("Saving source code...");
                        adtClient.saveObject(
                            destination,
                            request.getProgramName(),
                            "program",
                            request.getSourceCode(),
                            lockHandle,
                            lock.getTransportNumber(),
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("Saved successfully");

                        // Step 3: Unlock (SAME HTTP client)
                        log.debug("Unlocking...");
                        adtClient.unlockObject(
                            destination,
                            request.getProgramName(),
                            "program",
                            lockHandle,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("Unlocked");

                        return null;

                    } catch (Exception e) {
                        // If we got a lock, try to release it
                        if (lockHandle != null) {
                            try {
                                log.warn("Attempting to release lock after error");
                                adtClient.unlockObject(
                                    destination,
                                    request.getProgramName(),
                                    "program",
                                    lockHandle,
                                    session.getHttpClient(),
                                    session.getCsrfTokenCache(),
                                    session
                                );
                                log.warn("Released lock after error");
                            } catch (Exception unlockError) {
                                log.error("Failed to unlock after error", unlockError);
                            }
                        }
                        throw e;
                    }
                }
            );

            log.info("Atomic save-program successful");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_ARGUMENT", e.getMessage(), null));

        } catch (Exception e) {
            log.error("Atomic save-program failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SAVE_FAILED", e.getMessage(),
                      e.getCause() != null ? e.getCause().getMessage() : null));
        }
    }

    /**
     * Save ABAP class test include source code (atomic operation).
     *
     * This performs lock, save to testClasses include, and unlock in a single atomic operation
     * using the same HTTP client to maintain session state.
     *
     * POST /jco/save-class-test-include
     *
     * @param request Save class test include request
     */
    @PostMapping("/save-class-test-include")
    public ResponseEntity<?> saveClassTestInclude(@RequestBody SaveClassTestIncludeRequest request) {
        try {
            log.info("Atomic save-class-test-include - Session: {}, Class: {}",
                     request.getSessionId(), request.getClassName());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    String lockHandle = null;
                    try {
                        // Step 1: Lock the class (same HTTP client)
                        log.debug("Locking class: {}", request.getClassName());
                        LockResponse lock = adtClient.lockObject(
                            destination,
                            request.getClassName(),
                            "CLASS",
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        lockHandle = lock.getLockHandle();
                        log.debug("Locked with handle: {}", lockHandle);

                        // Step 2: Save test include (SAME HTTP client = same session!)
                        // Use user-provided transport if specified, otherwise fall back to lock response
                        String transportToUse = request.getTransportNumber();
                        if (transportToUse == null || transportToUse.isEmpty()) {
                            transportToUse = lock.getTransportNumber();
                        }
                        log.debug("Saving test include source code with transport: {}", transportToUse);
                        adtClient.saveClassTestInclude(
                            destination,
                            request.getClassName(),
                            request.getSourceCode(),
                            lockHandle,
                            transportToUse,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("Saved test include successfully");

                        // Step 3: Unlock (SAME HTTP client)
                        log.debug("Unlocking...");
                        adtClient.unlockObject(
                            destination,
                            request.getClassName(),
                            "CLASS",
                            lockHandle,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("Unlocked");

                        return null;

                    } catch (Exception e) {
                        // If we got a lock, try to release it
                        if (lockHandle != null) {
                            try {
                                log.warn("Attempting to release lock after error");
                                adtClient.unlockObject(
                                    destination,
                                    request.getClassName(),
                                    "CLASS",
                                    lockHandle,
                                    session.getHttpClient(),
                                    session.getCsrfTokenCache(),
                                    session
                                );
                                log.warn("Released lock after error");
                            } catch (Exception unlockError) {
                                log.error("Failed to unlock after error", unlockError);
                            }
                        }
                        throw e;
                    }
                }
            );

            log.info("Atomic save-class-test-include successful");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SESSION_NOT_FOUND", e.getMessage(), null));

        } catch (Exception e) {
            log.error("Atomic save-class-test-include failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SAVE_FAILED", e.getMessage(),
                      e.getCause() != null ? e.getCause().getMessage() : null));
        }
    }

    /**
     * Save ABAP class include (generic for all include types).
     * Atomic operation: lock class → save include → unlock class.
     * Supports: definitions, implementations, macros, testClasses
     *
     * @param request Save class include request
     */
    @PostMapping("/save-class-include")
    public ResponseEntity<?> saveClassInclude(@RequestBody SaveClassIncludeRequest request) {
        try {
            // Validate include_type
            String[] validTypes = {"definitions", "implementations", "macros", "testClasses"};
            if (!java.util.Arrays.asList(validTypes).contains(request.getIncludeType())) {
                log.error("Invalid include_type: {}", request.getIncludeType());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("INVALID_INCLUDE_TYPE",
                          "include_type must be one of: definitions, implementations, macros, testClasses",
                          "Received: " + request.getIncludeType()));
            }

            log.info("Atomic save-class-include - Session: {}, Class: {}, Type: {}",
                     request.getSessionId(), request.getClassName(), request.getIncludeType());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    String lockHandle = null;
                    try {
                        // Step 1: Lock the class (same HTTP client)
                        log.debug("Locking class: {}", request.getClassName());
                        LockResponse lock = adtClient.lockObject(
                            destination,
                            request.getClassName(),
                            "CLASS",
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        lockHandle = lock.getLockHandle();
                        log.debug("Locked with handle: {}", lockHandle);

                        // Step 2: Save class include (SAME HTTP client = same session!)
                        // Use user-provided transport if specified, otherwise fall back to lock response
                        String transportToUse = request.getTransportNumber();
                        if (transportToUse == null || transportToUse.isEmpty()) {
                            transportToUse = lock.getTransportNumber();
                        }
                        log.debug("Saving class include (type: {}) with transport: {}",
                                  request.getIncludeType(), transportToUse);
                        adtClient.saveClassInclude(
                            destination,
                            request.getClassName(),
                            request.getIncludeType(),
                            request.getSourceCode(),
                            lockHandle,
                            transportToUse,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("Saved class include successfully (type: {})", request.getIncludeType());

                        // Step 3: Unlock (SAME HTTP client)
                        log.debug("Unlocking...");
                        adtClient.unlockObject(
                            destination,
                            request.getClassName(),
                            "CLASS",
                            lockHandle,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("Unlocked");

                        return null;

                    } catch (Exception e) {
                        // If we got a lock, try to release it
                        if (lockHandle != null) {
                            try {
                                log.warn("Attempting to release lock after error");
                                adtClient.unlockObject(
                                    destination,
                                    request.getClassName(),
                                    "CLASS",
                                    lockHandle,
                                    session.getHttpClient(),
                                    session.getCsrfTokenCache(),
                                    session
                                );
                                log.warn("Released lock after error");
                            } catch (Exception unlockError) {
                                log.error("Failed to unlock after error", unlockError);
                            }
                        }
                        throw e;
                    }
                }
            );

            log.info("Atomic save-class-include successful (type: {})", request.getIncludeType());
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SESSION_NOT_FOUND", e.getMessage(), null));

        } catch (Exception e) {
            log.error("Atomic save-class-include failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SAVE_FAILED", e.getMessage(),
                      e.getCause() != null ? e.getCause().getMessage() : null));
        }
    }

    /**
     * Save ABAP interface source code (atomic operation).
     *
     * This performs lock, save, and unlock in a single atomic operation
     * using the same HTTP client to maintain session state.
     *
     * POST /jco/save-interface
     *
     * @param request Save interface request
     */
    @PostMapping("/save-interface")
    public ResponseEntity<?> saveInterface(@RequestBody SaveInterfaceRequest request) {
        try {
            log.info("Atomic save-interface - Session: {}, Interface: {}",
                     request.getSessionId(), request.getInterfaceName());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    String lockHandle = null;
                    try {
                        // Step 1: Lock (same HTTP client)
                        log.debug("Locking interface: {}", request.getInterfaceName());
                        LockResponse lock = adtClient.lockObject(
                            destination,
                            request.getInterfaceName(),
                            "INTERFACE",
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        lockHandle = lock.getLockHandle();
                        log.debug("✓ Locked with handle: {}", lockHandle);

                        // Step 2: Save (SAME HTTP client = same session!)
                        // Use user-provided transport if specified, otherwise fall back to lock response
                        String transportToUse = request.getTransportNumber();
                        if (transportToUse == null || transportToUse.isEmpty()) {
                            transportToUse = lock.getTransportNumber();
                        }
                        log.debug("Saving source code with transport: {}", transportToUse);
                        adtClient.saveObject(
                            destination,
                            request.getInterfaceName(),
                            "INTERFACE",
                            request.getSourceCode(),
                            lockHandle,
                            transportToUse,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("✓ Saved successfully");

                        // Step 3: Unlock (SAME HTTP client)
                        log.debug("Unlocking...");
                        adtClient.unlockObject(
                            destination,
                            request.getInterfaceName(),
                            "INTERFACE",
                            lockHandle,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("✓ Unlocked");

                        return null;

                    } catch (Exception e) {
                        // If we got a lock, try to release it
                        if (lockHandle != null) {
                            try {
                                log.warn("Attempting to release lock after error");
                                adtClient.unlockObject(
                                    destination,
                                    request.getInterfaceName(),
                                    "INTERFACE",
                                    lockHandle,
                                    session.getHttpClient(),
                                    session.getCsrfTokenCache(),
                                    session
                                );
                                log.warn("✓ Released lock after error");
                            } catch (Exception unlockError) {
                                log.error("Failed to unlock after error", unlockError);
                            }
                        }
                        throw e;
                    }
                }
            );

            log.info("✓ Atomic save-interface successful");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SESSION_NOT_FOUND", e.getMessage(), null));

        } catch (Exception e) {
            log.error("Atomic save-interface failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SAVE_FAILED", e.getMessage(),
                      e.getCause() != null ? e.getCause().getMessage() : null));
        }
    }

    /**
     * Save ABAP function group source code (atomic operation).
     *
     * This performs lock, save, and unlock in a single atomic operation
     * using the same HTTP client to maintain session state.
     *
     * POST /jco/save-function-group
     *
     * @param request Save function group request
     */
    @PostMapping("/save-function-group")
    public ResponseEntity<?> saveFunctionGroup(@RequestBody SaveFunctionGroupRequest request) {
        try {
            log.info("Atomic save-function-group - Session: {}, FunctionGroup: {}",
                     request.getSessionId(), request.getGroupName());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    String lockHandle = null;
                    try {
                        // Step 1: Lock (same HTTP client)
                        log.debug("Locking function group: {}", request.getGroupName());
                        LockResponse lock = adtClient.lockObject(
                            destination,
                            request.getGroupName(),
                            "FUNCTION_GROUP",
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        lockHandle = lock.getLockHandle();
                        log.debug("✓ Locked with handle: {}", lockHandle);

                        // Step 2: Save (SAME HTTP client = same session!)
                        // Use user-provided transport if specified, otherwise fall back to lock response
                        String transportToUse = request.getTransportNumber();
                        if (transportToUse == null || transportToUse.isEmpty()) {
                            transportToUse = lock.getTransportNumber();
                        }
                        log.debug("Saving source code with transport: {}", transportToUse);
                        adtClient.saveObject(
                            destination,
                            request.getGroupName(),
                            "FUNCTION_GROUP",
                            request.getSourceCode(),
                            lockHandle,
                            transportToUse,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("✓ Saved successfully");

                        // Step 3: Unlock (SAME HTTP client)
                        log.debug("Unlocking...");
                        adtClient.unlockObject(
                            destination,
                            request.getGroupName(),
                            "FUNCTION_GROUP",
                            lockHandle,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("✓ Unlocked");

                        return null;

                    } catch (Exception e) {
                        // If we got a lock, try to release it
                        if (lockHandle != null) {
                            try {
                                log.warn("Attempting to release lock after error");
                                adtClient.unlockObject(
                                    destination,
                                    request.getGroupName(),
                                    "FUNCTION_GROUP",
                                    lockHandle,
                                    session.getHttpClient(),
                                    session.getCsrfTokenCache(),
                                    session
                                );
                                log.warn("✓ Released lock after error");
                            } catch (Exception unlockError) {
                                log.error("Failed to unlock after error", unlockError);
                            }
                        }
                        throw e;
                    }
                }
            );

            log.info("✓ Atomic save-function-group successful");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SESSION_NOT_FOUND", e.getMessage(), null));

        } catch (Exception e) {
            log.error("Atomic save-function-group failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SAVE_FAILED", e.getMessage(),
                      e.getCause() != null ? e.getCause().getMessage() : null));
        }
    }

    /**
     * Save ABAP include program source code (atomic operation).
     *
     * This performs lock, save, and unlock in a single atomic operation
     * using the same HTTP client to maintain session state.
     *
     * POST /jco/save-include
     *
     * @param request Save include request
     */
    @PostMapping("/save-include")
    public ResponseEntity<?> saveInclude(@RequestBody SaveIncludeRequest request) {
        try {
            log.info("Atomic save-include - Session: {}, Include: {}",
                     request.getSessionId(), request.getIncludeName());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    String lockHandle = null;
                    try {
                        // Step 1: Lock (same HTTP client)
                        log.debug("Locking include: {}", request.getIncludeName());
                        LockResponse lock = adtClient.lockObject(
                            destination,
                            request.getIncludeName(),
                            "INCLUDE",
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        lockHandle = lock.getLockHandle();
                        log.debug("✓ Locked with handle: {}", lockHandle);

                        // Step 2: Save (SAME HTTP client = same session!)
                        // Use user-provided transport if specified, otherwise fall back to lock response
                        String transportToUse = request.getTransportNumber();
                        if (transportToUse == null || transportToUse.isEmpty()) {
                            transportToUse = lock.getTransportNumber();
                        }
                        log.debug("Saving source code with transport: {}", transportToUse);
                        adtClient.saveObject(
                            destination,
                            request.getIncludeName(),
                            "INCLUDE",
                            request.getSourceCode(),
                            lockHandle,
                            transportToUse,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("✓ Saved successfully");

                        // Step 3: Unlock (SAME HTTP client)
                        log.debug("Unlocking...");
                        adtClient.unlockObject(
                            destination,
                            request.getIncludeName(),
                            "INCLUDE",
                            lockHandle,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                        log.debug("✓ Unlocked");

                        return null;

                    } catch (Exception e) {
                        // If we got a lock, try to release it
                        if (lockHandle != null) {
                            try {
                                log.warn("Attempting to release lock after error");
                                adtClient.unlockObject(
                                    destination,
                                    request.getIncludeName(),
                                    "INCLUDE",
                                    lockHandle,
                                    session.getHttpClient(),
                                    session.getCsrfTokenCache(),
                                    session
                                );
                                log.warn("✓ Released lock after error");
                            } catch (Exception unlockError) {
                                log.error("Failed to unlock after error", unlockError);
                            }
                        }
                        throw e;
                    }
                }
            );

            log.info("✓ Atomic save-include successful");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SESSION_NOT_FOUND", e.getMessage(), null));

        } catch (Exception e) {
            log.error("Atomic save-include failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SAVE_FAILED", e.getMessage(),
                      e.getCause() != null ? e.getCause().getMessage() : null));
        }
    }

    /**
     * Unlock an ABAP object.
     *
     * DELETE /jco/lock
     *
     * @param request Unlock request
     */
    @DeleteMapping("/lock")
    public ResponseEntity<Void> unlockObject(@RequestBody UnlockRequest request) {
        try {
            log.info("Received unlock request - Session: {}, Object: {} ({}), Lock: {}",
                     request.getSessionId(), request.getObjectName(),
                     request.getObjectType(), request.getLockHandle());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    adtClient.unlockObject(
                        destination,
                        request.getObjectName(),
                        request.getObjectType(),
                        request.getLockHandle(),
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session  // NEW - pass full session object
                    );
                    return null;
                }
            );

            log.info("✓ Unlock successful");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Unlock failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Activate an ABAP object.
     *
     * POST /jco/activate
     *
     * @param request Activation request
     */
    @PostMapping("/activate")
    public ResponseEntity<Void> activateObject(@RequestBody ActivateRequest request) {
        try {
            log.info("Received activation request - Session: {}, Object: {} ({})",
                     request.getSessionId(), request.getObjectName(), request.getObjectType());

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    adtClient.activateObject(
                        destination,
                        request.getObjectName(),
                        request.getObjectType(),
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session  // NEW - pass full session object
                    );
                    return null;
                }
            );

            log.info("✓ Activation successful");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Activation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get active session count (for monitoring).
     *
     * GET /jco/sessions/count
     */
    @GetMapping("/sessions/count")
    public ResponseEntity<Integer> getSessionCount() {
        int count = sessionManager.getActiveSessionCount();
        return ResponseEntity.ok(count);
    }

    /**
     * Route HTTP requests through the session's SSO-enabled HTTP client.
     *
     * <p>This endpoint enables the Node.js MCP server to route ADT HTTP requests
     * through the Java service, leveraging SSO authentication via OS keystore
     * certificates (macOS Keychain or Windows Certificate Store) that are only
     * accessible from Java.</p>
     *
     * <p>The proxy uses the session's stored HTTP client which has already been
     * configured with appropriate authentication (basic auth, SNC, or X.509).</p>
     *
     * POST /jco/http-proxy
     *
     * @param request HTTP proxy request containing session ID, method, path, headers, body
     * @return Full HTTP response with status code, headers, and body
     */
    @PostMapping("/http-proxy")
    public ResponseEntity<HttpProxyResponse> httpProxy(@RequestBody HttpProxyRequest request) {
        try {
            log.info("HTTP proxy request - Session: {}, Method: {}, Path: {}",
                     request.getSessionId(), request.getMethod(), request.getPath());

            HttpProxyResponse response = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.executeHttpRequest(
                    destination,
                    request.getMethod(),
                    request.getPath(),
                    request.getQueryParams(),
                    request.getHeaders(),
                    request.getBody(),
                    request.getTimeoutMs(),
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
                )
            );

            log.info("HTTP proxy response - Status: {}", response.getStatusCode());
            return ResponseEntity.status(response.getStatusCode()).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(HttpProxyResponse.error("Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            log.error("HTTP proxy request failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(HttpProxyResponse.error("Proxy request failed: " + e.getMessage()));
        }
    }
}
