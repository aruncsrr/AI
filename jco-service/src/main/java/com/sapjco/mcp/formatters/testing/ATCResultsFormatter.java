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

/**
 * Formatter for ListATCResults tool responses.
 * Converts ATC result list XML into a human-readable table of historical results
 * with aggregated finding counts.
 */
@Slf4j
@Component
public class ATCResultsFormatter extends AbstractADTFormatter {

    private static final ElementExtractor RESULT_EXTRACTOR = ElementExtractor.builder("result")
            .field(FieldExtractor.builder("displayId")
                    .fromChildText("displayId")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("title")
                    .fromChildText("title")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("checkVariant")
                    .fromChildText("checkVariant")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("runSeries")
                    .fromChildText("runSeries")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("createdAt")
                    .fromChildText("createdAt")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("numPrio1")
                    .fromChildText("numPrio1")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .field(FieldExtractor.builder("numPrio2")
                    .fromChildText("numPrio2")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .field(FieldExtractor.builder("numPrio3")
                    .fromChildText("numPrio3")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .field(FieldExtractor.builder("numPrio4")
                    .fromChildText("numPrio4")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .field(FieldExtractor.builder("numFailure")
                    .fromChildText("numFailure")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "ListATCResults";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<Map<String, Object>> results = XmlExtractor.extract(rawResponse, RESULT_EXTRACTOR);
        return formatResults(results);
    }

    private String formatResults(List<Map<String, Object>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("ATC Results Browser\n");
        sb.append(boxDivider(80)).append("\n");

        if (results.isEmpty()) {
            sb.append("\nNo ATC results found for the given criteria.\n");
            return sb.toString();
        }

        sb.append(String.format("\nFound %d result%s\n\n", results.size(), results.size() == 1 ? "" : "s"));

        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> r = results.get(i);
            String displayId = (String) r.get("displayId");
            String title = (String) r.get("title");
            String variant = (String) r.get("checkVariant");
            String runSeries = (String) r.get("runSeries");
            String createdAt = (String) r.get("createdAt");
            int prio1 = (Integer) r.get("numPrio1");
            int prio2 = (Integer) r.get("numPrio2");
            int prio3 = (Integer) r.get("numPrio3");
            int prio4 = (Integer) r.get("numPrio4");
            int failures = (Integer) r.get("numFailure");

            // Line 1: Number and title
            sb.append(String.format("[%d] %s\n", i + 1, title != null ? title : "(untitled)"));

            // Line 2: Display ID
            sb.append(String.format("   ID: %s\n", displayId != null ? displayId : "-"));

            // Line 3: Variant and series
            sb.append(String.format("   Variant: %s", variant != null ? variant : "-"));
            if (runSeries != null && !runSeries.isEmpty()) {
                sb.append(String.format(" | Series: %s", runSeries));
            }
            sb.append("\n");

            // Line 4: Date
            if (createdAt != null && !createdAt.isEmpty()) {
                sb.append(String.format("   Created: %s\n", createdAt));
            }

            // Line 5: Findings summary
            int total = prio1 + prio2 + prio3 + prio4;
            sb.append(String.format("   Findings: [E] %d, [W] %d, [I] %d, [P4] %d (%d total)",
                    prio1, prio2, prio3, prio4, total));
            if (failures > 0) {
                sb.append(String.format(" | Failures: %d", failures));
            }
            sb.append("\n\n");
        }

        return sb.toString();
    }
}
