package com.sapjco.mcp.formatters.read;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.ElementExtractor;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Formatter for GetTableFields tool responses.
 * Converts raw table fields XML into human-readable field listing.
 */
@Slf4j
@Component
public class TableFieldsFormatter extends AbstractADTFormatter {

    // Nested field elementInfo elements (field metadata)
    private static final ElementExtractor FIELD_ELEMENT_EXTRACTOR = ElementExtractor.builder("elementInfo")
            .field(FieldExtractor.builder("name")
                    .fromAttribute("name")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("type")
                    .fromAttribute("type")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("description")
                    .fromChildText("documentation")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "GetTableFields";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<TableField> fields = parseTableFields(rawResponse);
        return formatTableFields(fields);
    }

    /**
     * Parsed table field information.
     */
    private static class TableField {
        final String name;
        final String type;
        final String description;
        final String ddicIsKey;
        final String ddicDataElement;
        final String ddicDataType;
        final String ddicLength;
        final String ddicDecimals;

        TableField(String name, String type, String description, Map<String, String> properties) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.ddicIsKey = properties.getOrDefault("ddicIsKey", "");
            this.ddicDataElement = properties.getOrDefault("ddicDataElement", "");
            this.ddicDataType = properties.getOrDefault("ddicDataType", "");
            this.ddicLength = properties.getOrDefault("ddicLength", "");
            this.ddicDecimals = properties.getOrDefault("ddicDecimals", "");
        }

        boolean isKey() {
            return "true".equalsIgnoreCase(ddicIsKey);
        }
    }

    private List<TableField> parseTableFields(String xml) {
        List<TableField> fields = new ArrayList<>();

        try {
            Document doc = XmlExtractor.parseXml(xml);
            Element root = doc.getDocumentElement();

            // Get nested elementInfo elements (fields)
            NodeList elementInfoNodes = root.getElementsByTagNameNS("*", "elementInfo");

            for (int i = 0; i < elementInfoNodes.getLength(); i++) {
                Element fieldElem = (Element) elementInfoNodes.item(i);

                // Extract basic field metadata
                Map<String, Object> fieldData = XmlExtractor.extractFields(fieldElem, FIELD_ELEMENT_EXTRACTOR.getFieldExtractors());

                // Extract properties using helper method
                Map<String, String> properties = new HashMap<>();
                properties.put("ddicIsKey", XmlExtractor.extractPropertyValue(fieldElem, "properties", "entry", "key", "ddicIsKey"));
                properties.put("ddicDataElement", XmlExtractor.extractPropertyValue(fieldElem, "properties", "entry", "key", "ddicDataElement"));
                properties.put("ddicDataType", XmlExtractor.extractPropertyValue(fieldElem, "properties", "entry", "key", "ddicDataType"));
                properties.put("ddicLength", XmlExtractor.extractPropertyValue(fieldElem, "properties", "entry", "key", "ddicLength"));
                properties.put("ddicDecimals", XmlExtractor.extractPropertyValue(fieldElem, "properties", "entry", "key", "ddicDecimals"));

                TableField field = new TableField(
                        (String) fieldData.get("name"),
                        (String) fieldData.get("type"),
                        (String) fieldData.get("description"),
                        properties
                );

                fields.add(field);
            }
        } catch (Exception e) {
            log.error("Failed to parse table fields XML", e);
        }

        return fields;
    }

    private String formatTableFields(List<TableField> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("Table Fields\n");
        sb.append(boxDivider(80)).append("\n\n");

        if (fields.isEmpty()) {
            sb.append("No fields found.\n");
            return sb.toString();
        }

        // Count key fields
        long keyCount = fields.stream().filter(TableField::isKey).count();

        sb.append(String.format("Found %d field%s (%d key field%s)\n\n",
                fields.size(), fields.size() == 1 ? "" : "s",
                keyCount, keyCount == 1 ? "" : "s"));

        // Format as table
        sb.append(String.format("%-4s %-20s %-10s %-7s %-4s %-15s %s\n",
                "Key", "Field Name", "Type", "Length", "Dec", "Data Element", "Description"));
        sb.append(boxDivider(110)).append("\n");

        for (TableField field : fields) {
            String keyIcon = field.isKey() ? "*" : " ";
            String decimals = field.ddicDecimals.isEmpty() ? "" : field.ddicDecimals;

            sb.append(String.format("%-4s %-20s %-10s %-7s %-4s %-15s %s\n",
                    keyIcon,
                    truncate(field.name, 20),
                    truncate(field.ddicDataType, 10),
                    truncate(field.ddicLength, 7),
                    truncate(decimals, 4),
                    truncate(field.ddicDataElement, 15),
                    truncate(field.description, 40)));
        }

        return sb.toString();
    }
}
