package com.sapjco.mcp.formatters.debug;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for DebugStepToLine tool responses.
 * Formats step-to-line operation results.
 */
@Slf4j
@Component
public class DebugStepToLineFormatter extends AbstractADTFormatter {

    // Patterns for text response
    private static final Pattern STEP_TYPE_PATTERN = Pattern.compile("Debug Step To Line:\\s*(\\w+)");
    private static final Pattern URI_PATTERN = Pattern.compile("URI:\\s*(/[^\\n]+)");
    private static final Pattern LINE_PATTERN = Pattern.compile("#start=(\\d+)");

    // Field extractors for XML response (if raw XML returned)
    private static final List<FieldExtractor> DEBUGGER_STATE_FIELDS = List.of(
            FieldExtractor.builder("processId")
                    .fromAttribute("processId")
                    .asString()
                    .build(),
            FieldExtractor.builder("serverName")
                    .fromAttribute("serverName")
                    .asString()
                    .build(),
            FieldExtractor.builder("isSteppingPossible")
                    .fromAttribute("isSteppingPossible")
                    .asBoolean()
                    .defaultValue(false)
                    .build()
    );

    @Override
    public String getToolName() {
        return "DebugStepToLine";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Debug Step To Line Result\n");
        sb.append(boxDivider(60)).append("\n\n");

        // Check if this is text response (from handler)
        if (rawResponse.contains("Debug Step To Line:") || rawResponse.contains("URI:")) {
            return formatTextResponse(rawResponse, sb);
        }

        // Try to parse as XML
        return formatXmlResponse(rawResponse, sb);
    }

    private String formatTextResponse(String rawResponse, StringBuilder sb) {
        String stepType = extractMatch(rawResponse, STEP_TYPE_PATTERN);
        String uri = extractMatch(rawResponse, URI_PATTERN);
        String line = extractMatch(rawResponse, LINE_PATTERN);

        sb.append("Operation\n");
        sb.append(divider(40)).append("\n");
        if (stepType != null) {
            sb.append(String.format("  Type:   %s\n", formatStepType(stepType)));
        }
        if (line != null) {
            sb.append(String.format("  Line:   %s\n", line));
        }
        sb.append("\n");

        if (uri != null) {
            sb.append("Target Location\n");
            sb.append(divider(40)).append("\n");
            sb.append(String.format("  %s\n\n", uri));
        }

        sb.append("Status: Execution moved to target line\n\n");

        appendNextActions(sb);
        return sb.toString();
    }

    private String formatXmlResponse(String rawResponse, StringBuilder sb) {
        Map<String, Object> state = XmlExtractor.extractSingle(rawResponse, DEBUGGER_STATE_FIELDS);

        String processId = (String) state.get("processId");
        String serverName = (String) state.get("serverName");

        sb.append("Status: Execution moved to target line\n\n");

        if (processId != null || serverName != null) {
            sb.append("Process Information\n");
            sb.append(divider(40)).append("\n");
            if (processId != null) {
                sb.append(String.format("  Process ID: %s\n", processId));
            }
            if (serverName != null) {
                sb.append(String.format("  Server:     %s\n", serverName));
            }
            sb.append("\n");
        }

        appendNextActions(sb);
        return sb.toString();
    }

    private void appendNextActions(StringBuilder sb) {
        sb.append("Next Actions\n");
        sb.append(divider(40)).append("\n");
        sb.append("  DebugGetStack       - View current position\n");
        sb.append("  DebugGetVariables   - Inspect variables\n");
        sb.append("  DebugStep           - Continue stepping\n");
        sb.append("  DebugResume         - Continue execution\n");
    }

    private String formatStepType(String stepType) {
        if (stepType == null) return "Unknown";
        switch (stepType.toLowerCase()) {
            case "stepruntoline": return "Run To Line (execute until)";
            case "stepjumptoline": return "Jump To Line (skip to)";
            default: return stepType;
        }
    }

    private String extractMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
