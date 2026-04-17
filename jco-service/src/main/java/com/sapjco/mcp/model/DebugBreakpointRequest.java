package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request for setting/deleting debug breakpoints via ADT REST.
 */
@Data
public class DebugBreakpointRequest {
    private String sessionId;
    private String breakpointXml;  // XML payload for set breakpoint
    private DebugListenerParams params;  // Query parameters for delete
}
