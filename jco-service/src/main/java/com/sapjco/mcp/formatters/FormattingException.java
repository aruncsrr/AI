package com.sapjco.mcp.formatters;

/**
 * Exception thrown when ADT response formatting fails.
 */
public class FormattingException extends RuntimeException {

    public FormattingException(String message) {
        super(message);
    }

    public FormattingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an exception for malformed XML responses.
     *
     * @param toolName The tool that returned the response
     * @param cause The underlying parse error
     * @return FormattingException with descriptive message
     */
    public static FormattingException malformedXml(String toolName, Throwable cause) {
        return new FormattingException(
                String.format("Failed to parse XML response from %s: %s", toolName, cause.getMessage()),
                cause
        );
    }

    /**
     * Create an exception for missing required elements in the response.
     *
     * @param toolName The tool that returned the response
     * @param missingElement The element that was expected but not found
     * @return FormattingException with descriptive message
     */
    public static FormattingException missingElement(String toolName, String missingElement) {
        return new FormattingException(
                String.format("Response from %s missing required element: %s", toolName, missingElement)
        );
    }

    /**
     * Create an exception for empty responses.
     *
     * @param toolName The tool that returned the response
     * @return FormattingException with descriptive message
     */
    public static FormattingException emptyResponse(String toolName) {
        return new FormattingException(
                String.format("Received empty response from %s", toolName)
        );
    }
}
