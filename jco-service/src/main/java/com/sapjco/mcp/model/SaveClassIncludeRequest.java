package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request model for atomic save-class-include operation.
 * Combines lock, save class include, and unlock in a single operation.
 * Supports all include types: definitions, implementations, macros, testClasses.
 */
@Data
public class SaveClassIncludeRequest {
    private String sessionId;
    private String className;
    private String includeType; // definitions, implementations, macros, testClasses
    private String sourceCode;
    /** Optional transport request number. If not provided, uses auto-detected transport from lock response. */
    private String transportNumber;
}
