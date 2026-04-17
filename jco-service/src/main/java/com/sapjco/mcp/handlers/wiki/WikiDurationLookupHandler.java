package com.sapjco.mcp.handlers.wiki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapjco.mcp.handlers.AbstractWikiHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP handler for wiki_duration_lookup.
 *
 * Reads Wiki page 5888305446, section "5.3.1 Solution Alias Duration Rules",
 * extracts the table with columns [Solution Alias, Implementation Type, Days Duration],
 * and returns the matching Days Duration for a given solution alias + implementation type.
 */
@Slf4j
@Component
public class WikiDurationLookupHandler extends AbstractWikiHandler {

    private static final String WIKI_PAGE_ID    = "5888305446";
    private static final String SECTION_HEADING = "5.3.1";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("solution_alias",      "Solution Alias to look up (e.g. 'S/4HANA On Premise')")
                .requiredString("implementation_type", "Implementation Type (e.g. 'Template Solutions')")
                .buildTool("wiki_duration_lookup",
                        "Look up the Days Duration from the Solution Alias Duration Rules table " +
                        "(Wiki page 5888305446, section 5.3.1). " +
                        "Provide solution_alias and implementation_type to find the matching duration in days. " +
                        "Returns the Days Duration value.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        CallToolResult cfg = checkConfigured();
        if (cfg != null) return cfg;

        Map<String, Object> args = request.arguments();
        String solutionAlias      = requireString(args, "solution_alias").trim();
        String implementationType = requireString(args, "implementation_type").trim();

        log.info("wiki_duration_lookup: solution='{}' implType='{}'", solutionAlias, implementationType);

        try {
            // ── 1. Fetch the wiki page (storage HTML body) ─────────────────
            Map<String, String> params = new LinkedHashMap<>();
            params.put("expand", "body.storage");
            String json = wikiClient.get("/rest/api/content/" + WIKI_PAGE_ID, params);

            // ── 2. Extract storage HTML from JSON ──────────────────────────
            JsonNode root = MAPPER.readTree(json);
            String storageBody = root.path("body").path("storage").path("value").asText("");
            if (storageBody.isEmpty()) {
                return error("Wiki page body is empty or could not be retrieved.");
            }

            // ── 3. Parse the duration table ────────────────────────────────
            List<DurationRow> tableRows = parseDurationTable(storageBody);
            log.info("wiki_duration_lookup: parsed {} rows", tableRows.size());

            if (tableRows.isEmpty()) {
                return error("Could not find the Solution Alias Duration Rules table " +
                        "in section " + SECTION_HEADING + " of wiki page " + WIKI_PAGE_ID +
                        ". The page structure may have changed.");
            }

            // ── 4. Look up matching row ─────────────────────────────────────
            String duration = findDuration(tableRows, solutionAlias, implementationType);

            if (duration == null) {
                StringBuilder sb = new StringBuilder();
                sb.append("No match found for:\n");
                sb.append("  Solution Alias     : ").append(solutionAlias).append("\n");
                sb.append("  Implementation Type: ").append(implementationType).append("\n\n");
                sb.append("Available entries:\n");
                sb.append(formatTable(tableRows));
                return success(sb.toString());
            }

            // ── 5. Return result ────────────────────────────────────────────
            String response = String.format(
                    "Duration Lookup Result\n" +
                    "======================\n" +
                    "Solution Alias     : %s\n" +
                    "Implementation Type: %s\n" +
                    "Days Duration      : %s\n",
                    solutionAlias, implementationType, duration);
            return success(response);

        } catch (Exception e) {
            log.error("wiki_duration_lookup failed", e);
            return error(e);
        }
    }

    // ── Table parsing ─────────────────────────────────────────────────────────

    private record DurationRow(String solutionAlias, String implementationType, String daysDuration) {}

    /**
     * Strategy:
     *  1. Wrap the Confluence storage HTML in a root element and parse as XML.
     *  2. Locate the heading/paragraph containing "5.3.1".
     *  3. Find the first table element that follows it.
     *  4. If XML parse fails, fall back to regex-based plain-text extraction.
     */
    private List<DurationRow> parseDurationTable(String storageHtml) {
        // Try XML DOM parsing first
        try {
            List<DurationRow> rows = parseViaXml(storageHtml);
            if (!rows.isEmpty()) return rows;
        } catch (Exception e) {
            log.debug("XML parse failed, trying regex fallback: {}", e.getMessage());
        }

        // Fallback: regex-based extraction
        return parseViaRegex(storageHtml);
    }

    // ── XML-based parsing ─────────────────────────────────────────────────────

    private List<DurationRow> parseViaXml(String html) throws Exception {
        // Confluence storage format is XML-like but may have HTML entities.
        // Wrap in a root tag and try to parse.
        String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root>" +
                html.replaceAll("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)", "&amp;") +
                "</root>";

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);

