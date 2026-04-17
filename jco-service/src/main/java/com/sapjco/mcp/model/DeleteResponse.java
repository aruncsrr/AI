package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from object deletion operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteResponse {
    /**
     * Name of the deleted object
     */
    private String objectName;

    /**
     * Type of the deleted object (e.g., "class", "interface", "program")
     */
    private String objectType;

    /**
     * HTTP status code (200 or 204 for successful deletion)
     */
    private int httpStatus;

    /**
     * Raw response body from SAP (usually empty on success)
     */
    private String rawResponse;
}
