package com.sapjco.mcp.formatters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry for ADT formatters.
 * Maps tool names to their corresponding formatters.
 * Spring-managed - automatically discovers and registers all ADTFormatter beans.
 */
@Slf4j
@Component
public class ADTFormatterRegistry {

    private final Map<String, ADTFormatter> formatters = new HashMap<>();

    /**
     * Constructor that auto-discovers and registers all ADTFormatter beans.
     *
     * @param formatterList All ADTFormatter beans found by Spring
     */
    public ADTFormatterRegistry(List<ADTFormatter> formatterList) {
        for (ADTFormatter formatter : formatterList) {
            String toolName = formatter.getToolName();
            if (formatters.containsKey(toolName)) {
                log.warn("Duplicate formatter for tool '{}': {} replaces {}",
                        toolName, formatter.getClass().getSimpleName(),
                        formatters.get(toolName).getClass().getSimpleName());
            }
            formatters.put(toolName, formatter);
            log.debug("Registered formatter for tool '{}': {}", toolName, formatter.getClass().getSimpleName());
        }
        log.info("ADTFormatterRegistry initialized with {} formatters", formatters.size());
    }

    /**
     * Get a formatter for a specific tool.
     *
     * @param toolName The tool name (e.g., "GetWhereUsed", "RunAbapUnit")
     * @return The formatter, or empty if no formatter is registered for this tool
     */
    public Optional<ADTFormatter> getFormatter(String toolName) {
        return Optional.ofNullable(formatters.get(toolName));
    }

    /**
     * Check if a formatter exists for a tool.
     *
     * @param toolName The tool name
     * @return true if a formatter is registered
     */
    public boolean hasFormatter(String toolName) {
        return formatters.containsKey(toolName);
    }

    /**
     * Get all registered tool names.
     *
     * @return Unmodifiable set of tool names with formatters
     */
    public Set<String> getRegisteredTools() {
        return Collections.unmodifiableSet(formatters.keySet());
    }

    /**
     * Format a raw response using the appropriate formatter.
     *
     * @param toolName The tool that produced the response
     * @param rawResponse The raw response to format
     * @return Formatted output
     * @throws FormattingException if no formatter exists or formatting fails
     */
    public String format(String toolName, String rawResponse) throws FormattingException {
        ADTFormatter formatter = formatters.get(toolName);
        if (formatter == null) {
            throw new FormattingException(String.format(
                    "No formatter registered for tool '%s'. Supported tools: %s",
                    toolName, String.join(", ", getSortedToolNames())
            ));
        }
        return formatter.format(rawResponse);
    }

    /**
     * Get sorted list of tool names for error messages.
     */
    private List<String> getSortedToolNames() {
        List<String> names = new ArrayList<>(formatters.keySet());
        Collections.sort(names);
        return names;
    }
}
