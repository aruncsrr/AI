package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request to activate an object.
 */
@Data
public class ActivateRequest {
    private String sessionId;
    private String objectName;
    private String objectType;
}
