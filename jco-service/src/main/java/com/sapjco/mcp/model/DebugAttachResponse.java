package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from debug attach operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebugAttachResponse {
    private String debugSessionId;
    private String debuggeeSessionId;
    private String serverName;
    private int processId;
    private boolean steppingPossible;
    private boolean terminationPossible;
    private String rawXml;
}
