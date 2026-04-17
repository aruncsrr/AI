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
import java.util.List;

/**
 * Formatter for GetCoverageResult tool responses.
 * Converts raw coverage measurement XML into human-readable coverage report.
 */
@Slf4j
@Component
public class CoverageResultFormatter extends AbstractADTFormatter {

    private static final String ADT_NS = "http://www.sap.com/adt/core";
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";

    // Atom link relations for coverage URIs
    private static final String REL_BULK_STATEMENTS = "http://www.sap.com/adt/relations/runtime/traces/coverage/results/bulkstatements";
    private static final String REL_STATEMENTS = "http://www.sap.com/adt/relations/runtime/traces/coverage/results/statements";

    // ═══════════════════════════════════════════════════════════════════════════
    // Coverage Data Classes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage metric for a single type (statement, branch, or procedure).
     */
    private static class CoverageMetric {
        final int total;
        final int executed;

        CoverageMetric(int total, int executed) {
            this.total = total;
            this.executed = executed;
        }

        double getPercentage() {
            return total > 0 ? (executed * 100.0 / total) : 0.0;
        }
    }

    /**
     * Coverage summary containing all metric types.
     */
    private static class CoverageSummary {
        final CoverageMetric statement;
        final CoverageMetric branch;
        final CoverageMetric procedure;

        CoverageSummary(CoverageMetric statement, CoverageMetric branch, CoverageMetric procedure) {
            this.statement = statement;
            this.branch = branch;
            this.procedure = procedure;
        }
    }

    /**
     * Coverage node representing a single object's coverage.
     */
    private static class CoverageNode {
        final String name;
        final String uri;
        final String statementUri;  // URI for fetching statement-level coverage
        final CoverageMetric statement;
        final CoverageMetric branch;
        final CoverageMetric procedure;

        CoverageNode(String name, String uri, String statementUri, CoverageMetric statement,
                     CoverageMetric branch, CoverageMetric procedure) {
            this.name = name;
            this.uri = uri;
            this.statementUri = statementUri;
            this.statement = statement;
            this.branch = branch;
            this.procedure = procedure;
        }
    }

    /**
     * Complete coverage result.
     */
    private static class CoverageResult {
        final CoverageSummary summary;
        final List<CoverageNode> nodes;
        final String bulkStatementsUri;  // URI for fetching bulk statement coverage

        CoverageResult(CoverageSummary summary, List<CoverageNode> nodes, String bulkStatementsUri) {
            this.summary = summary;
            this.nodes = nodes;
            this.bulkStatementsUri = bulkStatementsUri;
        }
    }

