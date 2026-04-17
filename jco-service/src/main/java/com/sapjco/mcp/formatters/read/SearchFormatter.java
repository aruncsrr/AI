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
 * Formatter for Search tool responses.
 * Converts raw search XML into human-readable search results.
 */
@Slf4j
@Component
public class SearchFormatter extends AbstractADTFormatter {

    // ElementExtractor for objectReference elements in search results
    private static final ElementExtractor OBJECT_REF_EXTRACTOR = ElementExtractor.builder("objectReference")
            .field(FieldExtractor.builder("name")
                    .fromAttribute("name")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("type")
                    .fromAttribute("type")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("uri")
                    .fromAttribute("uri")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("packageName")
                    .fromAttribute("packageName")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("description")
                    .fromAttribute("description")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "Search";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<Map<String, Object>> results = XmlExtractor.extract(rawResponse, OBJECT_REF_EXTRACTOR);
        return formatSearchResults(results);
    }

    private String formatSearchResults(List<Map<String, Object>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Search Results\n");
        sb.append(boxDivider(80)).append("\n");

        if (results.isEmpty()) {
            sb.append("\nNo objects found matching the search criteria.\n");
            return sb.toString();
        }

        // Group by type
        Map<String, Long> typeCounts = results.stream()
                .collect(Collectors.groupingBy(
                        r -> (String) r.getOrDefault("type", "unknown"),
                        Collectors.counting()));

        // Summary
        sb.append(String.format("\nFound %d object%s", results.size(), results.size() == 1 ? "" : "s"));
        if (typeCounts.size() > 1) {
            sb.append(String.format(" in %d types", typeCounts.size()));
        }
        sb.append("\n");

        // Type breakdown
        sb.append("By Type: ");
        sb.append(typeCounts.entrySet().stream()
                .map(e -> String.format("%d %s", e.getValue(), formatTypeName(e.getKey())))
                .collect(Collectors.joining(", ")));
        sb.append("\n\n");

        // Results table
        sb.append(String.format("%-30s %-12s %-20s %s\n", "Name", "Type", "Package", "Description"));
        sb.append(boxDivider(80)).append("\n");

        for (Map<String, Object> result : results) {
            String name = (String) result.getOrDefault("name", "");
            String type = (String) result.getOrDefault("type", "");
            String packageName = (String) result.getOrDefault("packageName", "");
            String description = (String) result.getOrDefault("description", "");

            sb.append(String.format("%-30s %-12s %-20s %s\n",
                    truncate(name, 30),
                    formatTypeName(type),
                    truncate(packageName, 20),
                    truncate(description, 40)));
        }

        return sb.toString();
    }

    private String formatTypeName(String type) {
        if (type == null || type.isEmpty()) {
            return "unknown";
        }
        // Map common type codes to readable names
        switch (type.toUpperCase()) {
            case "CLAS": return "Class";
            case "INTF": return "Interface";
            case "PROG": return "Program";
            case "FUGR": return "FuncGroup";
            case "FUNC": return "FuncModule";
            case "DTEL": return "DataElem";
            case "TABL": return "Table";
            case "DOMA": return "Domain";
            case "DDLS": return "CDSView";
            case "DEVC": return "Package";
            case "MSAG": return "MsgClass";
            case "TRAN": return "TCode";
            case "VIEW": return "View";
            case "SHLP": return "SearchHelp";
            default: return type;
        }
    }
}
