package com.sapjco.mcp.model;

import lombok.Data;

/**
 * Request to save an object.
 */
@Data
public class SaveRequest {
    private String sessionId;
    private String objectName;
    private String objectType;
    private String sourceCode;
    private String lockHandle;
    private String transportNumber;
}