    @Override
    public String getToolName() {
        return "GetCoverageResult";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        CoverageResult result = parseCoverageResults(rawResponse);
        return formatCoverageResults(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Coverage Results Parsing
    // ═══════════════════════════════════════════════════════════════════════════

    private CoverageResult parseCoverageResults(String xml) {
        CoverageSummary summary = null;
        List<CoverageNode> nodes = new ArrayList<>();
        String bulkStatementsUri = null;

        try {
            Document doc = XmlExtractor.parseXml(xml);

            // Extract bulk statements URI from root-level atom:link
            bulkStatementsUri = extractLinkByRel(doc.getDocumentElement(), REL_BULK_STATEMENTS);

            // Try to get summary from <summary> element first (test data format)
            NodeList summaryNodes = doc.getElementsByTagNameNS("*", "summary");
            if (summaryNodes.getLength() > 0) {
                Element summaryElem = (Element) summaryNodes.item(0);
                summary = parseCoverageSummary(summaryElem);
            }

            // If no summary, get from first node's <coverages> (real SAP format)
            // Real SAP responses have: <node><coverages><coverage .../></coverages></node>
            if (summary == null) {
                NodeList allNodeElements = doc.getElementsByTagNameNS("*", "node");
                if (allNodeElements.getLength() > 0) {
                    Element firstNode = (Element) allNodeElements.item(0);
                    summary = parseCoverageSummary(firstNode);
                }
            }

            // Parse all nodes for per-object breakdown
            NodeList allNodes = doc.getElementsByTagNameNS("*", "node");
            for (int i = 0; i < allNodes.getLength(); i++) {
                Element nodeElem = (Element) allNodes.item(i);
                CoverageNode node = parseCoverageNode(nodeElem);
                if (node != null) {
                    nodes.add(node);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse coverage results", e);
        }

        if (summary == null) {
            summary = new CoverageSummary(
                    new CoverageMetric(0, 0),
                    new CoverageMetric(0, 0),
                    new CoverageMetric(0, 0)
            );
        }

        return new CoverageResult(summary, nodes, bulkStatementsUri);
    }

    private String extractLinkByRel(Element parent, String rel) {
        // Look for atom:link elements with matching rel attribute
        NodeList links = parent.getElementsByTagNameNS(ATOM_NS, "link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String linkRel = link.getAttribute("rel");
            if (rel.equals(linkRel)) {
                return link.getAttribute("href");
            }
        }

        // Also check for non-namespaced link elements (some responses use this)
        NodeList linksNoNs = parent.getElementsByTagName("link");
        for (int i = 0; i < linksNoNs.getLength(); i++) {
            Element link = (Element) linksNoNs.item(i);
            String linkRel = link.getAttribute("rel");
            if (rel.equals(linkRel)) {
                return link.getAttribute("href");
            }
        }

        return null;
    }

    private CoverageSummary parseCoverageSummary(Element nodeElement) {
        CoverageMetric statement = null;
        CoverageMetric branch = null;
        CoverageMetric procedure = null;

        // Look for <coverages> wrapper element first (real ADT response format)
        NodeList coveragesNodes = nodeElement.getElementsByTagNameNS("*", "coverages");
        if (coveragesNodes.getLength() > 0) {
            Element coveragesElem = (Element) coveragesNodes.item(0);

            // Now get <coverage> elements from within <coverages>
            NodeList coverageNodes = coveragesElem.getElementsByTagNameNS("*", "coverage");
            for (int i = 0; i < coverageNodes.getLength(); i++) {
                Element coverageElem = (Element) coverageNodes.item(i);

                String type = coverageElem.getAttribute("type");
                int total = parseIntAttribute(coverageElem, "total");
                int executed = parseIntAttribute(coverageElem, "executed");

                CoverageMetric metric = new CoverageMetric(total, executed);

                switch (type.toLowerCase()) {
                    case "statement":
                        statement = metric;
                        break;
                    case "branch":
                        branch = metric;
                        break;
                    case "procedure":
                        procedure = metric;
                        break;
                }
            }
        }

        // Fallback: look for direct <coverage> children (test XML format)
        if (statement == null && branch == null && procedure == null) {
            NodeList directCoverageNodes = nodeElement.getElementsByTagNameNS("*", "coverage");
            for (int i = 0; i < directCoverageNodes.getLength(); i++) {
                Element coverageElem = (Element) directCoverageNodes.item(i);

                // Only process direct children
                if (coverageElem.getParentNode() != nodeElement) {
                    continue;
                }

                String type = coverageElem.getAttribute("type");
                int total = parseIntAttribute(coverageElem, "total");
                int executed = parseIntAttribute(coverageElem, "executed");

                CoverageMetric metric = new CoverageMetric(total, executed);

                switch (type.toLowerCase()) {
                    case "statement":
                        statement = metric;
                        break;
                    case "branch":
                        branch = metric;
                        break;
                    case "procedure":
                        procedure = metric;
                        break;
                }
            }
        }

        if (statement == null && branch == null && procedure == null) {
            return null;
        }

        return new CoverageSummary(
                statement != null ? statement : new CoverageMetric(0, 0),
                branch != null ? branch : new CoverageMetric(0, 0),
                procedure != null ? procedure : new CoverageMetric(0, 0)
        );
    }

    private CoverageNode parseCoverageNode(Element nodeElem) {
        // Get object reference
        String name = null;
        String uri = null;

        NodeList objRefs = nodeElem.getElementsByTagNameNS("*", "objectReference");
        if (objRefs.getLength() > 0) {
            Element objRef = (Element) objRefs.item(0);
            name = objRef.getAttributeNS(ADT_NS, "name");
            uri = objRef.getAttributeNS(ADT_NS, "uri");
        }

        // Extract statement URI from atom:link
        String statementUri = extractLinkByRel(nodeElem, REL_STATEMENTS);

        // Parse coverage metrics
        CoverageSummary coverage = parseCoverageSummary(nodeElem);
        if (coverage == null) {
            return null;
        }

        return new CoverageNode(
                name != null ? name : "Unknown",
                uri,
                statementUri,
                coverage.statement,
                coverage.branch,
                coverage.procedure
        );
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
    // Coverage Results Formatting
    // ═══════════════════════════════════════════════════════════════════════════

    private String formatCoverageResults(CoverageResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Coverage] Coverage Results\n");
        sb.append(boxDivider(80)).append("\n\n");

        // Summary section
        sb.append("Summary\n");
        sb.append(formatCoverageMetricLine("  Statement:", result.summary.statement));
        sb.append(formatCoverageMetricLine("  Branch:   ", result.summary.branch));
        sb.append(formatCoverageMetricLine("  Procedure:", result.summary.procedure));
        sb.append("\n");

        // Per-object coverage
        if (!result.nodes.isEmpty()) {
            sb.append("Per-Object Coverage\n");
            sb.append(String.format("  %-45s %-12s %-12s %-12s\n",
                    "Object", "Statement", "Branch", "Procedure"));
            sb.append("  ").append(boxDivider(76)).append("\n");

            for (CoverageNode node : result.nodes) {
                String statement = formatPercentage(node.statement);
                String branch = formatPercentage(node.branch);
                String procedure = formatPercentage(node.procedure);

                sb.append(String.format("  %-45s %-12s %-12s %-12s\n",
                        truncate(node.name, 45), statement, branch, procedure));
            }
            sb.append("\n");
        }

        // Statement-level data section
        sb.append("Statement-Level Data\n");
        if (result.bulkStatementsUri != null) {
            sb.append("  bulk_statements_uri: ").append(result.bulkStatementsUri).append("\n");
        }

        // Collect statement URIs from nodes
        List<String> statementUris = new ArrayList<>();
        for (CoverageNode node : result.nodes) {
            if (node.statementUri != null && !node.statementUri.isEmpty()) {
                statementUris.add(node.statementUri);
            }
        }

        if (!statementUris.isEmpty()) {
            sb.append("  statement_uris:\n");
            for (String uri : statementUris) {
                sb.append("    - ").append(uri).append("\n");
            }
        }

        sb.append("\n");
        sb.append("To get line-level coverage, use GetStatementCoverage with:\n");
        sb.append("  - bulk_statements_uri (required): from above\n");
        sb.append("  - statement_uris (required): array of statement URIs from above\n");

        return sb.toString();
    }

    private String formatCoverageMetricLine(String label, CoverageMetric metric) {
        if (metric.total == 0) {
            return String.format("%s  N/A\n", label);
        }
        return String.format(java.util.Locale.ROOT, "%s  %.1f%% (%d/%d)\n",
                label, metric.getPercentage(), metric.executed, metric.total);
    }

    private String formatPercentage(CoverageMetric metric) {
        if (metric == null || metric.total == 0) {
            return "N/A";
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", metric.getPercentage());
    }
}
