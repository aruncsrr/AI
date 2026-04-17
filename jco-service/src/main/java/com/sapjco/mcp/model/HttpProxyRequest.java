package com.sapjco.mcp.model;

import lombok.Data;
import java.util.Map;

/**
 * Request model for HTTP proxy routing through the JCo service.
 *
 * This model enables routing ADT HTTP requests through the Java service,
 * allowing SSO authentication using OS keystore certificates (macOS Keychain
 * or Windows Certificate Store) that are only accessible from Java.
 *
 * The proxy endpoint receives this request, uses the session's stored
 * credentials/certificates to authenticate with SAP, and returns the response.
 */
@Data
public class HttpProxyRequest {
    /**
     * JCo session ID (required).
     * The session must be created via CreateSession before making proxy requests.
     * Session contains authentication credentials and SAP system configuration.
     */
    private String sessionId;

    /**
     * HTTP method (GET, POST, PUT, DELETE).
     * Defaults to GET if not specified.
     */
    private String method;

    /**
     * ADT path (e.g., "/sap/bc/adt/oo/classes/ZCL_TEST/source/main").
     * This is the relative path to the SAP ADT endpoint.
     * The full URL is constructed by combining the session's base URL with this path.
     */
    private String path;

    /**
     * Query parameters to append to the URL.
     * Example: {"version": "active", "_action": "LOCK"}
     */
    private Map<String, String> queryParams;

    /**
     * Custom HTTP headers (Accept, Content-Type, etc.).
     * Common headers include:
     * - Accept: application/vnd.sap.adt.oo.classes.v2+xml
     * - Content-Type: text/plain
     * - X-sap-adt-sessiontype: stateful
     */
    private Map<String, String> headers;

    /**
     * Request body for POST/PUT requests.
     * For ADT operations, this is typically ABAP source code or XML payloads.
     */
    private String body;

    /**
     * Request timeout in milliseconds.
     * Default: 120000 (2 minutes).
     * Some operations like activation or ATC checks may require longer timeouts.
     */
    private int timeoutMs = 120000;
}
