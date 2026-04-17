package com.sapjco.mcp.formatters.bopf;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.ElementExtractor;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for GetBopfBusinessObject tool responses.
 * Converts raw BOPF Business Object XML into human-readable BO definition.
 */
@Slf4j
@Component
public class BopfBusinessObjectFormatter extends AbstractADTFormatter {

    // ADT Core namespace for nested element attributes
    private static final String ADTCORE_NS = "http://www.sap.com/adt/core";

    // ElementExtractors for BOPF components
    private static final ElementExtractor NODE_EXTRACTOR = ElementExtractor.builder("nodes")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .field(FieldExtractor.builder("nodeID").fromAttribute("nodeID").asString().build())
            .field(FieldExtractor.builder("dataType")
                    .fromChildAttribute("persistentStructureRef", ADTCORE_NS, "name")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("tableName")
                    .fromChildAttribute("persistentTableRef", ADTCORE_NS, "name")
                    .asString()
                    .build())
            .build();

    private static final ElementExtractor ACTION_EXTRACTOR = ElementExtractor.builder("actions")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .field(FieldExtractor.builder("actionID").fromAttribute("actionID").asString().build())
            .field(FieldExtractor.builder("category").fromAttribute("category").asString().build())
            .field(FieldExtractor.builder("implementationClass")
                    .fromChildAttribute("implementationClassRef", ADTCORE_NS, "name")
                    .asString()
                    .build())
            .build();

    private static final ElementExtractor ASSOC_EXTRACTOR = ElementExtractor.builder("associations")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .field(FieldExtractor.builder("associationID").fromAttribute("associationID").asString().build())
            .field(FieldExtractor.builder("implementationType").fromAttribute("implementationType").asString().build())
            .field(FieldExtractor.builder("multiplicity").fromAttribute("multiplicity").asString().build())
            .field(FieldExtractor.builder("targetNodeName")
                    .fromChildAttribute("targetNodeRef", ADTCORE_NS, "name")
                    .asString()
                    .build())
            .build();

    private static final ElementExtractor QUERY_EXTRACTOR = ElementExtractor.builder("queries")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .field(FieldExtractor.builder("queryID").fromAttribute("queryID").asString().build())
            .field(FieldExtractor.builder("category").fromAttribute("category").asString().build())
            .field(FieldExtractor.builder("implementationClass")
                    .fromChildAttribute("implementationClassRef", ADTCORE_NS, "name")
                    .asString()
                    .build())
            .build();

    private static final ElementExtractor DETERM_EXTRACTOR = ElementExtractor.builder("determinations")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .field(FieldExtractor.builder("category").fromAttribute("category").asString().build())
            .field(FieldExtractor.builder("implementationClass")
                    .fromChildAttribute("implementationClassRef", ADTCORE_NS, "name")
                    .asString()
                    .build())
            .build();

    private static final ElementExtractor VALID_EXTRACTOR = ElementExtractor.builder("validations")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .field(FieldExtractor.builder("validationID").fromAttribute("validationID").asString().build())
            .field(FieldExtractor.builder("category").fromAttribute("category").asString().build())
            .field(FieldExtractor.builder("implementationClass")
                    .fromChildAttribute("implementationClassRef", ADTCORE_NS, "name")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "GetBopfBusinessObject";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatBopfBusinessObject(rawResponse, "Unknown", "active");
    }

