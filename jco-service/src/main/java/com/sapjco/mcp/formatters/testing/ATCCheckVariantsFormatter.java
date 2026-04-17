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
 * Formatter for ListATCCheckVariants tool responses.
 * Converts named item list XML into a human-readable table of check variants.
 */
@Slf4j
@Component
public class ATCCheckVariantsFormatter extends AbstractADTFormatter {

    private static final ElementExtractor NAMED_ITEM_EXTRACTOR = ElementExtractor.builder("namedItem")
            .field(FieldExtractor.builder("name")
                    .fromChildText("name")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("description")
                    .fromChildText("description")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "ListATCCheckVariants";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<Map<String, Object>> items = XmlExtractor.extract(rawResponse, NAMED_ITEM_EXTRACTOR);
        return formatVariants(items);
    }

    private String formatVariants(List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("ATC Check Variants\n");
        sb.append(boxDivider(80)).append("\n");

        if (items.isEmpty()) {
            sb.append("\nNo check variants found.\n");
            return sb.toString();
        }

        sb.append(String.format("\nFound %d variant%s\n\n", items.size(), items.size() == 1 ? "" : "s"));

        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            String name = (String) item.get("name");
            String description = (String) item.get("description");

            sb.append(String.format("[%d] %s", i + 1, name != null ? name : "(unnamed)"));
            if (description != null && !description.isEmpty()) {
                sb.append(String.format(" - %s", description));
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
