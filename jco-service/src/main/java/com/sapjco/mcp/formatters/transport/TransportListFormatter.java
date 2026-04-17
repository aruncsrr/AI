package com.sapjco.mcp.formatters.transport;

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
 * Formatter for ListTransportRequests tool responses.
 * Converts raw transport organizer XML into human-readable transport list.
 */
@Slf4j
@Component
public class TransportListFormatter extends AbstractADTFormatter {

    // ElementExtractor for transport request elements
    private static final ElementExtractor REQUEST_EXTRACTOR = ElementExtractor.builder("request")
            .field(FieldExtractor.builder("number")
                    .fromAttribute("number")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("owner")
                    .fromAttribute("owner")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("description")
                    .fromAttribute("desc")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("type")
                    .fromAttribute("type")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("status")
                    .fromAttribute("status")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("target")
                    .fromAttribute("target")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "ListTransportRequests";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatTransportList(rawResponse, null, null, null);
    }

    /**
     * Format with additional context about the filters.
     */
    public String format(String rawResponse, String user, String trstatus, String trfunction) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatTransportList(rawResponse, user, trstatus, trfunction);
    }

    // ========================================================================
    // Transport List Formatting
    // ========================================================================

    private String formatTransportList(String xml, String user, String trstatus, String trfunction) {
        List<Map<String, Object>> requests = XmlExtractor.extract(xml, REQUEST_EXTRACTOR);

        StringBuilder sb = new StringBuilder();
        sb.append("Transport Requests\n");
        if (user != null) sb.append(String.format("User: %s\n", user));
        if (trstatus != null) sb.append(String.format("Status filter: %s\n", formatStatus(trstatus)));
        if (trfunction != null) sb.append(String.format("Type filter: %s\n", formatType(trfunction)));
        sb.append(boxDivider(90)).append("\n");

        if (requests.isEmpty()) {
            sb.append("\nNo transport requests found matching the criteria.\n");
            return sb.toString();
        }

        // Group by status
        Map<String, Long> statusCounts = requests.stream()
                .collect(Collectors.groupingBy(
                        r -> (String) r.getOrDefault("status", "unknown"),
                        Collectors.counting()));

        // Summary
        sb.append(String.format("\nFound %d transport request%s\n", requests.size(), requests.size() == 1 ? "" : "s"));
        sb.append("By Status: ");
        sb.append(statusCounts.entrySet().stream()
                .map(e -> String.format("%d %s", e.getValue(), formatStatus(e.getKey())))
                .collect(Collectors.joining(", ")));
        sb.append("\n\n");

        // Requests table
        sb.append(String.format("%-12s %-10s %-8s %-12s %-10s %s\n",
                "Request", "Type", "Status", "Owner", "Target", "Description"));
        sb.append(boxDivider(90)).append("\n");

        for (Map<String, Object> req : requests) {
            String number = (String) req.getOrDefault("number", "");
            String type = (String) req.getOrDefault("type", "");
            String status = (String) req.getOrDefault("status", "");
            String owner = (String) req.getOrDefault("owner", "");
            String target = (String) req.getOrDefault("target", "");
            String description = (String) req.getOrDefault("description", "");

            sb.append(String.format("%-12s %-10s %-8s %-12s %-10s %s\n",
                    number,
                    formatType(type),
                    formatStatus(status),
                    truncate(owner, 12),
                    truncate(target, 10),
                    truncate(description, 35)));
        }

        sb.append("\nLegend: Type (K=Workbench, W=Customizing), Status (D=Modifiable, R=Released, L=Protected)\n");

        return sb.toString();
    }

    private String formatType(String type) {
        if (type == null) return "";
        switch (type) {
            case "K": return "Workbench";
            case "W": return "Customiz";
            default: return type;
        }
    }

    private String formatStatus(String status) {
        if (status == null) return "";
        switch (status) {
            case "D": return "Modif";
            case "R": return "Released";
            case "L": return "Protect";
            default: return status;
        }
    }
}
