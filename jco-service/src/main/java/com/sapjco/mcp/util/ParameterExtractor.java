package com.sapjco.mcp.util;

import com.sapjco.mcp.mcp.McpResponseFormatter;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for extracting and validating parameters from tool request arguments.
 * Provides type-safe extraction methods with consistent null handling and error reporting.
 */
public final class ParameterExtractor {

    private ParameterExtractor() {
        // Utility class - prevent instantiation
    }

    /**
     * Extract a required string parameter. Throws IllegalArgumentException if missing or empty.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return The non-empty string value
     * @throws IllegalArgumentException if the parameter is missing or empty
     */
    public static String requireString(Map<String, Object> args, String name) {
        String value = (String) args.get(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    /**
     * Try to extract a required string parameter. Returns an error result if missing or empty.
     * Use this variant for early validation in handler methods.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return null if the parameter exists and is non-empty; a CallToolResult error otherwise
     */
    public static CallToolResult validateRequiredString(Map<String, Object> args, String name) {
        String value = (String) args.get(name);
        if (value == null || value.isEmpty()) {
            return McpResponseFormatter.error(name + " is required");
        }
        return null;
    }

    /**
     * Extract an optional string parameter. Returns null if not present.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return The string value, or null if not present
     */
    public static String optionalString(Map<String, Object> args, String name) {
        return (String) args.get(name);
    }

    /**
     * Extract an optional string parameter with a default value.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @return The string value, or the default value if not present
     */
    public static String optionalString(Map<String, Object> args, String name, String defaultValue) {
        String value = (String) args.get(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Extract an optional boolean parameter with a default value.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @return The boolean value, or the default value if not present
     */
    public static boolean optionalBoolean(Map<String, Object> args, String name, boolean defaultValue) {
        Object value = args.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        // Handle string "true"/"false" as well
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /**
     * Extract an optional integer parameter with a default value.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @return The integer value, or the default value if not present
     */
    public static int optionalInt(Map<String, Object> args, String name, int defaultValue) {
        Object value = args.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Extract an optional integer parameter with a default value and maximum limit.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @param maxValue Maximum allowed value (will be clamped to this)
     * @return The integer value clamped to maxValue, or the default value if not present
     */
    public static int optionalInt(Map<String, Object> args, String name, int defaultValue, int maxValue) {
        int value = optionalInt(args, name, defaultValue);
        return Math.min(value, maxValue);
    }

    /**
     * Extract an optional Integer (nullable) parameter.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return The Integer value, or null if not present
     */
    public static Integer optionalInteger(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extract an optional long parameter with a default value.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @return The long value, or the default value if not present
     */
    public static long optionalLong(Map<String, Object> args, String name, long defaultValue) {
        Object value = args.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Extract an optional double parameter with a default value.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @param defaultValue Default value if not present
     * @return The double value, or the default value if not present
     */
    public static double optionalDouble(Map<String, Object> args, String name, double defaultValue) {
        Object value = args.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Extract an optional list of strings parameter.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return The list of strings, or an empty list if not present
     */
    @SuppressWarnings("unchecked")
    public static List<String> optionalStringList(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        // Handle single string as a list of one element
        if (value instanceof String) {
            return List.of((String) value);
        }
        return List.of();
    }

    /**
     * Extract an optional Map parameter.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return The map, or null if not present
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> optionalMap(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    /**
     * Check if a string parameter is present and non-empty.
     *
     * @param args Map of request arguments
     * @param name Parameter name
     * @return true if the parameter is present and non-empty
     */
    public static boolean hasString(Map<String, Object> args, String name) {
        String value = (String) args.get(name);
        return value != null && !value.isEmpty();
    }

    /**
     * Check if any of the given parameters are present and non-empty.
     *
     * @param args Map of request arguments
     * @param names Parameter names to check
     * @return true if any of the parameters is present and non-empty
     */
    public static boolean hasAnyString(Map<String, Object> args, String... names) {
        for (String name : names) {
            if (hasString(args, name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate that exactly one of two parameters is provided.
     *
     * @param args Map of request arguments
     * @param param1 First parameter name
     * @param param2 Second parameter name
     * @return null if exactly one is present; a CallToolResult error otherwise
     */
    public static CallToolResult validateExactlyOne(Map<String, Object> args, String param1, String param2) {
        boolean has1 = hasString(args, param1);
        boolean has2 = hasString(args, param2);

        if (has1 && has2) {
            return McpResponseFormatter.error("Provide " + param1 + " OR " + param2 + ", not both");
        }
        if (!has1 && !has2) {
            return McpResponseFormatter.error("Must provide " + param1 + " or " + param2);
        }
        return null;
    }
}
