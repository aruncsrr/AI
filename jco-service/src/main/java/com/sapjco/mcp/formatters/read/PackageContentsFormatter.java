package com.sapjco.mcp.formatters.read;

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
 * Formatter for GetPackageContents tool responses.
 * Converts raw package contents XML into human-readable object list.
 */
@Slf4j
@Component
public class PackageContentsFormatter extends AbstractADTFormatter {

    // ElementExtractor for SEU_ADT_REPOSITORY_OBJ_NODE elements in package contents
    // Actual XML structure uses child elements, not attributes:
    // <SEU_ADT_REPOSITORY_OBJ_NODE>
    //     <OBJECT_TYPE>CLAS/OC</OBJECT_TYPE>
    //     <OBJECT_NAME>/BOBF/CL_DEMO_SALES_ORDER</OBJECT_NAME>
    //     <DESCRIPTION>Sales Order Demo</DESCRIPTION>
    //     <OBJECT_URI>/sap/bc/adt/oo/classes/...</OBJECT_URI>
    // </SEU_ADT_REPOSITORY_OBJ_NODE>
    private static final ElementExtractor OBJECT_REF_EXTRACTOR = ElementExtractor.builder("SEU_ADT_REPOSITORY_OBJ_NODE")
            .field(FieldExtractor.builder("name")
                    .fromChildText("OBJECT_NAME")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("type")
                    .fromChildText("OBJECT_TYPE")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("uri")
                    .fromChildText("OBJECT_URI")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("description")
                    .fromChildText("DESCRIPTION")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "GetPackageContents";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<Map<String, Object>> objects = XmlExtractor.extract(rawResponse, OBJECT_REF_EXTRACTOR);
        return formatPackageContents(objects);
    }

    private String formatPackageContents(List<Map<String, Object>> objects) {
        StringBuilder sb = new StringBuilder();
        sb.append("Package Contents\n");
        sb.append(boxDivider(80)).append("\n");

        if (objects.isEmpty()) {
            sb.append("\nNo objects found in package.\n");
            return sb.toString();
        }

        // Group by type
        Map<String, Long> typeCounts = objects.stream()
                .collect(Collectors.groupingBy(
                        r -> (String) r.getOrDefault("type", "unknown"),
                        Collectors.counting()));

        // Summary
        sb.append(String.format("\nFound %d object%s in %d type%s\n",
                objects.size(), objects.size() == 1 ? "" : "s",
                typeCounts.size(), typeCounts.size() == 1 ? "" : "s"));

        // Type breakdown
        sb.append("By Type: ");
        sb.append(typeCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> String.format("%d %s", e.getValue(), formatTypeName(e.getKey())))
                .collect(Collectors.joining(", ")));
        sb.append("\n\n");

        // Objects table
        sb.append(String.format("%-35s %-12s %s\n", "Name", "Type", "Description"));
        sb.append(boxDivider(80)).append("\n");

        for (Map<String, Object> obj : objects) {
            String name = (String) obj.getOrDefault("name", "");
            String type = (String) obj.getOrDefault("type", "");
            String description = (String) obj.getOrDefault("description", "");

            sb.append(String.format("%-35s %-12s %s\n",
                    truncate(name, 35),
                    formatTypeName(type),
                    truncate(description, 40)));
        }

        return sb.toString();
    }

    private String formatTypeName(String type) {
        if (type == null || type.isEmpty()) {
            return "unknown";
        }
        switch (type.toUpperCase()) {
            case "CLAS": return "Class";
            case "INTF": return "Interface";
            case "PROG": return "Program";
            case "FUGR": return "FuncGroup";
            case "FUNC": return "FuncModule";
            case "DTEL": return "DataElem";
            case "TABL": return "Table";
            case "DOMA": return "Domain";
            case "DDLS": return "CDSView";
            case "DEVC": return "Package";
            case "MSAG": return "MsgClass";
            case "TRAN": return "TCode";
            case "VIEW": return "View";
            case "SHLP": return "SearchHelp";
            case "ENQU": return "LockObj";
            case "XSLT": return "XSLT";
            default: return type;
        }
    }
}
