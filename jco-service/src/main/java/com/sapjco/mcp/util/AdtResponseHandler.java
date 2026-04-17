package com.sapjco.mcp.util;

import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

/**
 * Utility class for handling ADT REST API responses.
 * Provides consistent error handling and response parsing across all ADT operations.
 */
public final class AdtResponseHandler {

    private AdtResponseHandler() {
        // Utility class - prevent instantiation
    }

    /**
     * Handle an error response by extracting the body and throwing an IOException with details.
     *
     * @param response    HTTP response
     * @param operation   Name of the operation (for error message context)
     * @throws IOException always throws with operation name and response details
     */
    public static void handleError(Response response, String operation) throws IOException {
        String body = extractBody(response);
        String message = formatErrorMessage(response.code(), body, operation);
        throw new IOException(message);
    }

    /**
     * Check if response is successful, and if not, throw an IOException with details.
     *
     * @param response    HTTP response
     * @param operation   Name of the operation (for error message context)
     * @throws IOException if response is not successful
     */
    public static void checkSuccess(Response response, String operation) throws IOException {
        if (!response.isSuccessful()) {
            handleError(response, operation);
        }
    }

    /**
     * Extract body from response safely.
     *
     * @param response HTTP response
     * @return Body string, or empty string if body is null
     * @throws IOException if body cannot be read
     */
    public static String extractBody(Response response) throws IOException {
        return response.body() != null ? response.body().string() : "";
    }

    /**
     * Extract body from response safely, returning null for empty bodies.
     *
     * @param response HTTP response
     * @return Body string, or null if body is null/empty
     * @throws IOException if body cannot be read
     */
    public static String extractBodyOrNull(Response response) throws IOException {
        if (response.body() == null) {
            return null;
        }
        String body = response.body().string();
        return body.isEmpty() ? null : body;
    }

    /**
     * Format an error message with HTTP status code, body excerpt, and operation name.
     *
     * @param statusCode HTTP status code
     * @param body       Response body
     * @param operation  Operation name
     * @return Formatted error message
     */
    public static String formatErrorMessage(int statusCode, String body, String operation) {
        StringBuilder message = new StringBuilder();
        message.append(operation).append(" failed: HTTP ").append(statusCode);

        if (body != null && !body.isEmpty()) {
            // Truncate long bodies
            String excerpt = body.length() > 500 ? body.substring(0, 500) + "..." : body;
            message.append(" - ").append(excerpt);
        }

        // Add helpful hints for common errors
        if (statusCode == 403) {
            if (body != null && (body.contains("locked") || body.contains("enqueue"))) {
                message.append("\nHint: Object may already be locked by another user. Check SM12 for active locks.");
            } else {
                message.append("\nHint: Permission denied or object is read-only.");
            }
        } else if (statusCode == 404) {
            message.append("\nHint: Object not found. Check that the object name is correct.");
        } else if (statusCode == 409) {
            message.append("\nHint: Conflict - the object may have been modified by another user.");
        }

        return message.toString();
    }

    /**
     * Extract Location header from HTTP proxy response headers (case-insensitive).
     *
     * @param headers Response headers map
     * @return Location header value, or null if not found
     */
    public static String extractLocationHeader(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }

        // Try exact case first
        String location = headers.get("Location");
        if (location != null) {
            return location;
        }

        // Try case-insensitive search
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if ("location".equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Extract ETag header from HTTP response (case-insensitive).
     *
     * @param response HTTP response
     * @return ETag header value, or null if not found
     */
    public static String extractETag(Response response) {
        String etag = response.header("ETag");
        if (etag == null) {
            etag = response.header("etag");
        }
        return etag;
    }

    /**
     * Check if HTTP status code indicates success (2xx).
     *
     * @param statusCode HTTP status code
     * @return true if status code is in 2xx range
     */
    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Check if HTTP status code indicates client error (4xx).
     *
     * @param statusCode HTTP status code
     * @return true if status code is in 4xx range
     */
    public static boolean isClientError(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * Check if HTTP status code indicates server error (5xx).
     *
     * @param statusCode HTTP status code
     * @return true if status code is in 5xx range
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }
}
