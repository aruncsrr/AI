package com.sapjco.mcp.formatters;

/**
 * Interface for ADT response formatters.
 * Each formatter converts raw SAP ADT XML/text responses into human-readable format.
 */
public interface ADTFormatter {

    /**
     * Get the tool name this formatter handles.
     * Used by the registry to map tool names to formatters.
     *
     * @return Tool name (e.g., "GetWhereUsed", "RunAbapUnit")
     */
    String getToolName();

    /**
     * Format a raw ADT response into human-readable text.
     *
     * @param rawResponse The raw XML or text response from SAP ADT
     * @return Formatted human-readable output
     * @throws FormattingException if the response cannot be parsed or formatted
     */
    String format(String rawResponse) throws FormattingException;
}
