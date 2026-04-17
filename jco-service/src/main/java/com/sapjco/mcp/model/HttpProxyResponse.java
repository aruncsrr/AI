package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

/**
 * Response model for HTTP proxy requests routed through the JCo service.
 *
 * <p>This model encapsulates the response from proxied HTTP requests,
 * allowing the Node.js MCP server to leverage the Java service's SSO
 * capabilities (OS keystore certificates, SNC authentication) for
 * HTTP requests to SAP systems.</p>
 *
 * <p>When the proxy request succeeds, statusCode, headers, and body
 * contain the actual HTTP response. When the proxy itself fails
 * (e.g., connection error, SSL handshake failure), the error field
 * is populated with a descriptive message.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HttpProxyResponse {

    /**
     * HTTP status code from the proxied response.
     * Will be 500 for proxy-level errors.
     */
    private int statusCode;

    /**
     * Response headers from the proxied request.
     * Empty map for proxy-level errors.
     */
    private Map<String, String> headers;

    /**
     * Response body from the proxied request.
     * May be null or empty for proxy-level errors.
     */
    private String body;

    /**
     * Error message if the proxy request failed at the proxy level.
     * Null when the proxied request completed (regardless of HTTP status).
     */
    private String error;

    /**
     * Creates an HttpProxyResponse for a successful proxy operation.
     *
     * @param statusCode HTTP status code from the proxied response
     * @param headers Response headers from the proxied request
     * @param body Response body from the proxied request
     */
    public HttpProxyResponse(int statusCode, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.error = null;
    }

    /**
     * Creates an HttpProxyResponse indicating a proxy-level error.
     *
     * <p>Use this factory method when the proxy itself fails to complete
     * the request (e.g., connection refused, SSL errors, timeout).</p>
     *
     * @param errorMessage Description of the proxy-level error
     * @return HttpProxyResponse with status 500 and the error message
     */
    public static HttpProxyResponse error(String errorMessage) {
        HttpProxyResponse response = new HttpProxyResponse();
        response.setStatusCode(500);
        response.setHeaders(Collections.emptyMap());
        response.setBody(null);
        response.setError(errorMessage);
        return response;
    }
}
