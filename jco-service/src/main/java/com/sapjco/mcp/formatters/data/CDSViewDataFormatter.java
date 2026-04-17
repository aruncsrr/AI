package com.sapjco.mcp.formatters.data;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Formatter for PreviewCDSView tool responses.
 * Converts raw SAP data preview XML (columnar format) into human-readable table format.
 */
@Slf4j
@Component
public class CDSViewDataFormatter extends AbstractADTFormatter {

    @Override
    public String getToolName() {
        return "PreviewCDSView";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatDataPreview(rawResponse, "CDS View", 100, null);
    }

    /**
     * Format with additional context about the view name, max rows, and parameters.
     */
    public String format(String rawResponse, String viewName, int maxRows, Map<String, Object> parameters) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatDataPreview(rawResponse, viewName, maxRows, parameters);
    }

    // ========================================================================
    // Data Preview Formatting - Columnar XML Parsing
    // ========================================================================

    /**
     * Record to hold column data extracted from columnar XML format.
     * SAP returns data column-by-column, not row-by-row.
     */
    private record ColumnData(String name, String type, String description, String keyAttribute, List<String> values) {}

    /**
     * Extract columnar data from SAP's data preview XML format.
     */
    private List<ColumnData> extractColumnarData(String xml) {
        List<ColumnData> columns = new ArrayList<>();
        try {
            List<Element> columnElements = XmlExtractor.getElements(xml, "columns");
            for (Element colElement : columnElements) {
                // Extract metadata
                List<Element> metadataElements = getLocalChildElements(colElement, "metadata");
                if (metadataElements.isEmpty()) continue;
                Element metadata = metadataElements.get(0);

                String name = getAttributeValue(metadata, "name");
                String type = getAttributeValue(metadata, "type");
                String description = getAttributeValue(metadata, "description");
                String keyAttribute = getAttributeValue(metadata, "keyAttribute");

                // Extract data values from dataSet/data elements
                List<String> values = new ArrayList<>();
                List<Element> dataSetElements = getLocalChildElements(colElement, "dataSet");
                if (!dataSetElements.isEmpty()) {
                    List<Element> dataElements = getLocalChildElements(dataSetElements.get(0), "data");
                    for (Element dataEl : dataElements) {
                        values.add(dataEl.getTextContent().trim());
                    }
                }

                columns.add(new ColumnData(name, type, description, keyAttribute, values));
            }
        } catch (Exception e) {
            log.warn("Failed to extract columnar data: {}", e.getMessage());
        }
        return columns;
    }

    /**
     * Get child elements with a specific local name (ignoring namespace prefix).
     */
    private List<Element> getLocalChildElements(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el) {
                if (localName.equals(el.getLocalName())) {
                    children.add(el);
                }
            }
        }
        return children;
    }

    /**
     * Get attribute value, handling namespace prefixes.
     */
    private String getAttributeValue(Element el, String localName) {
        String value = el.getAttribute("dataPreview:" + localName);
        if (value == null || value.isEmpty()) {
            value = el.getAttribute(localName);
        }
        return value != null ? value : "";
    }

    private String formatDataPreview(String xml, String viewName, int maxRows, Map<String, Object> parameters) {
        List<ColumnData> columns = extractColumnarData(xml);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CDS View Data: %s\n", viewName));
        if (parameters != null && !parameters.isEmpty()) {
            sb.append("Parameters: ").append(parameters).append("\n");
        }
        sb.append(boxDivider(80)).append("\n\n");

        if (columns.isEmpty()) {
            sb.append("No data found.\n");
            return sb.toString();
        }

        // Determine row count from first column
        int rowCount = columns.isEmpty() ? 0 : columns.get(0).values().size();
        sb.append(String.format("Columns: %d | Rows: %d (max requested: %d)\n\n",
                columns.size(), rowCount, maxRows));

        // Calculate column widths
        List<Integer> colWidths = new ArrayList<>();
        for (ColumnData col : columns) {
            int width = Math.max(col.name().length(), 8);
            for (String val : col.values()) {
                width = Math.max(width, Math.min(val.length(), 30));
            }
            colWidths.add(Math.min(width, 30));
        }

        // Header row with key markers
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            ColumnData col = columns.get(i);
            String keyMarker = "true".equalsIgnoreCase(col.keyAttribute()) ? "*" : "";
            header.append(String.format("%-" + colWidths.get(i) + "s%s ",
                    truncate(col.name(), colWidths.get(i)), keyMarker));
        }
        sb.append(header.toString().trim()).append("\n");

        // Separator
        for (int i = 0; i < columns.size(); i++) {
            sb.append(boxDivider(colWidths.get(i) + 1)).append(" ");
        }
        sb.append("\n");

        // Data rows (transpose columnar to row-based)
        for (int row = 0; row < rowCount; row++) {
            StringBuilder rowStr = new StringBuilder();
            for (int col = 0; col < columns.size(); col++) {
                String value = row < columns.get(col).values().size()
                        ? columns.get(col).values().get(row) : "";
                rowStr.append(String.format("%-" + (colWidths.get(col) + 1) + "s ",
                        truncate(value, colWidths.get(col))));
            }
            sb.append(rowStr.toString().trim()).append("\n");
        }

        // Column details section
        sb.append("\nColumn Details:\n");
        sb.append(String.format("%-20s %-15s %-5s %s\n", "Name", "Type", "Key", "Description"));
        sb.append(boxDivider(80)).append("\n");
        for (ColumnData col : columns) {
            sb.append(String.format("%-20s %-15s %-5s %s\n",
                    truncate(col.name(), 20),
                    truncate(col.type(), 15),
                    "true".equalsIgnoreCase(col.keyAttribute()) ? "Yes" : "",
                    truncate(col.description(), 40)));
        }

        return sb.toString();
    }
}
