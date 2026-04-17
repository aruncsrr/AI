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

/**
 * Formatter for GetVersionHistory tool responses.
 * Converts raw ATOM feed XML into human-readable version history.
 */
@Slf4j
@Component
public class VersionHistoryFormatter extends AbstractADTFormatter {

    // ElementExtractor for ATOM entry elements (version history uses ATOM feed format)
    private static final ElementExtractor ENTRY_EXTRACTOR = ElementExtractor.builder("entry")
            .field(FieldExtractor.builder("id")
                    .fromChildText("id")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("title")
                    .fromChildText("title")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("updated")
                    .fromChildText("updated")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("author")
                    .fromNestedChildText("author", "name")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("versionNumber")
                    .fromChildText("versionNumber")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "GetVersionHistory";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<Map<String, Object>> entries = XmlExtractor.extract(rawResponse, ENTRY_EXTRACTOR);
        return formatVersionHistory(entries);
    }

    private String formatVersionHistory(List<Map<String, Object>> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("Version History\n");
        sb.append(boxDivider(90)).append("\n");

        if (entries.isEmpty()) {
            sb.append("\nNo versions found.\n");
            return sb.toString();
        }

        sb.append(String.format("\nVersions: %d\n\n", entries.size()));

        // Versions table
        sb.append(String.format("%-8s %-20s %-12s %s\n", "Version", "Date/Time", "Author", "Description"));
        sb.append(boxDivider(90)).append("\n");

        for (Map<String, Object> entry : entries) {
            String id = (String) entry.getOrDefault("id", "");
            String versionNumber = (String) entry.getOrDefault("versionNumber", "");
            String updated = (String) entry.getOrDefault("updated", "");
            String author = (String) entry.getOrDefault("author", "");
            String title = (String) entry.getOrDefault("title", "");

            // Use id as version number if versionNumber is not present
            // The id field contains the version ID directly (e.g., "00000")
            String displayVersion = !versionNumber.isEmpty() ? versionNumber : id;

            // Format date (ISO format to shorter)
            String dateStr = formatDate(updated);

            sb.append(String.format("%-8s %-20s %-12s %s\n",
                    displayVersion,
                    dateStr,
                    truncate(author, 12),
                    truncate(title, 50)));
        }

        sb.append("\nUse GetVersionContent with version ID to retrieve specific version source.\n");

        return sb.toString();
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return "";
        }
        // Convert ISO date (2024-01-15T10:30:45Z) to shorter format
        try {
            return isoDate.replace("T", " ").replace("Z", "").substring(0, 19);
        } catch (Exception e) {
            return isoDate;
        }
    }
}
