package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response when creating a session.
 */
@Data
@AllArgsConstructor
public class SessionResponse {
    private String sessionId;
}
