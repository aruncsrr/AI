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
 * Formatter for GetStructure tool responses.
 * Converts raw structure XML into human-readable structure definition.
 */
@Slf4j
@Component
public class StructureFormatter extends AbstractADTFormatter {

    // FieldExtractors for root blueSource attributes (adtcore namespace)
    private static final List<FieldExtractor> ROOT_FIELDS = Arrays.asList(
            FieldExtractor.builder("name").fromAttribute("name").asString().build(),
            FieldExtractor.builder("description").fromAttribute("description").asString().build(),
            FieldExtractor.builder("type").fromAttribute("type").asString().build(),
            FieldExtractor.builder("responsible").fromAttribute("responsible").asString().build(),
            FieldExtractor.builder("masterLanguage").fromAttribute("masterLanguage").asString().build(),
            FieldExtractor.builder("abapLanguageVersion").fromAttribute("abapLanguageVersion").asString().build(),
            FieldExtractor.builder("changedAt").fromAttribute("changedAt").asString().build(),
            FieldExtractor.builder("changedBy").fromAttribute("changedBy").asString().build(),
            FieldExtractor.builder("createdBy").fromAttribute("createdBy").asString().build()
    );

    // ElementExtractor for package reference
    private static final ElementExtractor PACKAGE_REF_EXTRACTOR = ElementExtractor.builder("packageRef")
            .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
            .field(FieldExtractor.builder("description").fromAttribute("description").asString().build())
            .build();

    @Override
    public String getToolName() {
        return "GetStructure";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        return formatStructure(rawResponse);
    }

    private String formatStructure(String xml) {
        // Extract root attributes from blueSource element
        Map<String, Object> rootData = XmlExtractor.extractSingle(xml, ROOT_FIELDS);

        // Extract package reference
        List<Map<String, Object>> packageRefs = XmlExtractor.extract(xml, PACKAGE_REF_EXTRACTOR);
        String packageName = "";
        String packageDesc = "";
        if (!packageRefs.isEmpty()) {
            packageName = (String) packageRefs.get(0).getOrDefault("name", "");
            packageDesc = (String) packageRefs.get(0).getOrDefault("description", "");
        }

        String structureName = (String) rootData.getOrDefault("name", "");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Structure: %s\n", structureName.toUpperCase()));
        sb.append(boxDivider(70)).append("\n\n");

        // Basic metadata
        String description = (String) rootData.getOrDefault("description", "");
        String type = (String) rootData.getOrDefault("type", "");
        String responsible = (String) rootData.getOrDefault("responsible", "");
        String changedBy = (String) rootData.getOrDefault("changedBy", "");
        String changedAt = (String) rootData.getOrDefault("changedAt", "");
        String createdBy = (String) rootData.getOrDefault("createdBy", "");
        String abapLangVersion = (String) rootData.getOrDefault("abapLanguageVersion", "");

        sb.append("Properties:\n");
        if (!description.isEmpty()) {
            sb.append(String.format("  Description:    %s\n", description));
        }
        if (!type.isEmpty()) {
            String typeLabel = formatStructureType(type);
            sb.append(String.format("  Type:           %s\n", typeLabel));
        }
        if (!packageName.isEmpty()) {
            sb.append(String.format("  Package:        %s\n", packageName));
            if (!packageDesc.isEmpty()) {
                sb.append(String.format("                  (%s)\n", packageDesc));
            }
        }
        if (!abapLangVersion.isEmpty()) {
            sb.append(String.format("  ABAP Version:   %s\n", abapLangVersion));
        }

        sb.append("\nChange Information:\n");
        if (!responsible.isEmpty()) {
            sb.append(String.format("  Responsible:    %s\n", responsible));
        }
        if (!createdBy.isEmpty()) {
            sb.append(String.format("  Created By:     %s\n", createdBy));
        }
        if (!changedBy.isEmpty()) {
            sb.append(String.format("  Last Changed:   %s\n", changedBy));
        }
        if (!changedAt.isEmpty()) {
            sb.append(String.format("  Changed At:     %s\n", formatDate(changedAt)));
        }

        sb.append("\nNote: Structure field details are available via ABAP source code or\n");
        sb.append("      by using GetDataElement for individual field type information.\n");

        return sb.toString();
    }

    private String formatStructureType(String type) {
        if (type == null || type.isEmpty()) {
            return "";
        }
        switch (type) {
            case "TABL/DS": return "Structure";
            case "TABL/DT": return "Transparent Table";
            case "TABL/CI": return "Cluster Table";
            case "TABL/PL": return "Pooled Table";
            default: return type;
        }
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return "";
        }
        try {
            return isoDate.replace("T", " ").replace("Z", "").substring(0, 19);
        } catch (Exception e) {
            return isoDate;
        }
    }
}
