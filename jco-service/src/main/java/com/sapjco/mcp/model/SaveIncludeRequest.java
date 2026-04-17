package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request model for atomic save-include operation.
 * Combines lock, save, and unlock in a single operation.
 */
@Data
public class SaveIncludeRequest {
    private String sessionId;
    private String includeName;
    private String sourceCode;
    /** Optional transport request number. If not provided, uses auto-detected transport from lock response. */
    private String transportNumber;
}
