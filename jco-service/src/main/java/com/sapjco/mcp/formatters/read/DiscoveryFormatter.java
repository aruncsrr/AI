package com.sapjco.mcp.formatters.read;

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
 * Formatter for GetDiscovery tool responses.
 * Converts raw ADT discovery XML into human-readable endpoint listing.
 */
@Slf4j
@Component
public class DiscoveryFormatter extends AbstractADTFormatter {

    // ElementExtractor for collection elements in ATOM service document
    private static final ElementExtractor COLLECTION_EXTRACTOR = ElementExtractor.builder("collection")
            .field(FieldExtractor.builder("href")
                    .fromAttribute("href")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("title")
                    .fromChildText("title")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "GetDiscovery";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatDiscovery(rawResponse);
    }

    /**
     * Format discovery response with optional context parameters.
     *
     * @param rawResponse The raw XML response
     * @param discoveryUri The discovery URI used (for header display)
     * @param categoryScheme Optional category scheme filter
     * @param categoryTerm Optional category term filter
     * @return Formatted string
     * @throws FormattingException if formatting fails
     */
    public String formatWithContext(String rawResponse, String discoveryUri,
                                    String categoryScheme, String categoryTerm) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<Map<String, Object>> collections = XmlExtractor.extract(rawResponse, COLLECTION_EXTRACTOR);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("ADT Discovery: %s\n", discoveryUri != null ? discoveryUri : "/sap/bc/adt/discovery"));
        if (categoryScheme != null) {
            sb.append(String.format("Category Scheme: %s\n", categoryScheme));
        }
        if (categoryTerm != null) {
            sb.append(String.format("Category Term: %s\n", categoryTerm));
        }
        sb.append(boxDivider(80)).append("\n");

        appendCollections(sb, collections);

        return sb.toString();
    }

    private String formatDiscovery(String xml) {
        List<Map<String, Object>> collections = XmlExtractor.extract(xml, COLLECTION_EXTRACTOR);

        StringBuilder sb = new StringBuilder();
        sb.append("ADT Discovery\n");
        sb.append(boxDivider(80)).append("\n");

        appendCollections(sb, collections);

        return sb.toString();
    }

    private void appendCollections(StringBuilder sb, List<Map<String, Object>> collections) {
        if (collections.isEmpty()) {
            sb.append("\nNo services found in discovery document.\n");
            return;
        }

        sb.append(String.format("\nEndpoints: %d\n\n", collections.size()));

        // Group by URI prefix (for better organization)
        Map<String, List<Map<String, Object>>> grouped = collections.stream()
                .collect(Collectors.groupingBy(c -> {
                    String href = (String) c.getOrDefault("href", "");
                    // Extract first path segment as category
                    if (href.startsWith("/sap/bc/adt/")) {
                        String rest = href.substring("/sap/bc/adt/".length());
                        int idx = rest.indexOf('/');
                        return idx > 0 ? rest.substring(0, idx) : rest;
                    }
                    return "other";
                }));

        // Endpoints table
        sb.append(String.format("%-50s %s\n", "Endpoint URI", "Title"));
        sb.append(boxDivider(80)).append("\n");

        for (Map.Entry<String, List<Map<String, Object>>> group : grouped.entrySet()) {
            for (Map<String, Object> coll : group.getValue()) {
                String href = (String) coll.getOrDefault("href", "");
                String title = (String) coll.getOrDefault("title", "");

                sb.append(String.format("%-50s %s\n",
                        truncate(href, 50),
                        truncate(title, 30)));
            }
        }
    }
}
