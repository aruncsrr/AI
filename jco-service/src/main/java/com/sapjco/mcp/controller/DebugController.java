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
 * REST API controller for ABAP Debugger operations.
 *
 * These endpoints execute debug operations within stateful JCo sessions,
 * which is required for maintaining debug context across operations.
 *
 * The key insight: Debug operations after attach() MUST execute in the
 * same SAP work process context. This is achieved via JCo stateful sessions
 * with consistent sap-contextid and session cookies.
 */
@Slf4j
@RestController
@RequestMapping("/jco/debug")
@RequiredArgsConstructor
public class DebugController {

    private final JcoSessionManager sessionManager;
    private final AdtClient adtClient;

    /**
     * Attach to a debuggee session.
     *
     * POST /jco/debug/attach
     *
     * This establishes a debug session. All subsequent debug operations
     * (getStack, getVariables, step*, resume) MUST use the same session.
     *
     * @param request Attach request with debuggeeId
     * @return Attach response with debugSessionId
     */
    @PostMapping("/attach")
    public ResponseEntity<?> attach(@RequestBody DebugAttachRequest request) {
        try {
            log.info("Debug attach - Session: {}, DebuggeeId: {}, User: {}",
                     request.getSessionId(), request.getDebuggeeId(), request.getRequestUser());

            String responseXml = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    // Step 1: Attach to debuggee
                    String attachXml = adtClient.debugAttach(
                        destination,
                        request.getDebuggeeId(),
                        request.getRequestUser(),
                        request.isDynproDebugging(),
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                    );

                    // Step 2: Set debugger settings (required by Eclipse ADT)
                    try {
                        adtClient.debugSetSettings(
                            destination,
                            session.getHttpClient(),
                            session.getCsrfTokenCache(),
                            session
                        );
                    } catch (Exception e) {
                        log.warn("setDebuggerSettings failed (continuing anyway): {}", e.getMessage());
                    }

                    return attachXml;
                }
            );

