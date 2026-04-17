package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Parameters for debug listener operations.
 */
@Data
public class DebugListenerParams {
    private String debuggingMode;
    private String requestUser;
    private String terminalId;
    private String ideId;
    private String checkConflict;
    private String isNotifiedOnConflict;
}
