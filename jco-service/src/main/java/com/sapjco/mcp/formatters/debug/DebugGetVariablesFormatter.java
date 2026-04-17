package com.sapjco.mcp.formatters.debug;

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
 * Formatter for DebugGetVariables tool responses.
 * Formats variable hierarchy with types and values.
 */
@Slf4j
@Component
public class DebugGetVariablesFormatter extends AbstractADTFormatter {

    // Variable extractor for XML response
    private static final ElementExtractor VARIABLE_EXTRACTOR = ElementExtractor.builder("STPDA_ADT_VARIABLE")
            .field(FieldExtractor.builder("id")
                    .fromChildText("ID")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("name")
                    .fromChildText("NAME")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("declaredType")
                    .fromChildText("DECLARED_TYPE_NAME")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("actualType")
                    .fromChildText("ACTUAL_TYPE_NAME")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("value")
                    .fromChildText("VALUE")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("hexValue")
                    .fromChildText("HEX_VALUE")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("metaType")
                    .fromChildText("META_TYPE")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("length")
                    .fromChildText("LENGTH")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .field(FieldExtractor.builder("tableLines")
                    .fromChildText("TABLE_LINES")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .build();

    /**
     * Variable data class.
     */
    private static class Variable {
        final String id;
        final String name;
        final String declaredType;
        final String actualType;
        final String value;
        final String hexValue;
        final String metaType;
        final int length;
        final int tableLines;

        Variable(String id, String name, String declaredType, String actualType,
                 String value, String hexValue, String metaType, int length, int tableLines) {
            this.id = id;
            this.name = name;
            this.declaredType = declaredType;
            this.actualType = actualType;
            this.value = value;
            this.hexValue = hexValue;
            this.metaType = metaType;
            this.length = length;
            this.tableLines = tableLines;
        }

        boolean isStructure() {
            return "structure".equalsIgnoreCase(metaType);
        }

        boolean isTable() {
            return "table".equalsIgnoreCase(metaType);
        }

        boolean isExpandable() {
            return isStructure() || isTable();
        }
    }

    @Override
    public String getToolName() {
        return "DebugGetVariables";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        // Check if this is already formatted text (from handler)
        if (rawResponse.contains("Local Variables:") || rawResponse.contains("Root Variables:") ||
            rawResponse.contains("Variables for")) {
            return formatTextResponse(rawResponse);
        }

        // Parse XML response
        List<Variable> variables = parseVariables(rawResponse);
        return formatVariables(variables);
    }

    private String formatTextResponse(String rawResponse) {
        // Response is already formatted, add header
        StringBuilder sb = new StringBuilder();
        sb.append("Debug Variables\n");
        sb.append(boxDivider(80)).append("\n\n");
        sb.append(rawResponse.trim());
        return sb.toString();
    }

    private String formatVariables(List<Variable> variables) {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug Variables\n");
        sb.append(boxDivider(80)).append("\n\n");

        if (variables.isEmpty()) {
            sb.append("No variables found.\n");
            sb.append("\nUse parent_id parameter to specify scope:\n");
            sb.append("  @ROOT   - Root variable hierarchy\n");
            sb.append("  @LOCALS - Local variables at current position\n");
            sb.append("  <id>    - Expand a specific structure/table\n");
            return sb.toString();
        }

        // Variable table header
        sb.append(String.format("%-20s %-10s %-25s %s\n", "Name", "Type", "Value", "Expandable"));
        sb.append(boxDivider(80)).append("\n");

        for (Variable var : variables) {
            String name = truncate(var.name, 20);
            String type = truncate(var.declaredType != null ? var.declaredType : "", 10);

            // Format value based on type
            String value;
            if (var.isTable()) {
                value = String.format("[table: %d rows]", var.tableLines);
            } else if (var.isStructure()) {
                value = "[structure]";
            } else {
                value = truncate(var.value != null ? var.value : "", 25);
            }

            String expandable = var.isExpandable() ? "Yes" : "";

            sb.append(String.format("%-20s %-10s %-25s %s\n", name, type, value, expandable));
        }

        sb.append(boxDivider(80)).append("\n");
        sb.append(String.format("Total: %d variable%s\n", variables.size(), variables.size() == 1 ? "" : "s"));

        // Show expandable hint
        long expandableCount = variables.stream().filter(Variable::isExpandable).count();
        if (expandableCount > 0) {
            sb.append(String.format("\n%d expandable variable%s - use parent_id with variable ID to expand.\n",
                    expandableCount, expandableCount == 1 ? "" : "s"));
        }

        // Show hex values for primitives if available
        List<Variable> withHex = variables.stream()
                .filter(v -> v.hexValue != null && !v.hexValue.isEmpty() && !v.isExpandable())
                .limit(5)
                .collect(Collectors.toList());

        if (!withHex.isEmpty()) {
            sb.append("\nHex Values:\n");
            for (Variable var : withHex) {
                sb.append(String.format("  %-15s = %s\n", var.name, var.hexValue));
            }
        }

        return sb.toString();
    }

    private List<Variable> parseVariables(String xml) {
        return XmlExtractor.extract(xml, VARIABLE_EXTRACTOR)
                .stream()
                .map(this::toVariable)
                .collect(Collectors.toList());
    }

    private Variable toVariable(Map<String, Object> data) {
        return new Variable(
                (String) data.get("id"),
                (String) data.get("name"),
                (String) data.get("declaredType"),
                (String) data.get("actualType"),
                (String) data.get("value"),
                (String) data.get("hexValue"),
                (String) data.get("metaType"),
                data.get("length") != null ? (Integer) data.get("length") : 0,
                data.get("tableLines") != null ? (Integer) data.get("tableLines") : 0
        );
    }
}
