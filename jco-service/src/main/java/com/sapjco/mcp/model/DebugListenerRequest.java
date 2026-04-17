package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request for starting debug listener via ADT REST (long-polling).
 */
@Data
public class DebugListenerRequest {
    private String sessionId;
    private DebugListenerParams params;
    private Integer timeoutMs;  // Timeout for long-polling
}
