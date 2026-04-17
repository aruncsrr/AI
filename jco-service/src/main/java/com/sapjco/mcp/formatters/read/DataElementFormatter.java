package com.sapjco.mcp.formatters.read;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.ElementExtractor;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Formatter for GetDataElement tool responses.
 * Converts raw data element XML into human-readable definition.
 */
@Slf4j
@Component
public class DataElementFormatter extends AbstractADTFormatter {

    // FieldExtractors for root attributes (adtcore namespace)
    private static final List<FieldExtractor> ROOT_FIELDS = Arrays.asList(
            FieldExtractor.builder("name").fromAttribute("name").asString().build(),
            FieldExtractor.builder("description").fromAttribute("description").asString().build()
    );

    // FieldExtractors for dataElement nested element
    private static final List<FieldExtractor> DATA_ELEMENT_FIELDS = Arrays.asList(
            FieldExtractor.builder("typeKind").fromChildText("typeKind").asString().build(),
            FieldExtractor.builder("typeName").fromChildText("typeName").asString().build(),
            FieldExtractor.builder("dataType").fromChildText("dataType").asString().build(),
            FieldExtractor.builder("length").fromChildText("dataTypeLength").asString().build(),
            FieldExtractor.builder("decimals").fromChildText("dataTypeDecimals").asString().build(),
            FieldExtractor.builder("shortLabel").fromChildText("shortFieldLabel").asString().build(),
            FieldExtractor.builder("mediumLabel").fromChildText("mediumFieldLabel").asString().build(),
            FieldExtractor.builder("longLabel").fromChildText("longFieldLabel").asString().build(),
            FieldExtractor.builder("headingLabel").fromChildText("headingFieldLabel").asString().build()
    );

    // ElementExtractor for package reference
    private static final ElementExtractor PACKAGE_REF_EXTRACTOR = ElementExtractor.builder("packageRef")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .field(FieldExtractor.builder("description").fromAttribute("description").asString().build())
            .build();

    @Override
    public String getToolName() {
        return "GetDataElement";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatDataElement(rawResponse, null, "active");
    }

    /**
     * Format data element response with context parameters.
     *
     * @param rawResponse The raw XML response
     * @param dataElementName The data element name (for header display)
     * @param version The version (active/inactive)
     * @return Formatted string
     * @throws FormattingException if formatting fails
     */
    public String formatWithContext(String rawResponse, String dataElementName, String version) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatDataElement(rawResponse, dataElementName, version);
    }

    private String formatDataElement(String xml, String dataElementName, String version) {
        // Extract root attributes
        Map<String, Object> rootData = XmlExtractor.extractSingle(xml, ROOT_FIELDS);

        // Extract nested dataElement content
        Map<String, Object> dtelData = XmlExtractor.extractFromElement(xml, "dataElement", DATA_ELEMENT_FIELDS);

        // Extract package reference
        List<Map<String, Object>> packageRefs = XmlExtractor.extract(xml, PACKAGE_REF_EXTRACTOR);
        String packageName = "";
        if (!packageRefs.isEmpty()) {
            packageName = (String) packageRefs.get(0).getOrDefault("name", "");
        }

        // Use name from XML if not provided
        String displayName = dataElementName != null ? dataElementName : (String) rootData.getOrDefault("name", "DATA_ELEMENT");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Data Element: %s (version: %s)\n", displayName.toUpperCase(), version));
        sb.append(boxDivider(60)).append("\n\n");

        // Basic info from root
        String description = (String) rootData.getOrDefault("description", "");

        // Type info from nested element
        String typeKind = (String) dtelData.getOrDefault("typeKind", "");
        String typeName = (String) dtelData.getOrDefault("typeName", "");
        String dataType = (String) dtelData.getOrDefault("dataType", "");
        String length = (String) dtelData.getOrDefault("length", "");
        String decimals = (String) dtelData.getOrDefault("decimals", "");

        sb.append("Properties:\n");
        if (!description.isEmpty()) {
            sb.append(String.format("  Description:  %s\n", description));
        }
        if (!packageName.isEmpty()) {
            sb.append(String.format("  Package:      %s\n", packageName));
        }

        sb.append("\nType Information:\n");
        if (!typeKind.isEmpty()) {
            String typeLabel = "domain".equals(typeKind) ? "Domain" :
                              "predefinedAbapType".equals(typeKind) ? "Built-in Type" : typeKind;
            sb.append(String.format("  Type Kind:    %s\n", typeLabel));
        }
        if (!typeName.isEmpty()) {
            sb.append(String.format("  Type Name:    %s\n", typeName));
        }
        if (!dataType.isEmpty()) {
            sb.append(String.format("  Data Type:    %s\n", dataType));
        }
        if (!length.isEmpty()) {
            sb.append(String.format("  Length:       %s\n", formatNumber(length)));
        }
        if (!decimals.isEmpty() && !"000000".equals(decimals) && !"0".equals(decimals)) {
            sb.append(String.format("  Decimals:     %s\n", formatNumber(decimals)));
        }

        // Labels from nested element
        String shortLabel = (String) dtelData.getOrDefault("shortLabel", "");
        String mediumLabel = (String) dtelData.getOrDefault("mediumLabel", "");
        String longLabel = (String) dtelData.getOrDefault("longLabel", "");
        String headingLabel = (String) dtelData.getOrDefault("headingLabel", "");

        if (!shortLabel.isEmpty() || !mediumLabel.isEmpty() || !longLabel.isEmpty() || !headingLabel.isEmpty()) {
            sb.append("\nField Labels:\n");
            if (!shortLabel.isEmpty()) {
                sb.append(String.format("  Short (10):   %s\n", shortLabel));
            }
            if (!mediumLabel.isEmpty()) {
                sb.append(String.format("  Medium (20):  %s\n", mediumLabel));
            }
            if (!longLabel.isEmpty()) {
                sb.append(String.format("  Long (40):    %s\n", longLabel));
            }
            if (!headingLabel.isEmpty()) {
                sb.append(String.format("  Heading (55): %s\n", headingLabel));
            }
        }

        return sb.toString();
    }

    private String formatNumber(String num) {
        if (num == null || num.isEmpty()) {
            return "";
        }
        // Remove leading zeros but keep at least one digit
        return num.replaceFirst("^0+(?!$)", "");
    }
}
