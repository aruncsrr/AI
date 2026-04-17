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
 * Formatter for GetStructureFields tool responses.
 * Converts raw structure fields XML into human-readable component listing.
 */
@Slf4j
@Component
public class StructureFieldsFormatter extends AbstractADTFormatter {

    // Nested field elementInfo elements (component metadata)
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
        return "GetStructureFields";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<StructureComponent> components = parseStructureComponents(rawResponse);
        return formatStructureComponents(components);
    }

    /**
     * Parsed structure component information.
     */
    private static class StructureComponent {
        final String name;
        final String type;
        final String description;
        final String ddicDataElement;
        final String ddicDataType;
        final String ddicLength;
        final String ddicDecimals;

        StructureComponent(String name, String type, String description, Map<String, String> properties) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.ddicDataElement = properties.getOrDefault("ddicDataElement", "");
            this.ddicDataType = properties.getOrDefault("ddicDataType", "");
            this.ddicLength = properties.getOrDefault("ddicLength", "");
            this.ddicDecimals = properties.getOrDefault("ddicDecimals", "");
        }
    }

    private List<StructureComponent> parseStructureComponents(String xml) {
        List<StructureComponent> components = new ArrayList<>();

        try {
            Document doc = XmlExtractor.parseXml(xml);
            Element root = doc.getDocumentElement();

            // Get nested elementInfo elements (components)
            NodeList elementInfoNodes = root.getElementsByTagNameNS("*", "elementInfo");

            for (int i = 0; i < elementInfoNodes.getLength(); i++) {
                Element componentElem = (Element) elementInfoNodes.item(i);

                // Extract basic component metadata
                Map<String, Object> componentData = XmlExtractor.extractFields(componentElem, FIELD_ELEMENT_EXTRACTOR.getFieldExtractors());

                // Extract properties using helper method
                Map<String, String> properties = new HashMap<>();
                properties.put("ddicDataElement", XmlExtractor.extractPropertyValue(componentElem, "properties", "entry", "key", "ddicDataElement"));
                properties.put("ddicDataType", XmlExtractor.extractPropertyValue(componentElem, "properties", "entry", "key", "ddicDataType"));
                properties.put("ddicLength", XmlExtractor.extractPropertyValue(componentElem, "properties", "entry", "key", "ddicLength"));
                properties.put("ddicDecimals", XmlExtractor.extractPropertyValue(componentElem, "properties", "entry", "key", "ddicDecimals"));

                StructureComponent component = new StructureComponent(
                        (String) componentData.get("name"),
                        (String) componentData.get("type"),
                        (String) componentData.get("description"),
                        properties
                );

                components.add(component);
            }
        } catch (Exception e) {
            log.error("Failed to parse structure components XML", e);
        }

        return components;
    }

    private String formatStructureComponents(List<StructureComponent> components) {
        StringBuilder sb = new StringBuilder();
        sb.append("Structure Components\n");
        sb.append(boxDivider(80)).append("\n\n");

        if (components.isEmpty()) {
            sb.append("No components found.\n");
            return sb.toString();
        }

        sb.append(String.format("Found %d component%s\n\n",
                components.size(), components.size() == 1 ? "" : "s"));

        // Format as table
        sb.append(String.format("%-20s %-10s %-7s %-4s %-15s %s\n",
                "Component Name", "Type", "Length", "Dec", "Data Element", "Description"));
        sb.append(boxDivider(110)).append("\n");

        for (StructureComponent component : components) {
            String decimals = component.ddicDecimals.isEmpty() ? "" : component.ddicDecimals;

            sb.append(String.format("%-20s %-10s %-7s %-4s %-15s %s\n",
                    truncate(component.name, 20),
                    truncate(component.ddicDataType, 10),
                    truncate(component.ddicLength, 7),
                    truncate(decimals, 4),
                    truncate(component.ddicDataElement, 15),
                    truncate(component.description, 40)));
        }

        return sb.toString();
    }
}
