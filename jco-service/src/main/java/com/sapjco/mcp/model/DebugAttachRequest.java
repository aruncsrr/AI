package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request to attach to a debuggee session.
 */
@Data
public class DebugAttachRequest {
    private String sessionId;
    private String debuggeeId;
    private String requestUser;
    private boolean dynproDebugging = true;
}
