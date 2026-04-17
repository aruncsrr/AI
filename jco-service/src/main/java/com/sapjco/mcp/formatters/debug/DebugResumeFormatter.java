package com.sapjco.mcp.formatters.debug;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Formatter for DebugResume tool responses.
 * Formats resume operation results.
 */
@Slf4j
@Component
public class DebugResumeFormatter extends AbstractADTFormatter {

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
                    .build(),
            FieldExtractor.builder("isTerminationPossible")
                    .fromAttribute("isTerminationPossible")
                    .asBoolean()
                    .defaultValue(false)
                    .build()
    );

    @Override
    public String getToolName() {
        return "DebugResume";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Debug Resume\n");
        sb.append(boxDivider(50)).append("\n\n");

        // Check if this is text response (from handler)
        if (rawResponse.contains("Execution Resumed") || rawResponse.contains("Process will continue")) {
            return formatTextResponse(rawResponse, sb);
        }

        // Try to parse as XML
        return formatXmlResponse(rawResponse, sb);
    }

    private String formatTextResponse(String rawResponse, StringBuilder sb) {
        sb.append("Status: Execution Resumed\n\n");

        sb.append("The process will continue until:\n");
        sb.append(divider(35)).append("\n");
        sb.append("  - Next breakpoint is hit\n");
        sb.append("  - Program completes\n");
        sb.append("  - Error/exception occurs\n\n");

        sb.append("If stopped at another breakpoint:\n");
        sb.append(divider(35)).append("\n");
        sb.append("  DebugGetStack       - View new position\n");
        sb.append("  DebugGetVariables   - Inspect variables\n");
        sb.append("  DebugStep           - Continue stepping\n");
        sb.append("  DebugResume         - Continue again\n\n");

        sb.append("When finished:\n");
        sb.append(divider(35)).append("\n");
        sb.append("  DestroySession      - Clean up resources\n");

        return sb.toString();
    }

    private String formatXmlResponse(String rawResponse, StringBuilder sb) {
        Map<String, Object> state = XmlExtractor.extractSingle(rawResponse, DEBUGGER_STATE_FIELDS);

        String processId = (String) state.get("processId");
        String serverName = (String) state.get("serverName");
        Boolean terminationPossible = (Boolean) state.get("isTerminationPossible");

        sb.append("Status: Execution Resumed\n\n");

        if (processId != null || serverName != null) {
            sb.append("Process Information\n");
            sb.append(divider(35)).append("\n");
            if (processId != null) {
                sb.append(String.format("  Process ID: %s\n", processId));
            }
            if (serverName != null) {
                sb.append(String.format("  Server:     %s\n", serverName));
            }
            sb.append("\n");
        }

        sb.append("Execution will continue until:\n");
        sb.append(divider(35)).append("\n");
        sb.append("  - Next breakpoint is hit\n");
        sb.append("  - Program completes\n");
        sb.append("  - Error/exception occurs\n");

        return sb.toString();
    }
}
