package com.sapjco.mcp.formatters.debug;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for DebugStartSession tool responses.
 * Formats combined response with session info, breakpoint, terminal/ide IDs.
 */
@Slf4j
@Component
public class DebugStartSessionFormatter extends AbstractADTFormatter {

    // Patterns to extract key information from the response
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("Session ID:\\s*([\\w-]+)");
    private static final Pattern DEBUGGEE_ID_PATTERN = Pattern.compile("Debuggee ID:\\s*([\\w-]+)");
    private static final Pattern TERMINAL_ID_PATTERN = Pattern.compile("Terminal ID:\\s*([A-F0-9]+)");
    private static final Pattern IDE_ID_PATTERN = Pattern.compile("IDE ID:\\s*([A-F0-9]+)");
    private static final Pattern OBJECT_PATTERN = Pattern.compile("Object:\\s*([^\\(]+)\\s*\\(([^)]+)\\)");
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("Include:\\s*(\\w+)");
    private static final Pattern URI_PATTERN = Pattern.compile("URI:\\s*(/[^\\n]+)");
    private static final Pattern LINE_PATTERN = Pattern.compile("Line:\\s*(\\d+)");

    @Override
    public String getToolName() {
        return "DebugStartSession";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Debug Session Started\n");
        sb.append(boxDivider(60)).append("\n\n");

        // Extract session information
        String sessionId = extractMatch(rawResponse, SESSION_ID_PATTERN);
        String debuggeeId = extractMatch(rawResponse, DEBUGGEE_ID_PATTERN);
        String terminalId = extractMatch(rawResponse, TERMINAL_ID_PATTERN);
        String ideId = extractMatch(rawResponse, IDE_ID_PATTERN);

        sb.append("Session Information\n");
        sb.append(divider(40)).append("\n");
        if (sessionId != null) {
            sb.append(String.format("  Session ID:   %s\n", sessionId));
        }
        if (debuggeeId != null) {
            sb.append(String.format("  Debuggee ID:  %s\n", debuggeeId));
        }
        if (terminalId != null) {
            sb.append(String.format("  Terminal ID:  %s\n", terminalId));
        }
        if (ideId != null) {
            sb.append(String.format("  IDE ID:       %s\n", ideId));
        }
        sb.append("\n");

        // Extract breakpoint information
        Matcher objectMatcher = OBJECT_PATTERN.matcher(rawResponse);
        String includeName = extractMatch(rawResponse, INCLUDE_PATTERN);
        String uri = extractMatch(rawResponse, URI_PATTERN);
        String line = extractMatch(rawResponse, LINE_PATTERN);

        sb.append("Breakpoint Location\n");
        sb.append(divider(40)).append("\n");
        if (objectMatcher.find()) {
            sb.append(String.format("  Object:       %s\n", objectMatcher.group(1).trim()));
            sb.append(String.format("  Type:         %s\n", objectMatcher.group(2).trim()));
        }
        if (includeName != null) {
            sb.append(String.format("  Include:      %s\n", includeName));
        }
        if (uri != null) {
            sb.append(String.format("  URI:          %s\n", uri));
        }
        if (line != null) {
            sb.append(String.format("  Line:         %s\n", line));
        }
        sb.append("\n");

        // Status
        if (rawResponse.contains("Attached and paused") || rawResponse.contains("paused at breakpoint")) {
            sb.append("Status: Attached and paused at breakpoint\n\n");
        }

        // Next steps
        sb.append("Available Commands\n");
        sb.append(divider(40)).append("\n");
        sb.append("  DebugGetStack       - View call stack\n");
        sb.append("  DebugGetVariables   - Inspect variables\n");
        sb.append("  DebugStep           - Step execution\n");
        sb.append("  DebugSetVariable    - Modify variables\n");
        sb.append("  DebugResume         - Continue execution\n");
        sb.append("  DestroySession      - Clean up session\n");

        return sb.toString();
    }

    private String extractMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
