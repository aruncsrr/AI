package com.sapjco.mcp.formatters.write;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

/**
 * Formatter for ActivateObject tool responses.
 * Parses XML activation results including success/failure status and messages.
 */
@Slf4j
@Component
public class ActivateObjectFormatter extends AbstractADTFormatter {

    @Override
    public String getToolName() {
        return "ActivateObject";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        StringBuilder sb = new StringBuilder();
        sb.append("Activation Result\n");
        sb.append(boxDivider(60)).append("\n\n");

        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            sb.append("Status: SUCCESS\n\n");
            sb.append("The object was activated successfully.\n");
            sb.append("The object is now active and can be used in the system.");
            return sb.toString();
        }

        // Try to parse as XML for detailed results
        try {
            Document doc = parseXml(rawResponse);
            return formatActivationResponse(sb, doc);
        } catch (FormattingException e) {
            // Not valid XML, check for error indicators in text
            if (containsError(rawResponse)) {
                sb.append("Status: FAILED\n\n");
                sb.append("Error Details:\n");
                sb.append(rawResponse);
            } else {
                sb.append("Status: SUCCESS\n\n");
                sb.append("The object was activated successfully.\n");
                sb.append("The object is now active and can be used in the system.");
            }
            return sb.toString();
        }
    }

    private String formatActivationResponse(StringBuilder sb, Document doc) {
        // Check for activation messages
        List<Element> messages = getElementsByTagName(doc, "message");
        List<Element> results = getElementsByTagName(doc, "result");
        List<Element> entries = getElementsByTagName(doc, "entry");

        boolean hasErrors = false;
        boolean hasWarnings = false;
        int errorCount = 0;
        int warningCount = 0;

        // Process messages
        for (Element message : messages) {
            String type = getAttribute(message, "type");
            if ("E".equals(type) || "error".equalsIgnoreCase(type)) {
                hasErrors = true;
                errorCount++;
            } else if ("W".equals(type) || "warning".equalsIgnoreCase(type)) {
                hasWarnings = true;
                warningCount++;
            }
        }

        // Check entries for error types
        for (Element entry : entries) {
            String severity = getAttribute(entry, "severity");
            if ("error".equalsIgnoreCase(severity)) {
                hasErrors = true;
                errorCount++;
            } else if ("warning".equalsIgnoreCase(severity)) {
                hasWarnings = true;
                warningCount++;
            }
        }

        // Format status
        if (hasErrors) {
            sb.append("Status: FAILED\n\n");
            sb.append(String.format("Errors: %d\n", errorCount));
            if (hasWarnings) {
                sb.append(String.format("Warnings: %d\n", warningCount));
            }
            sb.append("\nError Details:\n");
            sb.append(divider(40)).append("\n");
            appendMessages(sb, messages, entries, "E", "error");
        } else if (hasWarnings) {
            sb.append("Status: SUCCESS (with warnings)\n\n");
            sb.append(String.format("Warnings: %d\n", warningCount));
            sb.append("\nThe object was activated successfully.\n");
            sb.append("\nWarnings:\n");
            sb.append(divider(40)).append("\n");
            appendMessages(sb, messages, entries, "W", "warning");
        } else {
            sb.append("Status: SUCCESS\n\n");
            sb.append("The object was activated successfully.\n");
            sb.append("The object is now active and can be used in the system.");
        }

        return sb.toString();
    }

    private void appendMessages(StringBuilder sb, List<Element> messages, List<Element> entries,
                                 String typeCode, String severity) {
        for (Element message : messages) {
            String type = getAttribute(message, "type");
            if (typeCode.equals(type) || severity.equalsIgnoreCase(type)) {
                String text = message.getTextContent().trim();
                if (!text.isEmpty()) {
                    sb.append("- ").append(text).append("\n");
                }
            }
        }

        for (Element entry : entries) {
            String entrySeverity = getAttribute(entry, "severity");
            if (severity.equalsIgnoreCase(entrySeverity)) {
                String text = getChildText(entry, "shortText");
                if (text.isEmpty()) {
                    text = entry.getTextContent().trim();
                }
                if (!text.isEmpty()) {
                    sb.append("- ").append(text).append("\n");
                }
            }
        }
    }

    private boolean containsError(String response) {
        return response.contains("<error") ||
               response.contains("type=\"E\"") ||
               response.contains("severity=\"error\"") ||
               response.contains("<exception");
    }
}
