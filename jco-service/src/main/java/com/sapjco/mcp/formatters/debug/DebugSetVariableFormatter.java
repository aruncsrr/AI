package com.sapjco.mcp.formatters.debug;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.ElementExtractor;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for DebugSetVariable tool responses.
 * Formats variable modification results.
 */
@Slf4j
@Component
public class DebugSetVariableFormatter extends AbstractADTFormatter {

    // Patterns for text response
    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile("Debug Set Variable:\\s*(\\S+)\\s*=\\s*(.+)");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("Variable:\\s*(\\S+)");
    private static final Pattern VALUE_PATTERN = Pattern.compile("Value:\\s*(.+)");

    // Variable extractor for XML response
    private static final ElementExtractor VARIABLE_EXTRACTOR = ElementExtractor.builder("STPDA_ADT_VARIABLE")
            .field(FieldExtractor.builder("id")
                    .fromChildText("ID")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("name")
                    .fromChildText("NAME")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("value")
                    .fromChildText("VALUE")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("declaredType")
                    .fromChildText("DECLARED_TYPE_NAME")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "DebugSetVariable";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Debug Set Variable Result\n");
        sb.append(boxDivider(50)).append("\n\n");

        // Check if this is text response (from handler)
        if (rawResponse.contains("Debug Set Variable:")) {
            return formatTextResponse(rawResponse, sb);
        }

        // Try to parse as XML
        return formatXmlResponse(rawResponse, sb);
    }

    private String formatTextResponse(String rawResponse, StringBuilder sb) {
        Matcher assignmentMatcher = ASSIGNMENT_PATTERN.matcher(rawResponse);

        if (assignmentMatcher.find()) {
            String varName = assignmentMatcher.group(1);
            String newValue = assignmentMatcher.group(2).trim();

            sb.append("Variable Modified\n");
            sb.append(divider(35)).append("\n");
            sb.append(String.format("  Name:      %s\n", varName));
            sb.append(String.format("  New Value: %s\n", newValue));
            sb.append("\n");
        } else {
            // Try alternate patterns
            String varName = extractMatch(rawResponse, VARIABLE_PATTERN);
            String value = extractMatch(rawResponse, VALUE_PATTERN);

            if (varName != null || value != null) {
                sb.append("Variable Modified\n");
                sb.append(divider(35)).append("\n");
                if (varName != null) {
                    sb.append(String.format("  Name:      %s\n", varName));
                }
                if (value != null) {
                    sb.append(String.format("  New Value: %s\n", value));
                }
                sb.append("\n");
            }
        }

        sb.append("Status: Variable value updated successfully\n\n");

        sb.append("Next Actions\n");
        sb.append(divider(35)).append("\n");
        sb.append("  DebugGetVariables   - Verify new value\n");
        sb.append("  DebugStep           - Continue stepping\n");
        sb.append("  DebugResume         - Continue execution\n");

        return sb.toString();
    }

    private String formatXmlResponse(String rawResponse, StringBuilder sb) {
        List<Map<String, Object>> variables = XmlExtractor.extract(rawResponse, VARIABLE_EXTRACTOR);

        if (variables.isEmpty()) {
            sb.append("Status: Variable modification requested\n\n");
            sb.append("Use DebugGetVariables to verify the new value.\n");
            return sb.toString();
        }

        // Show first variable (the one that was modified)
        Map<String, Object> var = variables.get(0);

        sb.append("Variable Updated\n");
        sb.append(divider(35)).append("\n");
        if (var.get("name") != null) {
            sb.append(String.format("  Name:  %s\n", var.get("name")));
        }
        if (var.get("declaredType") != null) {
            sb.append(String.format("  Type:  %s\n", var.get("declaredType")));
        }
        if (var.get("value") != null) {
            sb.append(String.format("  Value: %s\n", var.get("value")));
        }
        sb.append("\n");

        sb.append("Status: Variable value updated successfully\n\n");

        sb.append("Next Actions\n");
        sb.append(divider(35)).append("\n");
        sb.append("  DebugGetVariables   - View all variables\n");
        sb.append("  DebugStep           - Continue stepping\n");
        sb.append("  DebugResume         - Continue execution\n");

        return sb.toString();
    }

    private String extractMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