            // Parse response to extract key fields
            DebugAttachResponse response = parseAttachResponse(responseXml);
            log.info("✓ Debug attach successful - DebugSessionId: {}", response.getDebugSessionId());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SESSION_NOT_FOUND", e.getMessage(), null));

        } catch (Exception e) {
            log.error("Debug attach failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("DEBUG_ATTACH_FAILED", e.getMessage(),
                      e.getCause() != null ? e.getCause().getMessage() : null));
        }
    }

    /**
     * Get the call stack.
     *
     * POST /jco/debug/stack
     *
     * @param request Operation request with sessionId
     * @return Stack XML
     */
    @PostMapping("/stack")
    public ResponseEntity<?> getStack(@RequestBody DebugOperationRequest request) {
        try {
            log.info("Debug getStack - Session: {}", request.getSessionId());

            String responseXml = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.debugGetStack(
                    destination,
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
                )
            );

            log.info("✓ Debug getStack successful");
            return ResponseEntity.ok(DebugOperationResponse.success("getStack", responseXml));

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(DebugOperationResponse.error("getStack", "Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            log.error("Debug getStack failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DebugOperationResponse.error("getStack", e.getMessage()));
        }
    }

    /**
     * Get child variables.
     *
     * POST /jco/debug/variables
     *
     * @param request Operation request with sessionId and optional parentId in requestBody
     * @return Variables XML
     */
    @PostMapping("/variables")
    public ResponseEntity<?> getVariables(@RequestBody DebugOperationRequest request) {
        try {
            // Extract parentId from request body or default to @ROOT
            String parentId = "@ROOT";
            if (request.getRequestBody() != null && !request.getRequestBody().isEmpty()) {
                parentId = request.getRequestBody();
            }

            log.info("Debug getVariables - Session: {}, ParentId: {}", request.getSessionId(), parentId);

            final String finalParentId = parentId;
            String responseXml = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.debugGetChildVariables(
                    destination,
                    finalParentId,
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
                )
            );

            log.info("✓ Debug getVariables successful");
            return ResponseEntity.ok(DebugOperationResponse.success("getVariables", responseXml));

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(DebugOperationResponse.error("getVariables", "Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            log.error("Debug getVariables failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DebugOperationResponse.error("getVariables", e.getMessage()));
        }
    }

    /**
     * Execute a step operation.
     *
     * POST /jco/debug/step
     *
     * @param request Operation request with operation (stepOver, stepInto, stepReturn)
     * @return Step response
     */
    @PostMapping("/step")
    public ResponseEntity<?> step(@RequestBody DebugOperationRequest request) {
        try {
            String stepType = request.getOperation();
            if (stepType == null || stepType.isEmpty()) {
                stepType = "stepOver";
            }

            // Validate step type
            if (!stepType.equals("stepOver") && !stepType.equals("stepInto") && !stepType.equals("stepReturn")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(DebugOperationResponse.error("step", "Invalid step type: " + stepType + ". Must be stepOver, stepInto, or stepReturn."));
            }

            log.info("Debug step - Session: {}, Type: {}", request.getSessionId(), stepType);

            final String finalStepType = stepType;
            String responseXml = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.debugStep(
                    destination,
                    finalStepType,
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
                )
            );

            log.info("✓ Debug step {} successful", stepType);
            return ResponseEntity.ok(DebugOperationResponse.success(stepType, responseXml));

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(DebugOperationResponse.error("step", "Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            log.error("Debug step failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DebugOperationResponse.error("step", e.getMessage()));
        }
    }

    /**
     * Resume execution.
     *
     * POST /jco/debug/resume
     *
     * @param request Operation request with sessionId
     * @return Resume response
     */
    @PostMapping("/resume")
    public ResponseEntity<?> resume(@RequestBody DebugOperationRequest request) {
        try {
            log.info("Debug resume - Session: {}", request.getSessionId());

            String responseXml = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.debugResume(
                    destination,
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
                )
            );

            log.info("✓ Debug resume successful");
            return ResponseEntity.ok(DebugOperationResponse.success("resume", responseXml));

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(DebugOperationResponse.error("resume", "Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            log.error("Debug resume failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DebugOperationResponse.error("resume", e.getMessage()));
        }
    }

    /**
     * Set a variable value during debugging.
     *
     * POST /jco/debug/setVariable
     *
     * Request body should contain:
     * - sessionId: JCo session ID
     * - requestBody: Variable name (e.g., "@LOCAL.LV_VALUE")
     * - operation: New value to set
     *
     * @param request Operation request with variable details
     * @return SetVariable response
     */
    @PostMapping("/setVariable")
    public ResponseEntity<?> setVariable(@RequestBody DebugOperationRequest request) {
        try {
            String variableName = request.getRequestBody();
            String newValue = request.getOperation();

            if (variableName == null || variableName.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(DebugOperationResponse.error("setVariable", "Variable name required in requestBody"));
            }
            if (newValue == null) {
                newValue = ""; // Allow empty string
            }

            log.info("Debug setVariable - Session: {}, Variable: {}, Value: '{}'",
                     request.getSessionId(), variableName, newValue);

            final String finalVariableName = variableName;
            final String finalNewValue = newValue;
            String responseXml = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.debugSetVariableValue(
                    destination,
                    finalVariableName,
                    finalNewValue,
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
                )
            );

            log.info("✓ Debug setVariable successful");
            return ResponseEntity.ok(DebugOperationResponse.success("setVariable", responseXml));

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(DebugOperationResponse.error("setVariable", "Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            log.error("Debug setVariable failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DebugOperationResponse.error("setVariable", e.getMessage()));
        }
    }

    /**
     * Step to a specific line (jump or run to line).
     *
     * POST /jco/debug/stepToLine
     *
     * Request body should contain:
     * - sessionId: JCo session ID
     * - operation: Step type ("stepJumpToLine" or "stepRunToLine")
     * - requestBody: Source URI with line position (e.g., "/sap/bc/adt/oo/classes/zclass/includes/testclasses#start=26")
     *
     * @param request Operation request with step details
     * @return StepToLine response
     */
    @PostMapping("/stepToLine")
    public ResponseEntity<?> stepToLine(@RequestBody DebugOperationRequest request) {
        try {
            String stepType = request.getOperation();
            String sourceUri = request.getRequestBody();

            // Validate step type
            if (stepType == null || stepType.isEmpty()) {
                stepType = "stepRunToLine";
            }
            if (!stepType.equals("stepJumpToLine") && !stepType.equals("stepRunToLine")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(DebugOperationResponse.error("stepToLine",
                        "Invalid step type: " + stepType + ". Must be stepJumpToLine or stepRunToLine."));
            }

            if (sourceUri == null || sourceUri.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(DebugOperationResponse.error("stepToLine", "Source URI required in requestBody"));
            }

            log.info("Debug stepToLine - Session: {}, Type: {}, URI: {}",
                     request.getSessionId(), stepType, sourceUri);

            final String finalStepType = stepType;
            final String finalSourceUri = sourceUri;
            String responseXml = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.debugStepToLine(
                    destination,
                    finalStepType,
                    finalSourceUri,
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
                )
            );

            log.info("✓ Debug stepToLine ({}) successful", stepType);
            return ResponseEntity.ok(DebugOperationResponse.success(stepType, responseXml));

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(DebugOperationResponse.error("stepToLine", "Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            log.error("Debug stepToLine failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DebugOperationResponse.error("stepToLine", e.getMessage()));
        }
    }

    // ==================== BREAKPOINT/LISTENER ADT REST OPERATIONS ====================
    // These endpoints route ADT REST calls through the session's HTTP client for SSO support

    /**
     * Set an external breakpoint via ADT REST API.
     *
     * POST /jco/debug/breakpoint
     *
     * @param request Request with sessionId and breakpointXml
     * @return Breakpoint response with server ID
     */
    @PostMapping("/breakpoint")
    public ResponseEntity<?> setBreakpoint(@RequestBody DebugBreakpointRequest request) {
        try {
            log.info("Set breakpoint - Session: {}", request.getSessionId());

            String responseXml = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.debugSetBreakpointRest(
                    destination,
                    request.getBreakpointXml(),
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
                )
            );

            // Extract server breakpoint ID from response
            String serverBreakpointId = extractAttribute(responseXml, "serverId");

            log.info("✓ Breakpoint set successfully - ServerID: {}", serverBreakpointId);
            return ResponseEntity.ok(DebugBreakpointResponse.success(responseXml, serverBreakpointId));

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(DebugBreakpointResponse.error("Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            log.error("Set breakpoint failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DebugBreakpointResponse.error(e.getMessage()));
        }
    }

    /**
     * Start debug listener via ADT REST API (long-polling).
     *
     * POST /jco/debug/listener
     *
     * @param request Request with sessionId, params, and timeoutMs
     * @return Listener response with debuggee ID if breakpoint was hit
     */
    @PostMapping("/listener")
    public ResponseEntity<?> startListener(@RequestBody DebugListenerRequest request) {
        try {
            int timeoutMs = request.getTimeoutMs() != null ? request.getTimeoutMs() : 60000;

            log.info("Start listener - Session: {}, Timeout: {}ms", request.getSessionId(), timeoutMs);

            // Build query params from request
            java.util.Map<String, String> queryParams = new java.util.LinkedHashMap<>();
            DebugListenerParams params = request.getParams();
            if (params.getDebuggingMode() != null) {
                queryParams.put("debuggingMode", params.getDebuggingMode());
            }
            if (params.getRequestUser() != null) {
                queryParams.put("requestUser", params.getRequestUser());
            }
            if (params.getTerminalId() != null) {
                queryParams.put("terminalId", params.getTerminalId());
            }
            if (params.getIdeId() != null) {
                queryParams.put("ideId", params.getIdeId());
            }
            if (params.getCheckConflict() != null) {
                queryParams.put("checkConflict", params.getCheckConflict());
            }
            if (params.getIsNotifiedOnConflict() != null) {
                queryParams.put("isNotifiedOnConflict", params.getIsNotifiedOnConflict());
            }

            String responseXml = sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> adtClient.debugStartListenerRest(
                    destination,
                    queryParams,
                    timeoutMs,
                    session.getHttpClient(),
                    session.getCsrfTokenCache(),
                    session
                )
            );

            // Extract debuggee ID from response
            String debuggeeId = extractXmlValue(responseXml, "DEBUGGEE_ID");

            if (debuggeeId != null && !debuggeeId.isEmpty()) {
                log.info("✓ Listener returned debuggee: {}", debuggeeId);
                return ResponseEntity.ok(DebugListenerResponse.success(responseXml, debuggeeId.trim()));
            } else {
                log.info("Listener returned without debuggee (timeout or conflict)");
                return ResponseEntity.ok(DebugListenerResponse.success(responseXml, null));
            }

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(DebugListenerResponse.error("Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            // Check for timeout
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                log.info("Listener timed out");
                return ResponseEntity.ok(DebugListenerResponse.timeout());
            }
            log.error("Start listener failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DebugListenerResponse.error(e.getMessage()));
        }
    }

    /**
     * Delete debug listener and breakpoints via ADT REST API.
     *
     * DELETE /jco/debug/breakpoint
     *
     * @param request Request with sessionId and params
     * @return Success/failure response
     */
    @DeleteMapping("/breakpoint")
    public ResponseEntity<?> deleteBreakpoint(@RequestBody DebugBreakpointRequest request) {
        try {
            log.info("Delete breakpoint - Session: {}", request.getSessionId());

            // Build query params from request
            java.util.Map<String, String> queryParams = new java.util.LinkedHashMap<>();
            DebugListenerParams params = request.getParams();
            if (params.getDebuggingMode() != null) {
                queryParams.put("debuggingMode", params.getDebuggingMode());
            }
            if (params.getRequestUser() != null) {
                queryParams.put("requestUser", params.getRequestUser());
            }
            if (params.getTerminalId() != null) {
                queryParams.put("terminalId", params.getTerminalId());
            }
            if (params.getIdeId() != null) {
                queryParams.put("ideId", params.getIdeId());
            }

            sessionManager.executeInContext(
                request.getSessionId(),
                (destination, session) -> {
                    adtClient.debugDeleteBreakpointRest(
                        destination,
                        queryParams,
                        session.getHttpClient(),
                        session.getCsrfTokenCache(),
                        session
                    );
                    return null;  // void return
                }
            );

            log.info("✓ Breakpoint/listener deleted successfully");
            return ResponseEntity.ok(java.util.Map.of("success", true));

        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", request.getSessionId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(java.util.Map.of("success", false, "error", "Session not found: " + request.getSessionId()));

        } catch (Exception e) {
            log.error("Delete breakpoint failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(java.util.Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Parse attach response XML to extract key fields.
     */
    private DebugAttachResponse parseAttachResponse(String xml) {
        DebugAttachResponse response = new DebugAttachResponse();
        response.setRawXml(xml);

        // Extract debugSessionId
        String debugSessionId = extractAttribute(xml, "debugSessionId");
        response.setDebugSessionId(debugSessionId);

        // Extract debuggeeSessionId
        String debuggeeSessionId = extractAttribute(xml, "debuggeeSessionId");
        response.setDebuggeeSessionId(debuggeeSessionId);

        // Extract serverName
        String serverName = extractAttribute(xml, "serverName");
        response.setServerName(serverName);

        // Extract processId
        String processIdStr = extractAttribute(xml, "processId");
        if (processIdStr != null) {
            try {
                response.setProcessId(Integer.parseInt(processIdStr));
            } catch (NumberFormatException e) {
                response.setProcessId(0);
            }
        }

        // Extract isSteppingPossible
        String steppingStr = extractAttribute(xml, "isSteppingPossible");
        response.setSteppingPossible("true".equalsIgnoreCase(steppingStr));

        // Extract isTerminationPossible
        String terminationStr = extractAttribute(xml, "isTerminationPossible");
        response.setTerminationPossible("true".equalsIgnoreCase(terminationStr));

        return response;
    }

    /**
     * Extract an attribute value from XML.
     */
    private String extractAttribute(String xml, String attributeName) {
        String pattern = attributeName + "=\"";
        int startIdx = xml.indexOf(pattern);
        if (startIdx == -1) {
            return null;
        }
        startIdx += pattern.length();
        int endIdx = xml.indexOf("\"", startIdx);
        if (endIdx == -1) {
            return null;
        }
        return xml.substring(startIdx, endIdx);
    }

    /**
     * Extract a tag value from XML (e.g., <TAG>value</TAG>).
     */
    private String extractXmlValue(String xml, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int startIdx = xml.indexOf(openTag);
        if (startIdx == -1) {
            return null;
        }
        startIdx += openTag.length();
        int endIdx = xml.indexOf(closeTag, startIdx);
        if (endIdx == -1) {
            return null;
        }
        return xml.substring(startIdx, endIdx);
    }
}
