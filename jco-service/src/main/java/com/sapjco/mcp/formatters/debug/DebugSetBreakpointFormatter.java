package com.sapjco.mcp.formatters.debug;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for DebugSetBreakpoint tool responses.
 * Formats breakpoint creation response.
 */
@Slf4j
@Component
public class DebugSetBreakpointFormatter extends AbstractADTFormatter {

    // Patterns to extract key information
    private static final Pattern OBJECT_PATTERN = Pattern.compile("Object:\\s*([^\\(]+)\\s*\\(([^)]+)\\)");
    private static final Pattern LINE_PATTERN = Pattern.compile("Line:\\s*(\\d+)");
    private static final Pattern USER_PATTERN = Pattern.compile("User:\\s*(\\S+)");
    private static final Pattern TERMINAL_ID_PATTERN = Pattern.compile("Terminal ID:\\s*([A-F0-9]+)");
    private static final Pattern IDE_ID_PATTERN = Pattern.compile("IDE ID:\\s*([A-F0-9]+)");
    private static final Pattern BREAKPOINT_ID_PATTERN = Pattern.compile("Breakpoint ID:\\s*([^\\n]+)");
    private static final Pattern XML_ID_PATTERN = Pattern.compile("id=\"([^\"]+)\"");
    private static final Pattern XML_URI_PATTERN = Pattern.compile("adtcore:uri=\"([^\"]+)\"");

    @Override
    public String getToolName() {
        return "DebugSetBreakpoint";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Breakpoint Set\n");
        sb.append(boxDivider(60)).append("\n\n");

        // Extract structured information
        Matcher objectMatcher = OBJECT_PATTERN.matcher(rawResponse);
        String line = extractMatch(rawResponse, LINE_PATTERN);
        String user = extractMatch(rawResponse, USER_PATTERN);
        String terminalId = extractMatch(rawResponse, TERMINAL_ID_PATTERN);
        String ideId = extractMatch(rawResponse, IDE_ID_PATTERN);
        String breakpointId = extractMatch(rawResponse, BREAKPOINT_ID_PATTERN);

        // If breakpoint ID not in text format, try XML
        if (breakpointId == null) {
            breakpointId = extractMatch(rawResponse, XML_ID_PATTERN);
        }

        sb.append("Breakpoint Details\n");
        sb.append(divider(40)).append("\n");
        if (objectMatcher.find()) {
            sb.append(String.format("  Object:         %s\n", objectMatcher.group(1).trim()));
            sb.append(String.format("  Type/Include:   %s\n", objectMatcher.group(2).trim()));
        }
        if (line != null) {
            sb.append(String.format("  Line:           %s\n", line));
        }
        if (user != null) {
            sb.append(String.format("  User:           %s\n", user));
        }
        if (breakpointId != null) {
            sb.append(String.format("  Breakpoint ID:  %s\n", truncate(breakpointId, 40)));
        }
        sb.append("\n");

        sb.append("Session IDs\n");
        sb.append(divider(40)).append("\n");
        if (terminalId != null) {
            sb.append(String.format("  Terminal ID:    %s\n", terminalId));
        }
        if (ideId != null) {
            sb.append(String.format("  IDE ID:         %s\n", ideId));
        }
        sb.append("\n");

        // Extract URI from XML if present
        String uri = extractMatch(rawResponse, XML_URI_PATTERN);
        if (uri != null) {
            sb.append("Location URI\n");
            sb.append(divider(40)).append("\n");
            sb.append(String.format("  %s\n\n", uri));
        }

        sb.append("Status: Breakpoint added to active debug session\n");
        sb.append("Use DebugResume to continue execution to this breakpoint.\n");

        return sb.toString();
    }

    private String extractMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
