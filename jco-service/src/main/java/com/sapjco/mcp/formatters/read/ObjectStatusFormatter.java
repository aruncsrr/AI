package com.sapjco.mcp.formatters.read;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Formatter for GetObjectStatus tool responses.
 * Converts raw object metadata XML into human-readable activation status.
 *
 * <p>The adtcore:version attribute indicates the object's activation state:</p>
 * <ul>
 *   <li>{@code active} - Only active version exists</li>
 *   <li>{@code inactive} - Only inactive version (new object not yet activated)</li>
 *   <li>{@code activeWithInactiveVersion} - Active exists AND pending inactive changes</li>
 *   <li>{@code partlyActive} - Mixed state (some includes active, some inactive)</li>
 * </ul>
 */
@Slf4j
@Component
public class ObjectStatusFormatter extends AbstractADTFormatter {

    private static final String ADT_NS = "http://www.sap.com/adt/core";

    // FieldExtractors for root attributes (adtcore namespace)
    private static final List<FieldExtractor> STATUS_FIELDS = List.of(
            FieldExtractor.builder("name").fromNamespacedAttribute(ADT_NS, "name").asString().build(),
            FieldExtractor.builder("version").fromNamespacedAttribute(ADT_NS, "version").asString().build(),
            FieldExtractor.builder("type").fromNamespacedAttribute(ADT_NS, "type").asString().build(),
            FieldExtractor.builder("description").fromNamespacedAttribute(ADT_NS, "description").asString().build()
    );

    @Override
    public String getToolName() {
        return "GetObjectStatus";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        Map<String, Object> statusData = XmlExtractor.extractSingle(rawResponse, STATUS_FIELDS);
        return formatObjectStatus(statusData);
    }

    private String formatObjectStatus(Map<String, Object> statusData) {
        String name = (String) statusData.getOrDefault("name", "unknown");
        String version = (String) statusData.getOrDefault("version", "unknown");
        String type = (String) statusData.getOrDefault("type", "");
        String description = (String) statusData.getOrDefault("description", "");

        boolean hasInactiveChanges = hasInactiveChanges(version);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Object Status: %s\n", name.toUpperCase()));
        sb.append(boxDivider(60)).append("\n\n");

        sb.append("Properties:\n");
        if (!description.isEmpty()) {
            sb.append(String.format("  Description:  %s\n", description));
        }
        if (!type.isEmpty()) {
            sb.append(String.format("  Type:         %s\n", type));
        }
        sb.append(String.format("  Version:      %s\n", version));

        sb.append("\nActivation Status:\n");
        sb.append(String.format("  Has Inactive Changes: %s\n", hasInactiveChanges ? "YES" : "NO"));

        if (hasInactiveChanges) {
            sb.append("\n");
            sb.append("  WARNING: This object has inactive changes.\n");
            sb.append("  Use ActivateObject to activate before running tests.\n");
        } else {
            sb.append("\n");
            sb.append("  Object is fully activated. Tests will run against current code.\n");
        }

        return sb.toString();
    }

    /**
     * Check if an object has inactive changes based on its version attribute.
     *
     * @param version The adtcore:version attribute value
     * @return true if the object has inactive (unactivated) changes
     */
    public static boolean hasInactiveChanges(String version) {
        if (version == null) {
            return false;
        }
        return version.equals("activeWithInactiveVersion")
                || version.equals("inactive")
                || version.equals("partlyActive");
    }
}
