package com.sapjco.mcp.formatters.read;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.ElementExtractor;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formatter for GetWhereUsed tool responses.
 * Converts raw where-used XML into human-readable usage report.
 */
@Slf4j
@Component
public class WhereUsedFormatter extends AbstractADTFormatter {

    private static final String ADT_NS = "http://www.sap.com/adt/core";

    // Where-used response uses <referencedObject> elements with nested <adtObject> children
    private static final ElementExtractor USAGE_REF_EXTRACTOR = ElementExtractor.builder("referencedObject")
            .field(FieldExtractor.builder("uri")
                    .fromAttribute("uri")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("objectIdentifier")
                    .fromChildText("objectIdentifier")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("type")
                    .fromChildAttribute("adtObject", ADT_NS, "type")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("name")
                    .fromChildAttribute("adtObject", ADT_NS, "name")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("packageName")
                    .fromNestedChildAttribute("adtObject", "packageRef", ADT_NS, "name")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "GetWhereUsed";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<UsageReference> references = parseWhereUsedResults(rawResponse);
        return formatResults(references);
    }

    /**
     * Parsed where-used reference.
     */
    private static class UsageReference {
        final String uri;
        final String type;
        final String name;
        final String packageName;
        final String objectIdentifier;

        UsageReference(String uri, String type, String name, String packageName, String objectIdentifier) {
            this.uri = uri;
            this.type = type;
            this.name = name;
            this.packageName = packageName;
            this.objectIdentifier = objectIdentifier;
        }
    }

    private List<UsageReference> parseWhereUsedResults(String xml) {
        return XmlExtractor.extract(xml, USAGE_REF_EXTRACTOR)
                .stream()
                .map(this::toUsageReference)
                .collect(Collectors.toList());
    }

    private UsageReference toUsageReference(Map<String, Object> data) {
        String type = (String) data.get("type");
        String uri = (String) data.get("uri");

        // Fallback: extract type from URI fragment if not in adtObject attribute
        if ((type == null || type.isEmpty()) && uri != null) {
            type = extractTypeFromUri(uri);
        }

        return new UsageReference(
                uri,
                type,
                (String) data.get("name"),
                (String) data.get("packageName"),
                (String) data.get("objectIdentifier")
        );
    }

    private String extractTypeFromUri(String uri) {
        if (uri == null || !uri.contains("#")) {
            return null;
        }

        int fragmentStart = uri.indexOf('#');
        if (fragmentStart < 0 || fragmentStart >= uri.length() - 1) {
            return null;
        }

        String fragment = uri.substring(fragmentStart + 1);
        String[] params = fragment.split(";");
        for (String param : params) {
            if (param.startsWith("type=")) {
                String encodedType = param.substring(5);
                try {
                    return URLDecoder.decode(encodedType, StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    log.debug("Failed to decode URI type: {}", encodedType);
                    return encodedType.replace("%2F", "/");
                }
            }
        }
        return null;
    }

    private String formatResults(List<UsageReference> references) {
        StringBuilder sb = new StringBuilder();
        sb.append("Where-Used Results\n");
        sb.append(boxDivider(80)).append("\n");

        if (references.isEmpty()) {
            sb.append("\nNo usages found.\n");
            return sb.toString();
        }

        // Group by type
        Map<String, Long> typeCounts = references.stream()
                .collect(Collectors.groupingBy(r -> r.type != null ? r.type : "unknown", Collectors.counting()));

        // Summary
        sb.append(String.format("\nFound %d usage%s in %d object%s\n",
                references.size(), references.size() == 1 ? "" : "s",
                typeCounts.size(), typeCounts.size() == 1 ? " type" : " types"));

        // Type breakdown
        sb.append("By Type: ");
        sb.append(typeCounts.entrySet().stream()
                .map(e -> String.format("%d %s", e.getValue(), formatTypeName(e.getKey())))
                .collect(Collectors.joining(", ")));
        sb.append("\n\n");

        // References table
        sb.append(String.format("%-35s %-12s %-20s\n", "Object", "Type", "Package"));
        sb.append(boxDivider(70)).append("\n");

        for (UsageReference ref : references) {
            sb.append(String.format("%-35s %-12s %-20s\n",
                    truncate(ref.name, 35),
                    formatTypeName(ref.type),
                    truncate(ref.packageName, 20)));
        }

        return sb.toString();
    }

    private String formatTypeName(String type) {
        if (type == null || type.isEmpty()) {
            return "unknown";
        }

        String[] parts = type.split("/");
        String mainType = parts[0];
        String subType = parts.length > 1 ? parts[1] : null;

        // Handle element-level types
        if (subType != null) {
            switch (subType) {
                case "OM": return "Method";
                case "OO": return "Section";
                case "OA": return "Attribute";
                case "OE": return "Event";
                case "OT": return "Type";
                case "OC": return "Class";
                case "OI": return "Interface";
                case "OCN": return "ConstItf";
                case "OEH": return "EvtHandler";
            }
        }

        // Handle main object types
        switch (mainType) {
            case "CLAS": return "Class";
            case "INTF": return "Interface";
            case "PROG": return "Program";
            case "FUGR": return "FuncGroup";
            case "FUNC": return "FuncModule";
            case "DTEL": return "DataElement";
            case "TABL": return "Table";
            case "DOMA": return "Domain";
            case "DDLS": return "CDSView";
            case "DEVC": return "Package";
            case "PINF": return "PkgInterface";
            case "FORM": return "Form";
            case "INCL": return "Include";
            case "MSAG": return "MsgClass";
            case "TRAN": return "TCode";
            case "VIEW": return "View";
            case "SHLP": return "SearchHelp";
            case "ENQU": return "LockObject";
            case "BADI": return "BAdI";
            case "XSLT": return "XSLT";
            default: return mainType;
        }
    }
}
