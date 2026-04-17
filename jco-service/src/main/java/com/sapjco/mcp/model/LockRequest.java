package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request to lock an object.
 */
@Data
public class LockRequest {
    private String sessionId;
    private String objectName;
    private String objectType;
}
