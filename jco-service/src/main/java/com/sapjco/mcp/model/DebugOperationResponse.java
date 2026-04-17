package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from debug operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebugOperationResponse {
    private boolean success;
    private String operation;
    private String responseXml;
    private String errorMessage;

    public static DebugOperationResponse success(String operation, String responseXml) {
        return new DebugOperationResponse(true, operation, responseXml, null);
    }

    public static DebugOperationResponse error(String operation, String errorMessage) {
        return new DebugOperationResponse(false, operation, null, errorMessage);
    }
}
