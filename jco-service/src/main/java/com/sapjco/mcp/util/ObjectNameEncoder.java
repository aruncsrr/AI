package com.sapjco.mcp.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for encoding ABAP object names for use in URLs.
 * Handles namespaced objects (e.g., /NAMESPACE/CLASS) by encoding "/" characters.
 */
public final class ObjectNameEncoder {

    private ObjectNameEncoder() {
        // Utility class - prevent instantiation
    }

    /**
     * Encode an ABAP object name for use in URL paths.
     * Handles namespaced objects by encoding "/" as "%2F".
     *
     * @param name The object name to encode (e.g., "ZCL_MY_CLASS" or "/SCMTMS/CL_TOR")
     * @return The encoded name suitable for use in URL paths
     */
    public static String encode(String name) {
        if (name == null) {
            return null;
        }
        // Handle namespaced objects like /NAMESPACE/CLASS
        if (name.startsWith("/")) {
            return name.replace("/", "%2F");
        }
        return name;
    }

    /**
     * Encode an ABAP object name for use in URL paths, converting to uppercase.
     * Handles namespaced objects by encoding "/" as "%2F".
     *
     * @param name The object name to encode (e.g., "zcl_my_class" or "/scmtms/cl_tor")
     * @return The encoded name in uppercase suitable for use in URL paths
     */
    public static String encodeUpperCase(String name) {
        if (name == null) {
            return null;
        }
        String upperName = name.toUpperCase();
        // Handle namespaced objects like /NAMESPACE/CLASS
        if (upperName.startsWith("/")) {
            return upperName.replace("/", "%2F");
        }
        return upperName;
    }

    /**
     * Decode a URL-encoded ABAP object name back to its original form.
     *
     * @param encodedName The encoded name (e.g., "%2FSCMTMS%2FCL_TOR")
     * @return The decoded name (e.g., "/SCMTMS/CL_TOR")
     */
    public static String decode(String encodedName) {
        if (encodedName == null) {
            return null;
        }
        return encodedName.replace("%2F", "/").replace("%2f", "/");
    }

    /**
     * Full URL encoding of a string, suitable for query parameters.
     * Uses UTF-8 encoding and encodes all special characters including "/" and "+".
     *
     * @param value The string to encode
     * @return The URL-encoded string
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Check if an object name is namespaced (starts with "/").
     *
     * @param name The object name to check
     * @return true if the name is namespaced
     */
    public static boolean isNamespaced(String name) {
        return name != null && name.startsWith("/");
    }

    /**
     * Extract the namespace from a namespaced object name.
     *
     * @param name The namespaced object name (e.g., "/SCMTMS/CL_TOR")
     * @return The namespace (e.g., "/SCMTMS/") or null if not namespaced
     */
    public static String extractNamespace(String name) {
        if (!isNamespaced(name)) {
            return null;
        }
        // Find the second "/" which marks the end of the namespace
        int secondSlash = name.indexOf("/", 1);
        if (secondSlash > 0) {
            return name.substring(0, secondSlash + 1);
        }
        return null;
    }
}
