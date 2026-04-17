package com.sapjco.mcp.util;

import com.sap.conn.jco.JCoDestination;
import com.sapjco.mcp.model.CreateResponse;
import com.sapjco.mcp.model.HttpProxyResponse;
import com.sapjco.mcp.model.JcoSession;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for executing ADT REST API calls via RFC proxy (SADT_REST_RFC_ENDPOINT).
 * This is the key mechanism for SNC SSO sessions where HTTP calls are wrapped in RFC calls.
 *
 * Provides standardized methods for common ADT operations:
 * - Object creation (class, interface, program, etc.)
 * - Lock/unlock operations
 * - Save operations
 */
@Slf4j
public final class RfcProxyExecutor {

    private RfcProxyExecutor() {
        // Utility class - prevent instantiation
    }

    // Content types for different object types
    public static final String CONTENT_TYPE_CLASS = "application/vnd.sap.adt.oo.classes.v4+xml; charset=utf-8";
    public static final String CONTENT_TYPE_INTERFACE = "application/vnd.sap.adt.oo.interfaces.v2+xml; charset=utf-8";
    public static final String CONTENT_TYPE_PROGRAM = "application/vnd.sap.adt.programs.programs.v2+xml; charset=utf-8";
    public static final String CONTENT_TYPE_FUNCTION_GROUP = "application/vnd.sap.adt.functions.groups.v4+xml; charset=utf-8";
    public static final String CONTENT_TYPE_INCLUDE = "application/vnd.sap.adt.programs.includes.v2+xml; charset=utf-8";

    public static final String ACCEPT_CLASS = "application/vnd.sap.adt.oo.classes.v4+xml, application/xml, */*";
    public static final String ACCEPT_INTERFACE = "application/vnd.sap.adt.oo.interfaces.v2+xml, application/xml, */*";
    public static final String ACCEPT_PROGRAM = "application/vnd.sap.adt.programs.programs.v2+xml, application/xml, */*";
    public static final String ACCEPT_FUNCTION_GROUP = "application/vnd.sap.adt.functions.groups.v4+xml, application/xml, */*";
    public static final String ACCEPT_INCLUDE = "application/vnd.sap.adt.programs.includes.v2+xml, application/xml, */*";

    /**
     * Build standard RFC headers map for ADT operations.
     *
     * @param session    JcoSession with context information
     * @param sapClient  SAP client number
     * @return Headers map with session headers
     */
    public static Map<String, String> buildStandardHeaders(JcoSession session, String sapClient) {
        Map<String, String> headers = new LinkedHashMap<>();

        // Client header
        if (session.getClient() != null && !session.getClient().isEmpty()) {
            headers.put(HttpHeaderBuilder.HEADER_SAP_CLIENT, session.getClient());
        } else if (sapClient != null && !sapClient.isEmpty()) {
            headers.put(HttpHeaderBuilder.HEADER_SAP_CLIENT, sapClient);
        }

        // Session type
        headers.put(HttpHeaderBuilder.HEADER_SESSION_TYPE, HttpHeaderBuilder.SESSION_TYPE_STATEFUL);

        // Context headers
        if (session.getContextId() != null) {
            headers.put(HttpHeaderBuilder.HEADER_CONTEXT_ID, session.getContextId());
        }
        if (session.getConnectionId() != null) {
            headers.put(HttpHeaderBuilder.HEADER_CONNECTION_ID, session.getConnectionId());
        }
        if (session.getSaplbToken() != null) {
            headers.put(HttpHeaderBuilder.HEADER_SAPLB, session.getSaplbToken());
        }

        return headers;
    }

    /**
     * Build headers for object creation operations.
     *
     * @param session     JcoSession with context information
     * @param sapClient   SAP client number
     * @param contentType Content-Type header for the object type
     * @param accept      Accept header for the object type
     * @return Headers map with session and content headers
     */
    public static Map<String, String> buildCreationHeaders(JcoSession session, String sapClient,
                                                            String contentType, String accept) {
        Map<String, String> headers = buildStandardHeaders(session, sapClient);
        headers.put(HttpHeaderBuilder.HEADER_CONTENT_TYPE, contentType);
        headers.put(HttpHeaderBuilder.HEADER_ACCEPT, accept);
        return headers;
    }

    /**
     * Build query parameters map with transport number if provided.
     *
     * @param transportNumber Optional transport request number
     * @return Query parameters map, or null if no transport
     */
    public static Map<String, String> buildCreationQueryParams(String transportNumber) {
        if (transportNumber != null && !transportNumber.isEmpty()) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("corrNr", transportNumber);
            return params;
        }
        return null;
    }

    /**
     * Parse CreateResponse from HTTP proxy response.
     *
     * @param response    HttpProxyResponse from RFC call
     * @param objectName  Name of the created object
     * @param objectType  Type of the created object
     * @return CreateResponse with object URI and status
     * @throws IOException if response indicates error
     */
    public static CreateResponse parseCreateResponse(HttpProxyResponse response, String objectName, String objectType)
            throws IOException {

        if (response.getStatusCode() >= 400) {
            throw new IOException(String.format("Create %s via RFC failed: %d - %s",
                    objectType, response.getStatusCode(), response.getBody()));
        }

        // Extract Location header
        String locationHeader = AdtResponseHandler.extractLocationHeader(response.getHeaders());

        log.info("✓ {} created via RFC - Location: {}", objectType, locationHeader);

        return CreateResponse.builder()
                .objectUri(locationHeader)
                .objectName(objectName.toUpperCase())
                .objectType(objectType)
                .httpStatus(response.getStatusCode())
                .rawResponse(response.getBody())
                .build();
    }

    /**
     * Get content type for object creation based on object type.
     *
     * @param objectType Object type (class, interface, program, function_group, include)
     * @return Content-Type header value
     */
    public static String getContentTypeForCreation(String objectType) {
        return switch (objectType.toLowerCase()) {
            case "class" -> CONTENT_TYPE_CLASS;
            case "interface" -> CONTENT_TYPE_INTERFACE;
            case "program" -> CONTENT_TYPE_PROGRAM;
            case "function_group" -> CONTENT_TYPE_FUNCTION_GROUP;
            case "include" -> CONTENT_TYPE_INCLUDE;
            default -> throw new IllegalArgumentException("Unsupported object type: " + objectType);
        };
    }

    /**
     * Get accept header for object creation based on object type.
     *
     * @param objectType Object type (class, interface, program, function_group, include)
     * @return Accept header value
     */
    public static String getAcceptForCreation(String objectType) {
        return switch (objectType.toLowerCase()) {
            case "class" -> ACCEPT_CLASS;
            case "interface" -> ACCEPT_INTERFACE;
            case "program" -> ACCEPT_PROGRAM;
            case "function_group" -> ACCEPT_FUNCTION_GROUP;
            case "include" -> ACCEPT_INCLUDE;
            default -> throw new IllegalArgumentException("Unsupported object type: " + objectType);
        };
    }

    /**
     * Build URI with query parameters.
     *
     * @param basePath    Base URI path
     * @param queryParams Query parameters map
     * @return Full URI with query string
     */
    public static String buildUriWithParams(String basePath, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return basePath;
        }

        StringBuilder uriBuilder = new StringBuilder(basePath);
        uriBuilder.append("?");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!first) {
                uriBuilder.append("&");
            }
            uriBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            uriBuilder.append("=");
            uriBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return uriBuilder.toString();
    }
}
