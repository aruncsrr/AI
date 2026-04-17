package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request model for atomic save-program operation.
 * Combines lock, save, and unlock in a single operation.
 */
@Data
public class SaveProgramRequest {
    private String sessionId;
    private String programName;
    private String sourceCode;
}
