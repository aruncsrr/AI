package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from lock operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LockResponse {
    private String lockHandle;
    private String transportNumber;
}
