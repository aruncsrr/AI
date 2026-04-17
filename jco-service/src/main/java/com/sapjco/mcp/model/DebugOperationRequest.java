package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request for debug operations (step, resume, getStack, getVariables, etc.)
 */
@Data
public class DebugOperationRequest {
    private String sessionId;
    private String operation;  // stepOver, stepInto, stepReturn, resume, getStack, getVariables, getChildVariables, setDebuggerSettings
    private String requestBody;  // Optional XML body for operations that need it
}
