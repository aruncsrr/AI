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

/**
 * Formatter for GetTransportRequests tool responses.
 * Converts raw transport check XML into human-readable transport request list.
 */
@Slf4j
@Component
public class TransportRequestsFormatter extends AbstractADTFormatter {

    // ElementExtractor for transport request elements in transport check results
    // XML structure: REQUESTS > CTS_REQUEST > REQ_HEADER (contains transport data)
    private static final ElementExtractor TRANSPORT_EXTRACTOR = ElementExtractor.builder("REQ_HEADER")
            .field(FieldExtractor.builder("trkorr")
                    .fromChildText("TRKORR")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("trfunction")
                    .fromChildText("TRFUNCTION")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("trstatus")
                    .fromChildText("TRSTATUS")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("as4user")
                    .fromChildText("AS4USER")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("as4text")
                    .fromChildText("AS4TEXT")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "GetTransportRequests";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatTransportRequests(rawResponse, "Unknown", "unknown");
    }

    /**
     * Format with additional context about the object.
     */
    public String format(String rawResponse, String objectName, String objectType) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatTransportRequests(rawResponse, objectName, objectType);
    }

    // ========================================================================
    // Transport Requests Formatting
    // ========================================================================

    private String formatTransportRequests(String xml, String objectName, String objectType) {
        List<Map<String, Object>> transports = XmlExtractor.extract(xml, TRANSPORT_EXTRACTOR);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Transport Requests for: %s (%s)\n", objectName.toUpperCase(), objectType));
        sb.append(boxDivider(80)).append("\n");

        if (transports.isEmpty()) {
            sb.append("\nNo transport requests found for this object.\n");
            sb.append("The object may be in a local package ($TMP) or no modifiable transports exist.\n");
            return sb.toString();
        }

        sb.append(String.format("\nFound %d transport request%s\n\n",
                transports.size(), transports.size() == 1 ? "" : "s"));

        // Transport table
        sb.append(String.format("%-12s %-8s %-8s %-12s %s\n", "Transport", "Function", "Status", "Owner", "Description"));
        sb.append(boxDivider(80)).append("\n");

        for (Map<String, Object> tr : transports) {
            String trkorr = (String) tr.getOrDefault("trkorr", "");
            String trfunction = (String) tr.getOrDefault("trfunction", "");
            String trstatus = (String) tr.getOrDefault("trstatus", "");
            String as4user = (String) tr.getOrDefault("as4user", "");
            String as4text = (String) tr.getOrDefault("as4text", "");

            sb.append(String.format("%-12s %-8s %-8s %-12s %s\n",
                    trkorr,
                    formatFunction(trfunction),
                    formatStatus(trstatus),
                    truncate(as4user, 12),
                    truncate(as4text, 40)));
        }

        sb.append("\nLegend: Function (K=Workbench, W=Customizing), Status (D=Modifiable, R=Released, L=Protected)\n");

        return sb.toString();
    }

    private String formatFunction(String trfunction) {
        if (trfunction == null) return "";
        switch (trfunction) {
            case "K": return "Workbench";
            case "W": return "Custom";
            default: return trfunction;
        }
    }

    private String formatStatus(String trstatus) {
        if (trstatus == null) return "";
        switch (trstatus) {
            case "D": return "Modif";
            case "R": return "Released";
            case "L": return "Protect";
            default: return trstatus;
        }
    }
}
