package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for debug listener operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebugListenerResponse {
    private boolean success;
    private String responseXml;
    private String debuggeeId;
    private String error;

    public static DebugListenerResponse success(String responseXml, String debuggeeId) {
        return new DebugListenerResponse(true, responseXml, debuggeeId, null);
    }

    public static DebugListenerResponse timeout() {
        return new DebugListenerResponse(true, null, null, "timeout");
    }

    public static DebugListenerResponse error(String error) {
        return new DebugListenerResponse(false, null, null, error);
    }
}
