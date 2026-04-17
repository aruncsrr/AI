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
 * Formatter for DebugStep tool responses.
 * Formats step operation results.
 */
@Slf4j
@Component
public class DebugStepFormatter extends AbstractADTFormatter {

    // Patterns for text response
    private static final Pattern OPERATION_PATTERN = Pattern.compile("Step Operation:\\s*(\\w+)");
    private static final Pattern PROCESS_ID_PATTERN = Pattern.compile("Process ID:\\s*([\\w-]+)");
    private static final Pattern SERVER_PATTERN = Pattern.compile("Server:\\s*(\\S+)");

    // Field extractors for XML response
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
                    .build(),
            FieldExtractor.builder("isTerminationPossible")
                    .fromAttribute("isTerminationPossible")
                    .asBoolean()
                    .defaultValue(false)
                    .build()
    );

    @Override
    public String getToolName() {
        return "DebugStep";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Debug Step Result\n");
        sb.append(boxDivider(50)).append("\n\n");

        // Check if this is text response (from handler) or XML
        if (rawResponse.contains("Step Operation:")) {
            return formatTextResponse(rawResponse, sb);
        }

        // Parse XML response
        return formatXmlResponse(rawResponse, sb);
    }

    private String formatTextResponse(String rawResponse, StringBuilder sb) {
        String operation = extractMatch(rawResponse, OPERATION_PATTERN);
        String processId = extractMatch(rawResponse, PROCESS_ID_PATTERN);
        String server = extractMatch(rawResponse, SERVER_PATTERN);

        if (operation != null) {
            sb.append("Operation\n");
            sb.append(divider(35)).append("\n");
            sb.append(String.format("  Type:       %s\n", formatOperationType(operation)));
            sb.append("\n");
        }

        sb.append("Status: Execution paused\n\n");

        if (processId != null || server != null) {
            sb.append("Process Information\n");
            sb.append(divider(35)).append("\n");
            if (processId != null) {
                sb.append(String.format("  Process ID: %s\n", processId));
            }
            if (server != null) {
                sb.append(String.format("  Server:     %s\n", server));
            }
            sb.append("\n");
        }

        appendNextActions(sb);
        return sb.toString();
    }

    private String formatXmlResponse(String rawResponse, StringBuilder sb) {
        Map<String, Object> state = XmlExtractor.extractSingle(rawResponse, DEBUGGER_STATE_FIELDS);

        String processId = (String) state.get("processId");
        String serverName = (String) state.get("serverName");
        Boolean steppingPossible = (Boolean) state.get("isSteppingPossible");

        sb.append("Status: Execution paused\n\n");

        sb.append("Process Information\n");
        sb.append(divider(35)).append("\n");
        if (processId != null) {
            sb.append(String.format("  Process ID:       %s\n", processId));
        }
        if (serverName != null) {
            sb.append(String.format("  Server:           %s\n", serverName));
        }
        if (steppingPossible != null) {
            sb.append(String.format("  Stepping Allowed: %s\n", steppingPossible ? "Yes" : "No"));
        }
        sb.append("\n");

        appendNextActions(sb);
        return sb.toString();
    }

    private void appendNextActions(StringBuilder sb) {
        sb.append("Next Actions\n");
        sb.append(divider(35)).append("\n");
        sb.append("  DebugGetStack           - View current position\n");
        sb.append("  DebugGetVariables       - Inspect variables\n");
        sb.append("  DebugStep(stepOver)     - Next line\n");
        sb.append("  DebugStep(stepInto)     - Enter method\n");
        sb.append("  DebugStep(stepReturn)   - Exit method\n");
        sb.append("  DebugResume             - Continue execution\n");
    }

    private String formatOperationType(String operation) {
        if (operation == null) return "Unknown";
        switch (operation.toLowerCase()) {
            case "stepover": return "Step Over (next line)";
            case "stepinto": return "Step Into (enter method)";
            case "stepreturn": return "Step Return (exit method)";
            default: return operation;
        }
    }

    private String extractMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
