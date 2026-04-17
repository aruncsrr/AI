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
 * Formatter for GetDomain tool responses.
 * Converts raw domain XML into human-readable domain definition.
 */
@Slf4j
@Component
public class DomainFormatter extends AbstractADTFormatter {

    // FieldExtractors for domain root attributes (adtcore namespace)
    private static final List<FieldExtractor> DOMAIN_ROOT_FIELDS = Arrays.asList(
            FieldExtractor.builder("name").fromAttribute("name").asString().build(),
            FieldExtractor.builder("description").fromAttribute("description").asString().build()
    );

    // FieldExtractors for type information (nested elements under doma:content/doma:typeInformation)
    private static final List<FieldExtractor> TYPE_INFO_FIELDS = Arrays.asList(
            FieldExtractor.builder("datatype").fromChildText("datatype").asString().build(),
            FieldExtractor.builder("length").fromChildText("length").asString().build(),
            FieldExtractor.builder("decimals").fromChildText("decimals").asString().build()
    );

    // FieldExtractors for output information (nested elements under doma:content/doma:outputInformation)
    private static final List<FieldExtractor> OUTPUT_INFO_FIELDS = Arrays.asList(
            FieldExtractor.builder("outputLength").fromChildText("length").asString().build(),
            FieldExtractor.builder("lowercase").fromChildText("lowercase").asString().build(),
            FieldExtractor.builder("signExists").fromChildText("signExists").asString().build(),
            FieldExtractor.builder("conversionExit").fromChildText("conversionExit").asString().build()
    );

    // ElementExtractor for fixed values
    private static final ElementExtractor FIXED_VALUE_EXTRACTOR = ElementExtractor.builder("fixedValue")
            .field(FieldExtractor.builder("low").fromChildText("low").asString().build())
            .field(FieldExtractor.builder("high").fromChildText("high").asString().build())
            .field(FieldExtractor.builder("description").fromChildText("description").asString().build())
            .build();

    // ElementExtractor for package reference
    private static final ElementExtractor PACKAGE_REF_EXTRACTOR = ElementExtractor.builder("packageRef")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .field(FieldExtractor.builder("description").fromAttribute("description").asString().build())
            .build();

    // ElementExtractor for value table reference
    private static final ElementExtractor VALUE_TABLE_EXTRACTOR = ElementExtractor.builder("valueTableRef")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .build();

    @Override
    public String getToolName() {
        return "GetDomain";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatDomain(rawResponse, null, "active");
    }

    /**
     * Format domain response with context parameters.
     *
     * @param rawResponse The raw XML response
     * @param domainName The domain name (for header display)
     * @param version The version (active/inactive)
     * @return Formatted string
     * @throws FormattingException if formatting fails
     */
    public String formatWithContext(String rawResponse, String domainName, String version) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatDomain(rawResponse, domainName, version);
    }

    private String formatDomain(String xml, String domainName, String version) {
        // Extract root attributes
        Map<String, Object> rootData = XmlExtractor.extractSingle(xml, DOMAIN_ROOT_FIELDS);

        // Extract nested content sections
        Map<String, Object> typeInfo = XmlExtractor.extractFromElement(xml, "typeInformation", TYPE_INFO_FIELDS);
        Map<String, Object> outputInfo = XmlExtractor.extractFromElement(xml, "outputInformation", OUTPUT_INFO_FIELDS);

        // Extract package reference
        List<Map<String, Object>> packageRefs = XmlExtractor.extract(xml, PACKAGE_REF_EXTRACTOR);
        String packageName = "";
        if (!packageRefs.isEmpty()) {
            packageName = (String) packageRefs.get(0).getOrDefault("name", "");
        }

        // Extract value table reference
        List<Map<String, Object>> valueTableRefs = XmlExtractor.extract(xml, VALUE_TABLE_EXTRACTOR);
        String valueTable = "";
        if (!valueTableRefs.isEmpty()) {
            valueTable = (String) valueTableRefs.get(0).getOrDefault("name", "");
        }

        // Extract fixed values
        List<Map<String, Object>> fixedValues = XmlExtractor.extract(xml, FIXED_VALUE_EXTRACTOR);

        // Use name from XML if not provided
        String displayName = domainName != null ? domainName : (String) rootData.getOrDefault("name", "DOMAIN");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Domain: %s (version: %s)\n", displayName.toUpperCase(), version));
        sb.append(boxDivider(60)).append("\n\n");

        // Basic info from root
        String description = (String) rootData.getOrDefault("description", "");

        // Type information
        String dataType = (String) typeInfo.getOrDefault("datatype", "");
        String length = (String) typeInfo.getOrDefault("length", "");
        String decimals = (String) typeInfo.getOrDefault("decimals", "");

        // Output information
        String outputLength = (String) outputInfo.getOrDefault("outputLength", "");
        String lowercase = (String) outputInfo.getOrDefault("lowercase", "");
        String signExists = (String) outputInfo.getOrDefault("signExists", "");
        String conversionExit = (String) outputInfo.getOrDefault("conversionExit", "");

        sb.append("Properties:\n");
        if (!description.isEmpty()) {
            sb.append(String.format("  Description:     %s\n", description));
        }
        if (!packageName.isEmpty()) {
            sb.append(String.format("  Package:         %s\n", packageName));
        }

        sb.append("\nType Information:\n");
        if (!dataType.isEmpty()) {
            sb.append(String.format("  Data Type:       %s\n", dataType));
        }
        if (!length.isEmpty()) {
            sb.append(String.format("  Length:          %s\n", formatNumber(length)));
        }
        if (!decimals.isEmpty() && !"000000".equals(decimals) && !"0".equals(decimals)) {
            sb.append(String.format("  Decimals:        %s\n", formatNumber(decimals)));
        }

        sb.append("\nOutput Characteristics:\n");
        if (!outputLength.isEmpty()) {
            sb.append(String.format("  Output Length:   %s\n", formatNumber(outputLength)));
        }
        if ("true".equalsIgnoreCase(lowercase)) {
            sb.append("  Lowercase:       Yes\n");
        }
        if ("true".equalsIgnoreCase(signExists)) {
            sb.append("  Sign:            Yes\n");
        }
        if (!conversionExit.isEmpty()) {
            sb.append(String.format("  Conversion Exit: %s\n", conversionExit));
        }

        // Value table
        if (!valueTable.isEmpty()) {
            sb.append(String.format("\nValue Table:       %s\n", valueTable));
        }

        // Fixed values
        if (!fixedValues.isEmpty()) {
            sb.append(String.format("\nFixed Values (%d):\n", fixedValues.size()));
            sb.append(String.format("%-15s %-15s %s\n", "Low", "High", "Description"));
            sb.append(boxDivider(60)).append("\n");

            for (Map<String, Object> fv : fixedValues) {
                String low = (String) fv.getOrDefault("low", "");
                String high = (String) fv.getOrDefault("high", "");
                String desc = (String) fv.getOrDefault("description", "");

                sb.append(String.format("%-15s %-15s %s\n",
                        truncate(low, 15),
                        high.isEmpty() ? "" : truncate(high, 15),
                        truncate(desc, 30)));
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
