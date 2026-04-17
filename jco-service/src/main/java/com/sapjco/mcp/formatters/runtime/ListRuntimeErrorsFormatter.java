package com.sapjco.mcp.formatters.runtime;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatter for ListRuntimeErrors tool responses.
 * Converts raw Atom feed XML into a human-readable runtime error dump table.
 */
@Slf4j
@Component
public class ListRuntimeErrorsFormatter extends AbstractADTFormatter {

    private static class DumpEntry {
        final String user;
        final String errorType;
        final String program;
        final String timestamp;
        final String dumpId;

        DumpEntry(String user, String errorType, String program, String timestamp, String dumpId) {
            this.user = user;
            this.errorType = errorType;
            this.program = program;
            this.timestamp = timestamp;
            this.dumpId = dumpId;
        }
    }

    @Override
    public String getToolName() {
        return "ListRuntimeErrors";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        Document doc = parseXml(rawResponse);
        List<DumpEntry> entries = parseEntries(doc);
        return formatEntries(entries);
    }

    private List<DumpEntry> parseEntries(Document doc) {
        List<DumpEntry> entries = new ArrayList<>();
        List<Element> entryElements = getElementsByTagName(doc, "entry");

        for (Element entry : entryElements) {
            String user = "";
            List<Element> authors = getChildElements(entry, "author");
            if (!authors.isEmpty()) {
                user = getChildText(authors.get(0), "name");
            }

            String errorType = "";
            String program = "";
            List<Element> categories = getChildElements(entry, "category");
            for (Element cat : categories) {
                String label = getAttribute(cat, "label");
                String term = getAttribute(cat, "term");
                if ("ABAP runtime error".equals(label)) {
                    errorType = term;
                } else if ("Terminated ABAP program".equals(label)) {
                    program = term;
                }
            }

            String timestamp = getChildText(entry, "published");

            String dumpId = "";
            List<Element> links = getChildElements(entry, "link");
            for (Element link : links) {
                String rel = getAttribute(link, "rel");
                String type = getAttribute(link, "type");
                if ("self".equals(rel) && "text/plain".equals(type)) {
                    String href = getAttribute(link, "href");
                    dumpId = extractDumpId(href);
                    break;
                }
            }

            entries.add(new DumpEntry(user, errorType, program, timestamp, dumpId));
        }

        return entries;
    }

    /**
     * Extract dump ID from self-link href.
     * Example href: /sap/bc/adt/runtime/dump/20260307150108ldcierx_ERX_00%20...%20114/formatted
     * We want the segment after "/dump/" and before any trailing path like "/formatted".
     */
    private String extractDumpId(String href) {
        if (href == null || href.isEmpty()) return "";
        String marker = "/dump/";
        int start = href.indexOf(marker);
        if (start < 0) return href;
        String remainder = href.substring(start + marker.length());
        // Remove trailing path segments (e.g., "/formatted", "/summary")
        int slash = remainder.indexOf('/');
        if (slash > 0) {
            remainder = remainder.substring(0, slash);
        }
        return remainder;
    }

    private String formatEntries(List<DumpEntry> entries) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Runtime Error Dumps (%d entr%s)\n",
                entries.size(), entries.size() == 1 ? "y" : "ies"));
        sb.append(boxDivider(80)).append("\n");

        if (entries.isEmpty()) {
            sb.append("\nNo runtime errors found.\n");
            return sb.toString();
        }

        // Compute column widths dynamically
        int errorW = "Error Type".length();
        int progW = "Program".length();
        int userW = "User".length();
        for (DumpEntry e : entries) {
            errorW = Math.max(errorW, e.errorType.length());
            progW = Math.max(progW, e.program.length());
            userW = Math.max(userW, e.user.length());
        }
        // Cap column widths to reasonable maximums
        errorW = Math.min(errorW, 30);
        progW = Math.min(progW, 40);
        userW = Math.min(userW, 14);

        sb.append("\n");
        String headerFmt = String.format("%%4s  %%-%ds  %%-%ds  %%-%ds  %%s\n", errorW, progW, userW);
        sb.append(String.format(headerFmt, "#", "Error Type", "Program", "User", "Timestamp"));
        sb.append(boxDivider(80)).append("\n");

        String rowFmt = String.format("%%4s  %%-%ds  %%-%ds  %%-%ds  %%s\n", errorW, progW, userW);
        for (int i = 0; i < entries.size(); i++) {
            DumpEntry e = entries.get(i);
            sb.append(String.format(rowFmt,
                    String.valueOf(i + 1),
                    truncate(e.errorType, errorW),
                    truncate(e.program, progW),
                    truncate(e.user, userW),
                    e.timestamp));
        }

        // Dump IDs section
        sb.append("\nDump IDs (for use with GetRuntimeError):\n");
        for (int i = 0; i < entries.size(); i++) {
            sb.append(String.format("  %d: %s\n", i + 1, entries.get(i).dumpId));
        }

        return sb.toString();
    }
}