    /**
     * Format with additional context about the BO name and version.
     */
    public String format(String rawResponse, String boName, String version) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatBopfBusinessObject(rawResponse, boName, version);
    }

    // ========================================================================
    // BOPF Business Object Formatting
    // ========================================================================

    private String formatBopfBusinessObject(String xml, String boName, String version) {
        BopfMetadata metadata = extractBoMetadata(xml);
        BopfSummary summary = parseBopfSummary(xml);

        // Extract components using XmlExtractor
        List<Map<String, Object>> nodes = XmlExtractor.extract(xml, NODE_EXTRACTOR);
        List<Map<String, Object>> actions = XmlExtractor.extract(xml, ACTION_EXTRACTOR);
        List<Map<String, Object>> associations = XmlExtractor.extract(xml, ASSOC_EXTRACTOR);
        List<Map<String, Object>> queries = XmlExtractor.extract(xml, QUERY_EXTRACTOR);
        List<Map<String, Object>> determinations = XmlExtractor.extract(xml, DETERM_EXTRACTOR);
        List<Map<String, Object>> validations = XmlExtractor.extract(xml, VALID_EXTRACTOR);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("BOPF Business Object: %s (version: %s)\n", boName, version));
        sb.append(boxDivider(80)).append("\n\n");

        // Metadata
        sb.append("Properties:\n");
        sb.append(String.format("  Name:              %s\n", metadata.name));
        sb.append(String.format("  Type:              %s\n", metadata.type));
        sb.append(String.format("  Programming Model: %s\n", metadata.programmingModel));
        sb.append(String.format("  Object Category:   %s\n", metadata.objectCategory));

        // Summary
        int total = summary.nodes + summary.actions + summary.associations +
                summary.queries + summary.determinations + summary.validations;
        sb.append(String.format("\nTotal Components: %d\n", total));
        sb.append(String.format("  Nodes: %d | Actions: %d | Associations: %d | Queries: %d | Determinations: %d | Validations: %d\n",
                summary.nodes, summary.actions, summary.associations,
                summary.queries, summary.determinations, summary.validations));

        // Nodes
        if (!nodes.isEmpty()) {
            sb.append(String.format("\nNodes (%d):\n", nodes.size()));
            sb.append(String.format("%-30s %-40s %s\n", "Name", "Data Type", "Table"));
            sb.append(boxDivider(80)).append("\n");
            for (Map<String, Object> node : nodes) {
                String name = (String) node.getOrDefault("name", "");
                String dataType = (String) node.getOrDefault("dataType", "");
                String tableName = (String) node.getOrDefault("tableName", "");
                sb.append(String.format("%-30s %-40s %s\n",
                        truncate(name, 30), truncate(dataType, 40), truncate(tableName, 20)));
            }
        }

        // Actions
        if (!actions.isEmpty()) {
            sb.append(String.format("\nActions (%d):\n", actions.size()));
            sb.append(String.format("%-30s %-10s %s\n", "Name", "Category", "Implementation Class"));
            sb.append(boxDivider(100)).append("\n");
            for (Map<String, Object> action : actions) {
                String name = (String) action.getOrDefault("name", "");
                String category = (String) action.getOrDefault("category", "");
                String implClass = (String) action.getOrDefault("implementationClass", "");
                sb.append(String.format("%-30s %-10s %s\n", truncate(name, 30), category, truncate(implClass, 55)));
            }
        }

        // Associations
        if (!associations.isEmpty()) {
            sb.append(String.format("\nAssociations (%d):\n", associations.size()));
            sb.append(String.format("%-25s %-15s %-8s %s\n", "Name", "Type", "Card.", "Target Node"));
            sb.append(boxDivider(100)).append("\n");
            for (Map<String, Object> assoc : associations) {
                String name = (String) assoc.getOrDefault("name", "");
                String implType = (String) assoc.getOrDefault("implementationType", "");
                String multiplicity = (String) assoc.getOrDefault("multiplicity", "");
                String target = (String) assoc.getOrDefault("targetNodeName", "");
                sb.append(String.format("%-25s %-15s %-8s %s\n",
                        truncate(name, 25), truncate(implType, 15), multiplicity, truncate(target, 45)));
            }
        }

        // Queries
        if (!queries.isEmpty()) {
            sb.append(String.format("\nQueries (%d):\n", queries.size()));
            sb.append(String.format("%-30s %-20s %s\n", "Name", "Category", "Implementation Class"));
            sb.append(boxDivider(100)).append("\n");
            for (Map<String, Object> query : queries) {
                String name = (String) query.getOrDefault("name", "");
                String category = (String) query.getOrDefault("category", "");
                String implClass = (String) query.getOrDefault("implementationClass", "");
                sb.append(String.format("%-30s %-20s %s\n", truncate(name, 30), truncate(category, 20), truncate(implClass, 45)));
            }
        }

        // Determinations
        if (!determinations.isEmpty()) {
            sb.append(String.format("\nDeterminations (%d):\n", determinations.size()));
            sb.append(String.format("%-30s %-25s %s\n", "Name", "Category", "Implementation Class"));
            sb.append(boxDivider(100)).append("\n");
            for (Map<String, Object> det : determinations) {
                String name = (String) det.getOrDefault("name", "");
                String category = (String) det.getOrDefault("category", "");
                String implClass = (String) det.getOrDefault("implementationClass", "");
                sb.append(String.format("%-30s %-25s %s\n", truncate(name, 30), truncate(category, 25), truncate(implClass, 40)));
            }
        }

        // Validations
        if (!validations.isEmpty()) {
            sb.append(String.format("\nValidations (%d):\n", validations.size()));
            sb.append(String.format("%-30s %-20s %s\n", "Name", "Category", "Implementation Class"));
            sb.append(boxDivider(100)).append("\n");
            for (Map<String, Object> val : validations) {
                String name = (String) val.getOrDefault("name", "");
                String category = (String) val.getOrDefault("category", "");
                String implClass = (String) val.getOrDefault("implementationClass", "");
                sb.append(String.format("%-30s %-20s %s\n", truncate(name, 30), truncate(category, 20), truncate(implClass, 45)));
            }
        }

        sb.append("\nUse raw_response=true to see full XML, or output_to_file=true for large BOs.\n");

        return sb.toString();
    }

    /**
     * Parse BOPF XML to extract component counts.
     */
    private BopfSummary parseBopfSummary(String xml) {
        return new BopfSummary(
            countMatches(xml, "<bo:nodes[^>]*bo:name=\"[^\"]+\""),
            countMatches(xml, "<bo:actions[^>]*bo:name=\"[^\"]+\""),
            countMatches(xml, "<bo:associations[^>]*bo:name=\"[^\"]+\""),
            countMatches(xml, "<bo:queries[^>]*bo:name=\"[^\"]+\""),
            countMatches(xml, "<bo:determinations[^>]*bo:name=\"[^\"]+\""),
            countMatches(xml, "<bo:validations[^>]*bo:name=\"[^\"]+\"")
        );
    }

    private int countMatches(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Extract basic BO metadata from XML.
     */
    private BopfMetadata extractBoMetadata(String xml) {
        return new BopfMetadata(
            extractAttribute(xml, "adtcore:name=\"([^\"]+)\"", "Unknown"),
            extractAttribute(xml, "adtcore:type=\"([^\"]+)\"", "Unknown"),
            extractAttribute(xml, "bo:programmingModel=\"([^\"]+)\"", "Unknown"),
            extractAttribute(xml, "bo:objectCategory=\"([^\"]+)\"", "Unknown")
        );
    }

    private String extractAttribute(String xml, String regex, String defaultValue) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private record BopfSummary(int nodes, int actions, int associations, int queries, int determinations, int validations) {}
    private record BopfMetadata(String name, String type, String programmingModel, String objectCategory) {}
}
