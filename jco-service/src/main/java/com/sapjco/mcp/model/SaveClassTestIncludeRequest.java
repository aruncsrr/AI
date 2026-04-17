package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request model for atomic save-class-test-include operation.
 * Combines lock, save test include, and unlock in a single operation.
 */
@Data
public class SaveClassTestIncludeRequest {
    private String sessionId;
    private String className;
    private String sourceCode;
    /** Optional transport request number. If not provided, uses auto-detected transport from lock response. */
    private String transportNumber;
}
