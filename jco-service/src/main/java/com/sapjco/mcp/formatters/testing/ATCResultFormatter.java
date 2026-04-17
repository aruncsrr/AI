package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.ElementExtractor;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Formatter for GetATCResult tool responses.
 * Converts a single ATC result's findings XML into a human-readable report.
 * Reuses the ATC finding format from ATCFormatter, adding documentation link extraction.
 */
@Slf4j
@Component
public class ATCResultFormatter extends AbstractADTFormatter {

    private static final String ADT_NS = "http://www.sap.com/adt/core";

    private static final ElementExtractor ATC_FINDING_EXTRACTOR = ElementExtractor.builder("finding")
            .field(FieldExtractor.builder("priority")
                    .fromAttribute("priority")
                    .asInteger()
                    .defaultValue(3)
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
            .field(FieldExtractor.builder("processor")
                    .fromAttribute("processor")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("uri")
                    .fromNamespacedAttribute(ADT_NS, "uri")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("location")
                    .fromAttribute("location")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("docLink")
                    .fromChildAttribute("link", "href")
                    .asString()
                    .build())
            .build();

    private static class AtcFinding {
        final int priority;
        final String checkTitle;
        final String messageTitle;
        final int line;
        final String exemptionApproval;
        final String processor;
        final String objectName;
        final String docLink;

        AtcFinding(int priority, String checkTitle, String messageTitle,
                   int line, String exemptionApproval, String processor,
                   String objectName, String docLink) {
            this.priority = priority;
            this.checkTitle = checkTitle;
            this.messageTitle = messageTitle;
            this.line = line;
            this.exemptionApproval = exemptionApproval;
            this.processor = processor;
            this.objectName = objectName;
            this.docLink = docLink;
        }

        boolean isExempt() {
            return exemptionApproval != null
                    && !exemptionApproval.isEmpty()
                    && !"-".equals(exemptionApproval);
        }
    }

    @Override
    public String getToolName() {
        return "GetATCResult";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<AtcFinding> findings = XmlExtractor.extract(rawResponse, ATC_FINDING_EXTRACTOR)
                .stream()
                .map(this::toAtcFinding)
                .toList();

        return formatFindings(findings);
    }

    private AtcFinding toAtcFinding(Map<String, Object> data) {
        // Try adtcore:uri first, fall back to atcfinding:location
        String uri = (String) data.get("uri");
        String location = (String) data.get("location");
        String lineSource = (uri != null && uri.contains("#start=")) ? uri
                : (location != null && location.contains("#start=")) ? location : null;

        int line = 0;
        if (lineSource != null) {
            try {
                int startIdx = lineSource.indexOf("#start=") + 7;
                int commaIdx = lineSource.indexOf(',', startIdx);
                String lineStr = (commaIdx > 0) ? lineSource.substring(startIdx, commaIdx) : lineSource.substring(startIdx);
                line = Integer.parseInt(lineStr);
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        // Extract object name from location or uri path
        String objectName = extractObjectName(location != null ? location : uri);

        return new AtcFinding(
                (Integer) data.get("priority"),
                (String) data.get("checkTitle"),
                (String) data.get("messageTitle"),
                line,
                (String) data.get("exemptionApproval"),
                (String) data.get("processor"),
                objectName,
                (String) data.get("docLink")
        );
    }

    /**
     * Extract the ABAP object name from a location or URI path.
     * E.g., "/sap/bc/adt/oo/classes/cl_test/source/main#start=42,5" → "CL_TEST"
     * E.g., "/sap/bc/adt/oo/classes/cl_test/includes/testclasses#start=150,0" → "CL_TEST"
     */
    private String extractObjectName(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        // Remove fragment
        String clean = path.contains("#") ? path.substring(0, path.indexOf('#')) : path;
        // Known patterns: /sap/bc/adt/oo/classes/{name}/..., /sap/bc/adt/programs/programs/{name}/...
        String[] segments = clean.split("/");
        // Find the segment after "classes", "interfaces", "programs", "groups"
        for (int i = 0; i < segments.length - 1; i++) {
            String seg = segments[i];
            if ("classes".equals(seg) || "interfaces".equals(seg) || "programs".equals(seg) || "groups".equals(seg)) {
                return segments[i + 1].toUpperCase();
            }
        }
        return null;
    }

    private String formatFindings(List<AtcFinding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("ATC Result Findings\n");
        sb.append(boxDivider(80)).append("\n");

        if (findings.isEmpty()) {
            sb.append("\n[OK] No findings! Code looks clean.\n");
            return sb.toString();
        }

        // Count by priority and doc links in a single pass
        long errors = 0, warnings = 0, infos = 0, prio4 = 0, docLinkCount = 0;
        Map<String, List<AtcFinding>> byObject = new LinkedHashMap<>();
        for (AtcFinding f : findings) {
            switch (f.priority) {
                case 1 -> errors++;
                case 2 -> warnings++;
                case 3 -> infos++;
                case 4 -> prio4++;
            }
            if (f.docLink != null && !f.docLink.isEmpty()) {
                docLinkCount++;
            }
            byObject.computeIfAbsent(
                    f.objectName != null ? f.objectName : "(unknown)",
                    k -> new ArrayList<>()).add(f);
        }

        // Summary
        sb.append(String.format("\nSummary: [E] %d error%s, [W] %d warning%s, [I] %d info%s",
                errors, errors == 1 ? "" : "s",
                warnings, warnings == 1 ? "" : "s",
                infos, infos == 1 ? "" : "s"));
        if (prio4 > 0) {
            sb.append(String.format(", [P4] %d", prio4));
        }
        sb.append(String.format(" (%d total)\n", findings.size()));

        if (docLinkCount > 0) {
            sb.append(String.format("Documentation links: %d (use GetATCFindingDocumentation to view)\n", docLinkCount));
        }
        sb.append("\n");

        int globalIndex = 0;
        for (Map.Entry<String, List<AtcFinding>> entry : byObject.entrySet()) {
            sb.append(String.format("--- %s ---\n", entry.getKey()));

            for (AtcFinding f : entry.getValue()) {
                globalIndex++;
                String icon = getPriorityIcon(f.priority);

                sb.append(String.format("%s [%d] %s\n", icon, globalIndex, f.checkTitle != null ? f.checkTitle : ""));
                sb.append(String.format("   %s\n", f.messageTitle != null ? f.messageTitle : ""));

                String locationStr = f.line > 0 ? String.valueOf(f.line) : "-";
                String status = f.isExempt() ? "exempt" : "active";
                sb.append(String.format("   Line: %s | Status: %s", locationStr, status));

                if (f.processor != null && !f.processor.isEmpty()) {
                    sb.append(String.format(" | Processor: %s", f.processor));
                }
                sb.append("\n");

                if (f.docLink != null && !f.docLink.isEmpty()) {
                    sb.append(String.format("   Doc: %s\n", f.docLink));
                }

                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String getPriorityIcon(int priority) {
        return switch (priority) {
            case 1 -> "[E]";
            case 2 -> "[W]";
            case 3 -> "[I]";
            case 4 -> "[P4]";
            default -> "[ ]";
        };
    }
}
