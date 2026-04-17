package com.sapjco.mcp.formatters.write;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatter for CheckSyntax tool responses.
 * Parses syntax check XML results to show errors, warnings, and validation status.
 */
@Slf4j
@Component
public class CheckSyntaxFormatter extends AbstractADTFormatter {

    @Override
    public String getToolName() {
        return "CheckSyntax";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        StringBuilder sb = new StringBuilder();
        sb.append("Syntax Check Result\n");
        sb.append(boxDivider(60)).append("\n\n");

        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            sb.append("Status: VALID\n\n");
            sb.append("No syntax errors found.\n");
            sb.append("The object syntax is valid.");
            return sb.toString();
        }

        // Parse check indicators
        boolean hasErrors = rawResponse.contains("type=\"E\"") ||
                           rawResponse.contains("severity=\"error\"");
        boolean hasWarnings = rawResponse.contains("type=\"W\"") ||
                             rawResponse.contains("severity=\"warning\"");
        boolean checkExecuted = rawResponse.contains("checkExecuted=\"true\"");

        // Try to parse as XML for detailed messages
        List<SyntaxMessage> messages = new ArrayList<>();
        try {
            Document doc = parseXml(rawResponse);
            messages = parseSyntaxMessages(doc);
        } catch (FormattingException e) {
            log.debug("Could not parse syntax response as XML: {}", e.getMessage());
        }

        // Determine overall status
        long errorCount = messages.stream().filter(m -> "E".equals(m.type)).count();
        long warningCount = messages.stream().filter(m -> "W".equals(m.type)).count();

        if (hasErrors || errorCount > 0) {
            errorCount = Math.max(errorCount, 1);
        }
        if (hasWarnings || warningCount > 0) {
            warningCount = Math.max(warningCount, 1);
        }

        boolean syntaxValid = !hasErrors && errorCount == 0;

        if (syntaxValid) {
            if (warningCount > 0) {
                sb.append("Status: VALID (with warnings)\n\n");
            } else {
                sb.append("Status: VALID\n\n");
            }
            sb.append("No syntax errors found.\n");
        } else {
            sb.append("Status: INVALID\n\n");
            sb.append("Syntax errors detected.\n");
        }

        sb.append(String.format("Check Executed: %s\n", checkExecuted ? "Yes" : "Unknown"));
        sb.append(String.format("Errors: %d\n", errorCount));
        sb.append(String.format("Warnings: %d\n", warningCount));

        // Show detailed messages
        if (!messages.isEmpty()) {
            sb.append("\nMessages:\n");
            sb.append(divider(50)).append("\n");

            for (SyntaxMessage msg : messages) {
                String prefix = "E".equals(msg.type) ? "[ERROR]" :
                               "W".equals(msg.type) ? "[WARNING]" : "[INFO]";
                sb.append(String.format("%s %s\n", prefix, msg.text));
                if (msg.objDescr != null && !msg.objDescr.isEmpty()) {
                    sb.append(String.format("        Location: %s\n", msg.objDescr));
                }
                if (msg.line > 0) {
                    sb.append(String.format("        Line %d, Column %d\n", msg.line, msg.column));
                }
                if (msg.suggestion != null && !msg.suggestion.isEmpty()) {
                    sb.append(String.format("        Suggestion: %s\n", msg.suggestion));
                }
            }
        } else if (hasErrors || hasWarnings) {
            // Show raw response if we couldn't parse messages
            sb.append("\nRaw Response:\n");
            sb.append(divider(50)).append("\n");
            sb.append(rawResponse);
        }

