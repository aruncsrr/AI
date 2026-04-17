package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from object creation operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateResponse {
    /**
     * Object URI returned in Location header (e.g., "/sap/bc/adt/oo/classes/ZTEST_CLASS")
     */
    private String objectUri;

    /**
     * Name of the created object
     */
    private String objectName;

    /**
     * Type of the created object (e.g., "class", "interface", "program")
     */
    private String objectType;

    /**
     * HTTP status code (201 for successful creation)
     */
    private int httpStatus;

    /**
     * Raw response body from SAP (may be empty on success)
     */
    private String rawResponse;
}
