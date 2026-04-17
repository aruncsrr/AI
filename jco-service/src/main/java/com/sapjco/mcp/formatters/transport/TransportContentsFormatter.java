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
 * Formatter for GetTransportContents tool responses.
 * Converts raw transport contents XML into human-readable object list.
 */
@Slf4j
@Component
public class TransportContentsFormatter extends AbstractADTFormatter {

    // ElementExtractor for transport object elements (XML uses tm:abap_object)
    private static final ElementExtractor OBJECT_EXTRACTOR = ElementExtractor.builder("abap_object")
            .field(FieldExtractor.builder("pgmid")
                    .fromAttribute("pgmid")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("type")
                    .fromAttribute("type")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("name")
                    .fromAttribute("name")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("description")
                    .fromAttribute("obj_desc")
                    .asString()
                    .build())
            .build();

    // ElementExtractor for task elements (XML uses tm:task with tm:desc attribute)
    private static final ElementExtractor TASK_EXTRACTOR = ElementExtractor.builder("task")
            .field(FieldExtractor.builder("id")
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
            .build();

    @Override
    public String getToolName() {
        return "GetTransportContents";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatTransportContents(rawResponse, "Unknown");
    }

    /**
     * Format with additional context about the transport ID.
     */
    public String format(String rawResponse, String transportId) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatTransportContents(rawResponse, transportId);
    }

    // ========================================================================
    // Transport Contents Formatting
    // ========================================================================

    private String formatTransportContents(String xml, String transportId) {
        List<Map<String, Object>> objects = XmlExtractor.extract(xml, OBJECT_EXTRACTOR);
        List<Map<String, Object>> tasks = XmlExtractor.extract(xml, TASK_EXTRACTOR);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Transport Contents: %s\n", transportId.toUpperCase()));
        sb.append(boxDivider(80)).append("\n");

        // Show tasks if this is a request (contains tasks)
        if (!tasks.isEmpty()) {
            sb.append(String.format("\nTasks: %d\n", tasks.size()));
            for (Map<String, Object> task : tasks) {
                String id = (String) task.getOrDefault("id", "");
                String owner = (String) task.getOrDefault("owner", "");
                String desc = (String) task.getOrDefault("description", "");
                sb.append(String.format("  - %s (%s) - %s\n", id, owner, truncate(desc, 50)));
            }
            sb.append("\n");
        }

        if (objects.isEmpty()) {
            sb.append("\nNo objects found in transport.\n");
            return sb.toString();
        }

        // Group by type
        Map<String, Long> typeCounts = objects.stream()
                .collect(Collectors.groupingBy(
                        r -> (String) r.getOrDefault("type", "unknown"),
                        Collectors.counting()));

        // Summary
        sb.append(String.format("Objects: %d in %d type%s\n",
                objects.size(), typeCounts.size(), typeCounts.size() == 1 ? "" : "s"));

        // Type breakdown
        sb.append("By Type: ");
        sb.append(typeCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> String.format("%d %s", e.getValue(), e.getKey()))
                .collect(Collectors.joining(", ")));
        sb.append("\n\n");

        // Objects table
        sb.append(String.format("%-6s %-8s %-30s %s\n", "PGMID", "Type", "Name", "Description"));
        sb.append(boxDivider(80)).append("\n");

        for (Map<String, Object> obj : objects) {
            String pgmid = (String) obj.getOrDefault("pgmid", "");
            String type = (String) obj.getOrDefault("type", "");
            String name = (String) obj.getOrDefault("name", "");
            String description = (String) obj.getOrDefault("description", "");

            sb.append(String.format("%-6s %-8s %-30s %s\n",
                    pgmid,
                    type,
                    truncate(name, 30),
                    truncate(description, 35)));
        }

        return sb.toString();
    }
}