        return sb.toString();
    }

    private static class SyntaxMessage {
        String type;
        String text;
        int line;
        int column;
        String uri;
        String objDescr;
        String suggestion;

        SyntaxMessage(String type, String text, int line, int column, String uri, String objDescr, String suggestion) {
            this.type = type;
            this.text = text;
            this.line = line;
            this.column = column;
            this.uri = uri;
            this.objDescr = objDescr;
            this.suggestion = suggestion;
        }
    }

    private List<SyntaxMessage> parseSyntaxMessages(Document doc) {
        List<SyntaxMessage> messages = new ArrayList<>();

        // Try common message element names - "msg" is SAP's actual element name
        for (String tagName : new String[]{"msg", "message", "entry", "finding", "item"}) {
            List<Element> elements = getElementsByTagName(doc, tagName);
            for (Element elem : elements) {
                String type = getAttribute(elem, "type");
                if (type.isEmpty()) {
                    String severity = getAttribute(elem, "severity");
                    type = "error".equalsIgnoreCase(severity) ? "E" :
                           "warning".equalsIgnoreCase(severity) ? "W" : "I";
                }

                // Extract text - handle nested <shortText><txt>...</txt></shortText> structure
                String text = extractMessageText(elem);

                // Extract line/column from href fragment: #start=LINE,COL;end=LINE,COL
                int line = 0;
                int column = 0;
                String uri = getAttribute(elem, "href");
                if (uri.isEmpty()) {
                    uri = getAttribute(elem, "uri");
                }

                if (!uri.isEmpty() && uri.contains("#start=")) {
                    int[] lineCol = parseLineColumnFromHref(uri);
                    line = lineCol[0];
                    column = lineCol[1];
                } else {
                    line = parseIntAttribute(elem, "line", 0);
                    column = parseIntAttribute(elem, "column", 0);
                }

                // Extract object description (method/class context)
                String objDescr = getAttribute(elem, "objDescr");

                // Extract correction hints (suggestions)
                String suggestion = extractCorrectionHint(elem);

                if (!text.isEmpty()) {
                    messages.add(new SyntaxMessage(type, text, line, column, uri, objDescr, suggestion));
                }
            }
        }

        return messages;
    }

    /**
     * Extract message text, handling nested structures like <shortText><txt>...</txt></shortText>
     */
    private String extractMessageText(Element elem) {
        // Try shortText first
        String text = getChildText(elem, "shortText");
        if (!text.isEmpty()) {
            return text;
        }

        // Try nested shortText > txt structure
        List<Element> shortTextElements = getChildElements(elem, "shortText");
        for (Element shortText : shortTextElements) {
            String txtContent = getChildText(shortText, "txt");
            if (!txtContent.isEmpty()) {
                return txtContent;
            }
            // Fallback to direct text content of shortText
            String directContent = shortText.getTextContent().trim();
            if (!directContent.isEmpty()) {
                return directContent;
            }
        }

        // Try messageText
        text = getChildText(elem, "messageText");
        if (!text.isEmpty()) {
            return text;
        }

        // Fallback to direct text content
        return elem.getTextContent().trim();
    }

    /**
     * Parse line and column from href fragment like #start=739,8;end=739,26
     */
    private int[] parseLineColumnFromHref(String href) {
        int[] result = new int[]{0, 0};
        if (href == null || !href.contains("#start=")) {
            return result;
        }

        try {
            int startIdx = href.indexOf("#start=") + 7;
            int endIdx = href.indexOf(";", startIdx);
            if (endIdx == -1) {
                endIdx = href.length();
            }

            String startPart = href.substring(startIdx, endIdx);
            String[] parts = startPart.split(",");
            if (parts.length >= 1) {
                result[0] = Integer.parseInt(parts[0].trim());
            }
            if (parts.length >= 2) {
                result[1] = Integer.parseInt(parts[1].trim());
            }
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            log.debug("Failed to parse line/column from href: {}", href);
        }

        return result;
    }

    /**
     * Extract correction hint (suggestion) from correctionHint elements with kind="I" (insertion)
     */
    private String extractCorrectionHint(Element elem) {
        List<Element> hints = getChildElements(elem, "correctionHint");
        for (Element hint : hints) {
            String kind = getAttribute(hint, "kind");
            // kind="I" is insertion hint (suggested replacement)
            if ("I".equals(kind)) {
                return getAttribute(hint, "word");
            }
        }
        return null;
    }

    private int parseIntAttribute(Element elem, String attrName, int defaultValue) {
        String value = getAttribute(elem, attrName);
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
