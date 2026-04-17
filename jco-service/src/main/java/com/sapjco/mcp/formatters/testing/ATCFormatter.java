package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.ElementExtractor;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formatter for RunATC tool responses.
 * Converts raw ATC (ABAP Test Cockpit) results XML into human-readable findings report.
 */
@Slf4j
@Component
public class ATCFormatter extends AbstractADTFormatter {

    private static final String ADT_NS = "http://www.sap.com/adt/core";

    // ═══════════════════════════════════════════════════════════════════════════
    // ATC Finding Extractor Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    private static final ElementExtractor ATC_FINDING_EXTRACTOR = ElementExtractor.builder("finding")
            .field(FieldExtractor.builder("priority")
                    .fromAttribute("priority")
                    .asInteger()
                    .defaultValue(3)
                    .build())
            .field(FieldExtractor.builder("checkId")
                    .fromAttribute("checkId")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("checkTitle")
                    .fromAttribute("checkTitle")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("messageTitle")
                    .fromAttribute("messageTitle")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("exemptionApproval")
                    .fromAttribute("exemptionApproval")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("uri")
                    .fromNamespacedAttribute(ADT_NS, "uri")
                    .asString()
                    .build())
            .build();

    // ═══════════════════════════════════════════════════════════════════════════
    // ATC Finding Data Class
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parsed ATC finding.
     */
    private static class AtcFinding {
        final int priority;
        final String checkId;
        final String checkTitle;
        final String messageTitle;
        final String uri;
        final int line;
        final int column;
        final String exemptionApproval;

        AtcFinding(int priority, String checkId, String checkTitle, String messageTitle,
                   String uri, int line, int column, String exemptionApproval) {
            this.priority = priority;
            this.checkId = checkId;
            this.checkTitle = checkTitle;
            this.messageTitle = messageTitle;
            this.uri = uri;
            this.line = line;
            this.column = column;
            this.exemptionApproval = exemptionApproval;
        }

        boolean isExempt() {
            return exemptionApproval != null && !exemptionApproval.isEmpty();
        }
    }

    @Override
    public String getToolName() {
        return "RunATC";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<AtcFinding> findings = parseAtcResults(rawResponse);
        return formatAtcResults(findings);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ATC Results Parsing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse ATC worklist results XML.
     */
    private List<AtcFinding> parseAtcResults(String xml) {
        return XmlExtractor.extract(xml, ATC_FINDING_EXTRACTOR)
                .stream()
                .map(this::toAtcFinding)
                .collect(Collectors.toList());
    }

    private AtcFinding toAtcFinding(Map<String, Object> data) {
        String uri = (String) data.get("uri");

        // Extract line/column from URI fragment
        int line = 0, column = 0;
        if (uri != null && uri.contains("#start=")) {
            try {
                String fragment = uri.substring(uri.indexOf("#start=") + 7);
                String[] parts = fragment.split(",");
                if (parts.length >= 1) {
                    line = Integer.parseInt(parts[0]);
                }
                if (parts.length >= 2) {
                    column = Integer.parseInt(parts[1].split(";")[0]);
                }
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        return new AtcFinding(
                (Integer) data.get("priority"),
                (String) data.get("checkId"),
                (String) data.get("checkTitle"),
                (String) data.get("messageTitle"),
                uri,
                line,
                column,
                (String) data.get("exemptionApproval")
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ATC Results Formatting
    // ═══════════════════════════════════════════════════════════════════════════

    private String formatAtcResults(List<AtcFinding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("ATC Results\n");
        sb.append(boxDivider(80)).append("\n");

        if (findings.isEmpty()) {
            sb.append("\n[OK] No findings! Code looks clean.\n");
            return sb.toString();
        }

        // Count by priority
        Map<Integer, Long> priorityCounts = findings.stream()
                .collect(Collectors.groupingBy(f -> f.priority, Collectors.counting()));

        long errors = priorityCounts.getOrDefault(1, 0L);
        long warnings = priorityCounts.getOrDefault(2, 0L);
        long infos = priorityCounts.getOrDefault(3, 0L);

        // Summary
        sb.append(String.format("\nSummary: [E] %d error%s, [W] %d warning%s, [I] %d info%s (%d total)\n\n",
                errors, errors == 1 ? "" : "s",
                warnings, warnings == 1 ? "" : "s",
                infos, infos == 1 ? "" : "s",
                findings.size()));

        // Multi-line format per finding
        for (int i = 0; i < findings.size(); i++) {
            AtcFinding f = findings.get(i);
            String icon = getPriorityIcon(f.priority);

            // Line 1: Icon, number, check title (full, no truncation)
            sb.append(String.format("%s [%d] %s\n", icon, i + 1, f.checkTitle != null ? f.checkTitle : ""));

            // Line 2: Message (indented, full, no truncation)
            sb.append(String.format("   %s\n", f.messageTitle != null ? f.messageTitle : ""));

            // Line 3: Location and status (indented)
            String location = f.line > 0 ? String.valueOf(f.line) : "-";
            String status = f.isExempt() ? "exempt" : "active";
            sb.append(String.format("   Line: %s | Status: %s\n", location, status));

            sb.append("\n");
        }

        return sb.toString();
    }

    private String getPriorityIcon(int priority) {
        switch (priority) {
            case 1: return "[E]";  // Error
            case 2: return "[W]";  // Warning
            case 3: return "[I]";  // Info
            default: return "[ ]";
        }
    }
}
