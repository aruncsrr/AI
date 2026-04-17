package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request to unlock an object.
 */
@Data
public class UnlockRequest {
    private String sessionId;
    private String objectName;
    private String objectType;
    private String lockHandle;
}
