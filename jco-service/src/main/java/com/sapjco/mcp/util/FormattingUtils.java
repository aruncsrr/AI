package com.sapjco.mcp.util;

/**
 * Common formatting utilities for MCP handlers.
 * Provides consistent text truncation and duration formatting across all handlers.
 */
public final class FormattingUtils {

    private FormattingUtils() {
        // Utility class - no instantiation
    }

    /**
     * Truncate text to a maximum length, appending "..." if truncated.
     *
     * @param text the text to truncate
     * @param maxLength maximum length including ellipsis
     * @return truncated text, or empty string if null/empty
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        text = text.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Format a duration in milliseconds for display.
     *
     * @param timeMs duration in milliseconds
     * @param unit the original unit ("s" for seconds, "ms" for milliseconds)
     * @return formatted duration string (e.g., "1.234s" or "100ms")
     */
    public static String formatDuration(long timeMs, String unit) {
        if ("s".equals(unit)) {
            // Value is already in milliseconds after unit conversion
            return String.format("%d.%03ds", timeMs / 1000, timeMs % 1000);
        } else if ("ms".equals(unit) || timeMs > 0) {
            return String.format("%dms", timeMs);
        }
        return "-";
    }
}
