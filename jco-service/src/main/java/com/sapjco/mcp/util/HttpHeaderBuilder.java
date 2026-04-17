package com.sapjco.mcp.util;

import com.sapjco.mcp.model.JcoSession;
import okhttp3.Request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Utility class for building HTTP headers for SAP ADT REST API requests.
 * Provides consistent header construction for authentication, session management, and content types.
 */
public final class HttpHeaderBuilder {

    private HttpHeaderBuilder() {
        // Utility class - prevent instantiation
    }

    // Standard header names
    public static final String HEADER_SAP_CLIENT = "SAP-Client";
    public static final String HEADER_CSRF_TOKEN = "x-csrf-token";
    public static final String HEADER_SESSION_TYPE = "x-sap-adt-sessiontype";
    public static final String HEADER_CONTEXT_ID = "sap-contextid";
    public static final String HEADER_CONNECTION_ID = "sap-adt-connection-id";
    public static final String HEADER_SAPLB = "saplb";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ACCEPT = "Accept";

    // Common content types
    public static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain; charset=utf-8";
    public static final String CONTENT_TYPE_XML = "application/xml; charset=utf-8";
    public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    public static final String ACCEPT_ALL = "*/*";
    public static final String ACCEPT_LOCK = "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, " +
            "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9";

    // Session type values
    public static final String SESSION_TYPE_STATEFUL = "stateful";
    public static final String SESSION_TYPE_STATELESS = "stateless";

    /**
     * Add standard session headers to a request builder.
     * Includes: SAP-Client, x-sap-adt-sessiontype, sap-contextid, saplb (if available)
     *
     * @param builder Request builder
     * @param session JcoSession with context information
     * @param sapClient SAP client number
     * @param stateful Whether the session is stateful
     * @return The request builder for chaining
     */
    public static Request.Builder addStandardHeaders(Request.Builder builder, JcoSession session,
                                                      String sapClient, boolean stateful) {
        builder.addHeader(HEADER_SAP_CLIENT, sapClient);
        builder.addHeader(HEADER_SESSION_TYPE, stateful ? SESSION_TYPE_STATEFUL : SESSION_TYPE_STATELESS);

        if (session != null) {
            if (session.getContextId() != null) {
                builder.addHeader(HEADER_CONTEXT_ID, session.getContextId());
            }
            if (session.getSaplbToken() != null) {
                builder.addHeader(HEADER_SAPLB, session.getSaplbToken());
            }
            if (session.getConnectionId() != null) {
                builder.addHeader(HEADER_CONNECTION_ID, session.getConnectionId());
            }
        }

        return builder;
    }

    /**
     * Add standard stateful session headers to a request builder.
     * This is the most common case for write operations.
     *
     * @param builder Request builder
     * @param session JcoSession with context information
     * @param sapClient SAP client number
     * @return The request builder for chaining
     */
    public static Request.Builder addStatefulHeaders(Request.Builder builder, JcoSession session, String sapClient) {
        return addStandardHeaders(builder, session, sapClient, true);
    }

    /**
     * Add CSRF token header to a request builder.
     *
     * @param builder Request builder
     * @param csrfToken CSRF token value
     * @return The request builder for chaining
     */
    public static Request.Builder addCsrfHeader(Request.Builder builder, String csrfToken) {
        if (csrfToken != null && !csrfToken.isEmpty()) {
            builder.addHeader(HEADER_CSRF_TOKEN, csrfToken);
        }
        return builder;
    }

    /**
     * Add Content-Type and Accept headers to a request builder.
     *
     * @param builder Request builder
     * @param contentType Content-Type header value (null to skip)
     * @param accept Accept header value (null to skip)
     * @return The request builder for chaining
     */
    public static Request.Builder addContentHeaders(Request.Builder builder, String contentType, String accept) {
        if (contentType != null && !contentType.isEmpty()) {
            builder.addHeader(HEADER_CONTENT_TYPE, contentType);
        }
        if (accept != null && !accept.isEmpty()) {
            builder.addHeader(HEADER_ACCEPT, accept);
        }
        return builder;
    }

    /**
     * Add Basic authentication header to a request builder.
     *
     * @param builder Request builder
     * @param username SAP username
     * @param password SAP password
     * @return The request builder for chaining
     */
    public static Request.Builder addBasicAuth(Request.Builder builder, String username, String password) {
        if (username != null && password != null) {
            String credentials = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            builder.addHeader(HEADER_AUTHORIZATION, "Basic " + encoded);
        }
        return builder;
    }

