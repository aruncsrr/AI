package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for GetStatementCoverage tool responses.
 * Converts raw statement coverage XML into human-readable line-by-line coverage report.
 */
@Slf4j
@Component
public class StatementCoverageFormatter extends AbstractADTFormatter {

    private static final String ADT_NS = "http://www.sap.com/adt/core";
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";

    // Pattern to extract position from URI fragment: #start=line,col;end=line,col
    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "#start=(\\d+),(\\d+)(?:;end=(\\d+),(\\d+))?");

    // ═══════════════════════════════════════════════════════════════════════════
    // Statement Coverage Data Classes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A single statement with coverage information.
     */
    private static class CoveredStatement {
        final int startLine;
        final int startColumn;
        final int endLine;
        final int endColumn;
        final int executedCount;
        final boolean executed;

        CoveredStatement(int startLine, int startColumn, int endLine, int endColumn, int executedCount) {
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
            this.executedCount = executedCount;
            this.executed = executedCount > 0;
        }
    }

    /**
     * Coverage for a processing block (method, form, etc.).
     */
    private static class BlockCoverage {
        final String name;
        final String sourceUri;
        final List<CoveredStatement> statements;

        BlockCoverage(String name, String sourceUri, List<CoveredStatement> statements) {
            this.name = name;
            this.sourceUri = sourceUri;
            this.statements = statements;
        }
    }

    @Override
    public String getToolName() {
        return "GetStatementCoverage";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<BlockCoverage> blocks = parseStatementCoverage(rawResponse);
        return formatStatementCoverage(blocks);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Statement Coverage Parsing
    // ═══════════════════════════════════════════════════════════════════════════

    private List<BlockCoverage> parseStatementCoverage(String xml) {
        List<BlockCoverage> blocks = new ArrayList<>();

        try {
            Document doc = XmlExtractor.parseXml(xml);

            // Find all statementsResponse elements
            NodeList responseNodes = doc.getElementsByTagNameNS("*", "statementsResponse");

            for (int i = 0; i < responseNodes.getLength(); i++) {
                Element responseElem = (Element) responseNodes.item(i);
                BlockCoverage block = parseStatementsResponse(responseElem);
                if (block != null) {
                    blocks.add(block);
                }
            }

            // If no statementsResponse, try parsing statements directly from root
            if (blocks.isEmpty()) {
                Element root = doc.getDocumentElement();
                List<CoveredStatement> statements = parseStatements(root);
                if (!statements.isEmpty()) {
                    blocks.add(new BlockCoverage("main", null, statements));
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse statement coverage", e);
        }

        return blocks;
    }

    private BlockCoverage parseStatementsResponse(Element responseElem) {
        String name = responseElem.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "block";
        }

        // Extract source URI from atom:link
        String sourceUri = extractSourceLink(responseElem);

        List<CoveredStatement> statements = parseStatements(responseElem);

        if (statements.isEmpty()) {
            return null;
        }

        return new BlockCoverage(name, sourceUri, statements);
    }

    private String extractSourceLink(Element parent) {
        // Look for atom:link with rel="http://www.sap.com/adt/relations/source"
        NodeList links = parent.getElementsByTagNameNS(ATOM_NS, "link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String rel = link.getAttribute("rel");
            if ("http://www.sap.com/adt/relations/source".equals(rel)) {
                return link.getAttribute("href");
            }
        }

        // Try non-namespaced links
        NodeList linksNoNs = parent.getElementsByTagName("link");
        for (int i = 0; i < linksNoNs.getLength(); i++) {
            Element link = (Element) linksNoNs.item(i);
            String rel = link.getAttribute("rel");
            if ("http://www.sap.com/adt/relations/source".equals(rel)) {
                return link.getAttribute("href");
            }
        }

        return null;
    }

    private List<CoveredStatement> parseStatements(Element parent) {
        List<CoveredStatement> statements = new ArrayList<>();

        NodeList stmtNodes = parent.getElementsByTagNameNS("*", "statement");
        for (int i = 0; i < stmtNodes.getLength(); i++) {
            Element stmtElem = (Element) stmtNodes.item(i);

            // Get executed count from attribute
            int executed = parseIntAttribute(stmtElem, "executed");

            // Get position from objectReference URI fragment
            int startLine = 0, startCol = 0, endLine = 0, endCol = 0;

            NodeList objRefs = stmtElem.getElementsByTagNameNS("*", "objectReference");
            if (objRefs.getLength() > 0) {
                Element objRef = (Element) objRefs.item(0);
                String uri = objRef.getAttributeNS(ADT_NS, "uri");
                if (uri == null || uri.isEmpty()) {
                    uri = objRef.getAttribute("adtcore:uri");
                }

                if (uri != null && !uri.isEmpty()) {
                    Matcher m = POSITION_PATTERN.matcher(uri);
                    if (m.find()) {
                        startLine = Integer.parseInt(m.group(1));
                        startCol = Integer.parseInt(m.group(2));
                        if (m.group(3) != null) {
                            endLine = Integer.parseInt(m.group(3));
                            endCol = Integer.parseInt(m.group(4));
                        } else {
                            endLine = startLine;
                            endCol = startCol;
                        }
                    }
                }
            }

            // Fallback: check for line/column attributes directly on statement
            if (startLine == 0) {
                startLine = parseIntAttribute(stmtElem, "line");
                startCol = parseIntAttribute(stmtElem, "column");
                endLine = startLine;
                endCol = startCol;
            }

            if (startLine > 0) {
                statements.add(new CoveredStatement(startLine, startCol, endLine, endCol, executed));
            }
        }

        // Sort by start line
        statements.sort((a, b) -> {
            int lineCmp = Integer.compare(a.startLine, b.startLine);
            return lineCmp != 0 ? lineCmp : Integer.compare(a.startColumn, b.startColumn);
        });

        return statements;
    }

    private int parseIntAttribute(Element elem, String attrName) {
        try {
            String value = elem.getAttribute(attrName);
            return value != null && !value.isEmpty() ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Statement Coverage Formatting
    // ═══════════════════════════════════════════════════════════════════════════

    private String formatStatementCoverage(List<BlockCoverage> blocks) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Coverage] Statement Coverage\n");
        sb.append(boxDivider(80)).append("\n\n");

        if (blocks.isEmpty()) {
            sb.append("No statement coverage data found.\n");
            sb.append("\nPossible reasons:\n");
            sb.append("  - The statement_uris may be invalid or expired\n");
            sb.append("  - The coverage measurement may have been cleared\n");
            sb.append("  - Try running unit tests again with with_coverage=true\n");
            return sb.toString();
        }

        int totalStatements = 0;
        int totalExecuted = 0;

        for (BlockCoverage block : blocks) {
            sb.append(String.format("Block: %s\n", block.name));
            if (block.sourceUri != null) {
                sb.append(String.format("Source: %s\n", block.sourceUri));
            }

            // Calculate block stats
            int blockExecuted = 0;
            int blockTotal = block.statements.size();
            for (CoveredStatement stmt : block.statements) {
                if (stmt.executed) blockExecuted++;
            }
            totalStatements += blockTotal;
            totalExecuted += blockExecuted;

            double percentage = blockTotal > 0 ? (blockExecuted * 100.0 / blockTotal) : 0;
            sb.append(String.format(java.util.Locale.ROOT, "Coverage: %.1f%% (%d/%d statements)\n\n", percentage, blockExecuted, blockTotal));

            // Group statements by line for cleaner output
            Map<Integer, List<CoveredStatement>> byLine = new LinkedHashMap<>();
            for (CoveredStatement stmt : block.statements) {
                byLine.computeIfAbsent(stmt.startLine, k -> new ArrayList<>()).add(stmt);
            }

            // Format statements (line numbers only since we don't have source code in formatter)
            sb.append(String.format("  %-3s %5s  %-6s\n", "", "Line", "Hits"));
            sb.append("  ").append(boxDivider(30)).append("\n");

            for (Map.Entry<Integer, List<CoveredStatement>> entry : byLine.entrySet()) {
                int line = entry.getKey();
                List<CoveredStatement> lineStmts = entry.getValue();

                int maxHits = 0;
                boolean lineExecuted = false;
                for (CoveredStatement stmt : lineStmts) {
                    if (stmt.executedCount > maxHits) maxHits = stmt.executedCount;
                    if (stmt.executed) lineExecuted = true;
                }

                String icon = lineExecuted ? "+" : "X";
                String hits = maxHits > 0 ? String.valueOf(maxHits) : "0";

                sb.append(String.format("  %s %5d  %-6s\n", icon, line, hits));
            }

            sb.append("\n");
        }

        // Overall summary
        sb.append(boxDivider(80)).append("\n");
        double overallPct = totalStatements > 0 ? (totalExecuted * 100.0 / totalStatements) : 0;
        sb.append(String.format(java.util.Locale.ROOT, "Total: %d/%d statements (%.1f%%)\n\n", totalExecuted, totalStatements, overallPct));
        sb.append("Legend: + = executed, X = not executed\n");

        return sb.toString();
    }
}
