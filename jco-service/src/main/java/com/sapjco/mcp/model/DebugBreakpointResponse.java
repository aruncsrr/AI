package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for debug breakpoint operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebugBreakpointResponse {
    private boolean success;
    private String responseXml;
    private String serverBreakpointId;
    private String error;

    public static DebugBreakpointResponse success(String responseXml, String serverBreakpointId) {
        return new DebugBreakpointResponse(true, responseXml, serverBreakpointId, null);
    }

    public static DebugBreakpointResponse error(String error) {
        return new DebugBreakpointResponse(false, null, null, error);
    }
}