    /**
     * Create Basic authentication header value.
     *
     * @param username SAP username
     * @param password SAP password
     * @return Basic auth header value, or null if credentials are missing
     */
    public static String createBasicAuth(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Add authentication header based on session type.
     * For SSO sessions (X.509/SNC), no Authorization header is added since auth is handled at RFC/TLS level.
     * For basic auth sessions, adds the Authorization header.
     *
     * @param builder Request builder
     * @param session JcoSession to check for SSO mode
     * @param username SAP username (for basic auth)
     * @param password SAP password (for basic auth)
     * @param authType Authentication type ("basic", "snc", "x509")
     * @return The request builder for chaining
     */
    public static Request.Builder addAuthHeader(Request.Builder builder, JcoSession session,
                                                 String username, String password, String authType) {
        // For SSO sessions, RFC proxy handles authentication - no auth header needed
        if (session != null && session.isHttpSsoEnabled()) {
            return builder;
        }

        // For X.509/SNC authentication, auth happens at TLS/RFC level
        if ("x509".equalsIgnoreCase(authType) || "snc".equalsIgnoreCase(authType)) {
            return builder;
        }

        // Basic authentication
        return addBasicAuth(builder, username, password);
    }

    /**
     * Add standard headers to a Map (for RFC proxy calls).
     *
     * @param headers Headers map to populate
     * @param session JcoSession with context information
     * @param sapClient SAP client number
     * @param stateful Whether the session is stateful
     */
    public static void addStandardHeaders(Map<String, String> headers, JcoSession session,
                                           String sapClient, boolean stateful) {
        headers.put(HEADER_SAP_CLIENT, sapClient);
        headers.put(HEADER_SESSION_TYPE, stateful ? SESSION_TYPE_STATEFUL : SESSION_TYPE_STATELESS);

        if (session != null) {
            if (session.getContextId() != null) {
                headers.put(HEADER_CONTEXT_ID, session.getContextId());
            }
            if (session.getSaplbToken() != null) {
                headers.put(HEADER_SAPLB, session.getSaplbToken());
            }
            if (session.getConnectionId() != null) {
                headers.put(HEADER_CONNECTION_ID, session.getConnectionId());
            }
        }
    }

    /**
     * Add CSRF token to a headers Map.
     *
     * @param headers Headers map to populate
     * @param csrfToken CSRF token value
     */
    public static void addCsrfHeader(Map<String, String> headers, String csrfToken) {
        if (csrfToken != null && !csrfToken.isEmpty()) {
            headers.put(HEADER_CSRF_TOKEN, csrfToken);
        }
    }

    /**
     * Add Content-Type and Accept headers to a Map.
     *
     * @param headers Headers map to populate
     * @param contentType Content-Type header value (null to skip)
     * @param accept Accept header value (null to skip)
     */
    public static void addContentHeaders(Map<String, String> headers, String contentType, String accept) {
        if (contentType != null && !contentType.isEmpty()) {
            headers.put(HEADER_CONTENT_TYPE, contentType);
        }
        if (accept != null && !accept.isEmpty()) {
            headers.put(HEADER_ACCEPT, accept);
        }
    }

    /**
     * Add Basic authentication header to a Map.
     *
     * @param headers Headers map to populate
     * @param username SAP username
     * @param password SAP password
     */
    public static void addBasicAuth(Map<String, String> headers, String username, String password) {
        String authValue = createBasicAuth(username, password);
        if (authValue != null) {
            headers.put(HEADER_AUTHORIZATION, authValue);
        }
    }

    /**
     * Add authentication header to a Map based on session type.
     *
     * @param headers Headers map to populate
     * @param session JcoSession to check for SSO mode
     * @param username SAP username (for basic auth)
     * @param password SAP password (for basic auth)
     * @param authType Authentication type ("basic", "snc", "x509")
     */
    public static void addAuthHeader(Map<String, String> headers, JcoSession session,
                                      String username, String password, String authType) {
        // For SSO sessions, RFC proxy handles authentication - no auth header needed
        if (session != null && session.isHttpSsoEnabled()) {
            return;
        }

        // For X.509/SNC authentication, auth happens at TLS/RFC level
        if ("x509".equalsIgnoreCase(authType) || "snc".equalsIgnoreCase(authType)) {
            return;
        }

        // Basic authentication
        addBasicAuth(headers, username, password);
    }
}
