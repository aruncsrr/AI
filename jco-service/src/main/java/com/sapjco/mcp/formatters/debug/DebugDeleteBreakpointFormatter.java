package com.sapjco.mcp.formatters.debug;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for DebugDeleteBreakpoint tool responses.
 * Formats breakpoint deletion confirmation.
 */
@Slf4j
@Component
public class DebugDeleteBreakpointFormatter extends AbstractADTFormatter {

    // Patterns to extract key information
    private static final Pattern TERMINAL_PATTERN = Pattern.compile("Terminal:\\s*([A-F0-9]+)");
    private static final Pattern IDE_PATTERN = Pattern.compile("IDE:\\s*([A-F0-9]+)");

    @Override
    public String getToolName() {
        return "DebugDeleteBreakpoint";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Breakpoints Deleted\n");
        sb.append(boxDivider(50)).append("\n\n");

        // Extract IDs
        String terminalId = extractMatch(rawResponse, TERMINAL_PATTERN);
        String ideId = extractMatch(rawResponse, IDE_PATTERN);

        sb.append("Cleanup Details\n");
        sb.append(divider(35)).append("\n");
        if (terminalId != null) {
            sb.append(String.format("  Terminal ID:  %s\n", terminalId));
        }
        if (ideId != null) {
            sb.append(String.format("  IDE ID:       %s\n", ideId));
        }
        sb.append("\n");

        // Check for success indication
        if (rawResponse.contains("cleaned up successfully") || rawResponse.contains("Deleted")) {
            sb.append("Status: Cleanup completed successfully\n\n");
        } else {
            sb.append("Status: Breakpoint deletion requested\n\n");
        }

        sb.append("Actions Performed\n");
        sb.append(divider(35)).append("\n");
        sb.append("  - Debug listener stopped\n");
        sb.append("  - Breakpoints removed\n");
        sb.append("  - Resources released\n");

        return sb.toString();
    }

    private String extractMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