        Document doc = dbf.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xmlContent)));

        // Find all table elements
        NodeList tables = doc.getElementsByTagName("table");
        if (tables.getLength() == 0) return Collections.emptyList();

        // Try to find the table following section 5.3.1
        Element targetTable = findTableAfterSection(doc, tables);
        if (targetTable == null) {
            // Fallback: first table with matching headers
            targetTable = findTableByHeaders(tables);
        }
        if (targetTable == null && tables.getLength() > 0) {
            targetTable = (Element) tables.item(0);
        }
        if (targetTable == null) return Collections.emptyList();

        return extractRowsFromXmlTable(targetTable);
    }

    private Element findTableAfterSection(Document doc, NodeList tables) {
        // Walk all text nodes looking for "5.3.1"
        // Then find the first table that appears after that position in the document
        NodeList allElements = doc.getElementsByTagName("*");
        Element sectionEl = null;
        for (int i = 0; i < allElements.getLength(); i++) {
            Element el = (Element) allElements.item(i);
            String text = el.getTextContent();
            if (text != null && text.contains(SECTION_HEADING)) {
                // prefer elements whose OWN text (not descendant) is short
                String own = getDirectText(el);
                if (own.contains(SECTION_HEADING) && own.length() < 300) {
                    sectionEl = el;
                    // don't break — keep the most specific (deepest) match
                }
            }
        }
        if (sectionEl == null) return null;

        // Find following sibling or parent's sibling that is a table
        return findFollowingTable(sectionEl);
    }

    private Element findFollowingTable(Node from) {
        Node sibling = from.getNextSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) sibling;
                if ("table".equalsIgnoreCase(el.getTagName())) return el;
                // Check inside this sibling
                NodeList inner = el.getElementsByTagName("table");
                if (inner.getLength() > 0) return (Element) inner.item(0);
            }
            sibling = sibling.getNextSibling();
        }
        // Move up one level and retry
        Node parent = from.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE
                && !"root".equals(((Element) parent).getTagName())) {
            return findFollowingTable(parent);
        }
        return null;
    }

    private Element findTableByHeaders(NodeList tables) {
        for (int i = 0; i < tables.getLength(); i++) {
            Element table = (Element) tables.item(i);
            String text = table.getTextContent().toLowerCase();
            if (text.contains("solution alias") && text.contains("implementation type")) {
                return table;
            }
        }
        return null;
    }

    private List<DurationRow> extractRowsFromXmlTable(Element table) {
        List<DurationRow> rows = new ArrayList<>();
        NodeList allRows = table.getElementsByTagName("tr");

        int colSol = -1, colImpl = -1, colDays = -1;
        int dataStart = 0;

        for (int r = 0; r < Math.min(5, allRows.getLength()); r++) {
            Element row = (Element) allRows.item(r);
            List<String> cellTexts = getCellTexts(row);
            for (int c = 0; c < cellTexts.size(); c++) {
                String t = cellTexts.get(c).toLowerCase();
                if (t.contains("solution alias"))        colSol  = c;
                if (t.contains("implementation type"))   colImpl = c;
                if (t.contains("days duration") || t.equals("days") || t.equals("duration")) colDays = c;
            }
            if (colSol >= 0 && colImpl >= 0 && colDays >= 0) {
                dataStart = r + 1;
                break;
            }
        }

        // Fallback column positions based on actual table structure:
        // [Solution Alias(0), Implementation Type(1), Scope(2), Days Duration(3), Buffer(4), Notes(5)]
        if (colSol  < 0) colSol  = 0;
        if (colImpl < 0) colImpl = 1;
        if (colDays < 0) colDays = 3;

        for (int r = dataStart; r < allRows.getLength(); r++) {
            Element row = (Element) allRows.item(r);
            List<String> cells = getCellTexts(row);
            int needed = Math.max(colSol, Math.max(colImpl, colDays)) + 1;
            if (cells.size() < needed) continue;

            String sol  = cells.get(colSol).trim();
            String impl = cells.get(colImpl).trim();
            String days = cells.get(colDays).trim();

            if (sol.isEmpty() && impl.isEmpty()) continue;
            rows.add(new DurationRow(sol, impl, days));
            log.debug("  Duration row: '{}' | '{}' | '{}'", sol, impl, days);
        }
        return rows;
    }

    private List<String> getCellTexts(Element row) {
        List<String> texts = new ArrayList<>();
        NodeList children = row.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tag = ((Element) child).getTagName().toLowerCase();
                if ("td".equals(tag) || "th".equals(tag)) {
                    texts.add(child.getTextContent().trim());
                }
            }
        }
        return texts;
    }

    private String getDirectText(Element el) {
        StringBuilder sb = new StringBuilder();
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.TEXT_NODE) {
                sb.append(children.item(i).getNodeValue());
            }
        }
        return sb.toString().trim();
    }

    // ── Regex fallback parsing ────────────────────────────────────────────────

    /**
     * Fallback: extract table rows using regex patterns on raw HTML text.
     * Looks for <tr>...<td>...</td>...</tr> blocks.
     */
    private List<DurationRow> parseViaRegex(String html) {
        List<DurationRow> rows = new ArrayList<>();

        // Find the section 5.3.1 marker, then extract text after it
        int sectionIdx = html.indexOf(SECTION_HEADING);
        String searchRegion = sectionIdx >= 0 ? html.substring(sectionIdx) : html;

        // Find first <table> in the region
        int tableStart = searchRegion.toLowerCase().indexOf("<table");
        if (tableStart < 0) {
            // Try from the full html
            tableStart = html.toLowerCase().indexOf("<table");
            searchRegion = html;
        }
        if (tableStart < 0) return rows;

        int tableEnd = searchRegion.toLowerCase().indexOf("</table>", tableStart);
        if (tableEnd < 0) tableEnd = searchRegion.length();

        String tableHtml = searchRegion.substring(tableStart, tableEnd + 8);

        // Extract all <tr> blocks
        Pattern trPat = Pattern.compile("<tr[^>]*>(.*?)</tr>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Pattern tdPat = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        List<List<String>> allRows = new ArrayList<>();
        Matcher trMatcher = trPat.matcher(tableHtml);
        while (trMatcher.find()) {
            List<String> cells = new ArrayList<>();
            Matcher tdMatcher = tdPat.matcher(trMatcher.group(1));
            while (tdMatcher.find()) {
                cells.add(stripTags(tdMatcher.group(1)));
            }
            if (!cells.isEmpty()) allRows.add(cells);
        }

        if (allRows.isEmpty()) return rows;

        // Default column positions based on actual 6-column table layout:
        // [Solution Alias(0), Implementation Type(1), Scope(2), Days Duration(3), Buffer(4), Notes(5)]
        int colSol = 0, colImpl = 1, colDays = 3;
        int dataStart = 0;
        for (int r = 0; r < Math.min(3, allRows.size()); r++) {
            List<String> cells = allRows.get(r);
            for (int c = 0; c < cells.size(); c++) {
                String t = cells.get(c).toLowerCase();
                if (t.contains("solution alias"))      colSol  = c;
                if (t.contains("implementation type")) colImpl = c;
                if (t.contains("days duration") || t.equals("days") || t.equals("duration")) colDays = c;
            }
            if (cells.stream().anyMatch(c -> c.toLowerCase().contains("solution alias"))) {
                dataStart = r + 1;
                break;
            }
        }

        for (int r = dataStart; r < allRows.size(); r++) {
            List<String> cells = allRows.get(r);
            int needed = Math.max(colSol, Math.max(colImpl, colDays)) + 1;
            if (cells.size() < needed) continue;
            String sol  = cells.get(colSol).trim();
            String impl = cells.get(colImpl).trim();
            String days = cells.get(colDays).trim();
            if (sol.isEmpty() && impl.isEmpty()) continue;
            rows.add(new DurationRow(sol, impl, days));
        }
        return rows;
    }

    private String stripTags(String html) {
        return html.replaceAll("<[^>]+>", "")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&#\\d+;", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    // ── Lookup logic ──────────────────────────────────────────────────────────

    private String findDuration(List<DurationRow> rows, String solutionAlias, String implementationType) {
        String solN  = norm(solutionAlias);
        String implN = norm(implementationType);

        // Pass 1: exact match on both
        for (DurationRow row : rows) {
            if (norm(row.solutionAlias()).equals(solN) && norm(row.implementationType()).equals(implN))
                return row.daysDuration();
        }
        // Pass 2: contains match on both
        for (DurationRow row : rows) {
            if (norm(row.solutionAlias()).contains(solN) && norm(row.implementationType()).contains(implN))
                return row.daysDuration();
        }
        // Pass 3: fuzzy — each key contains the other
        for (DurationRow row : rows) {
            boolean solMatch  = norm(row.solutionAlias()).contains(solN)
                             || solN.contains(norm(row.solutionAlias()));
            boolean implMatch = norm(row.implementationType()).contains(implN)
                             || implN.contains(norm(row.implementationType()));
            if (solMatch && implMatch) return row.daysDuration();
        }
        return null;
    }

    private String norm(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    // ── Text formatting ───────────────────────────────────────────────────────

    private String formatTable(List<DurationRow> rows) {
        int w1 = "Solution Alias".length(), w2 = "Implementation Type".length();
        for (DurationRow r : rows) {
            w1 = Math.max(w1, r.solutionAlias().length());
            w2 = Math.max(w2, r.implementationType().length());
        }
        String fmt = "  %-" + w1 + "s | %-" + w2 + "s | %s%n";
        String sep = "  " + "-".repeat(w1) + "-+-" + "-".repeat(w2) + "-+------\n";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(fmt, "Solution Alias", "Implementation Type", "Days"));
        sb.append(sep);
        for (DurationRow r : rows) {
            sb.append(String.format(fmt, r.solutionAlias(), r.implementationType(), r.daysDuration()));
        }
        return sb.toString();
    }
}
