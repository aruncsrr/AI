package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.AlertSeverity;
import com.sapjco.mcp.util.xml.ElementExtractor;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Formatter for RunAbapUnit tool responses.
 * Converts raw ABAP Unit test results XML into human-readable test report.
 */
@Slf4j
@Component
public class AbapUnitFormatter extends AbstractADTFormatter {

    private static final String ADT_NS = "http://www.sap.com/adt/core";

    // ═══════════════════════════════════════════════════════════════════════════
    // ABAP Unit Extractor Configurations
    // ═══════════════════════════════════════════════════════════════════════════

    private static final ElementExtractor TEST_METHOD_EXTRACTOR = ElementExtractor.builder("testMethod")
            .field(FieldExtractor.builder("name")
                    .fromNamespacedAttribute(ADT_NS, "name")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("uri")
                    .fromNamespacedAttribute(ADT_NS, "uri")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("executionTimeMs")
                    .fromAttribute("executionTime")
                    .asDouble()
                    .withUnitConversion("unit", Map.of("s", 1000.0, "ms", 1.0))
                    .toLong()
                    .defaultValue(0L)
                    .build())
            .field(FieldExtractor.builder("unit")
                    .fromAttribute("unit")
                    .asString()
                    .build())
            .build();

    private static final ElementExtractor TEST_CLASS_EXTRACTOR = ElementExtractor.builder("testClass")
            .field(FieldExtractor.builder("name")
                    .fromNamespacedAttribute(ADT_NS, "name")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("uri")
                    .fromNamespacedAttribute(ADT_NS, "uri")
                    .asString()
                    .build())
            .build();

    private static final ElementExtractor ALERT_EXTRACTOR = ElementExtractor.builder("alert")
            .field(FieldExtractor.builder("kind")
                    .fromAttribute("kind")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("severity")
                    .fromAttribute("severity")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("title")
                    .fromChildText("title")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("details")
                    .fromChildText("details")
                    .asString()
                    .build())
            .build();

    // ═══════════════════════════════════════════════════════════════════════════
    // ABAP Unit Data Classes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parsed test class from ABAP Unit results.
     */
    private static class TestClass {
        final String name;
        final String uri;
        final String programName;
        final List<TestMethod> methods;
        final List<Alert> alerts;

        TestClass(String name, String uri, String programName, List<TestMethod> methods, List<Alert> alerts) {
            this.name = name;
            this.uri = uri;
            this.programName = programName;
            this.methods = methods;
            this.alerts = alerts;
        }
    }

    /**
     * Parsed test method from ABAP Unit results.
     */
    private static class TestMethod {
        final String name;
        final String uri;
        final long executionTimeMs;
        final String unit;
        final List<Alert> alerts;

        TestMethod(String name, String uri, long executionTimeMs, String unit, List<Alert> alerts) {
            this.name = name;
            this.uri = uri;
            this.executionTimeMs = executionTimeMs;
            this.unit = unit;
            this.alerts = alerts;
        }
    }

    /**
     * Parsed alert from ABAP Unit results.
     */
    private static class Alert {
        final String kind;
        final String severity;
        final String title;
        final String details;

        Alert(String kind, String severity, String title, String details) {
            this.kind = kind;
            this.severity = severity;
            this.title = title;
            this.details = details;
        }
    }

    @Override
    public String getToolName() {
        return "RunAbapUnit";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<TestClass> testClasses = parseAbapUnitResults(rawResponse);
        String coverageUri = extractCoverageMeasurementUri(rawResponse);

        return formatAbapUnitResults(testClasses, coverageUri);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ABAP Unit Results Parsing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse ABAP Unit test results XML.
     * Supports both program-wrapped and flat testClass structures.
     */
    private List<TestClass> parseAbapUnitResults(String xml) {
        List<TestClass> testClasses = new ArrayList<>();

        try {
            Document doc = XmlExtractor.parseXml(xml);

            // Try program-level parsing first (standard SAP format)
            NodeList programNodes = doc.getElementsByTagName("program");
            if (programNodes.getLength() > 0) {
                for (int p = 0; p < programNodes.getLength(); p++) {
                    Element programElem = (Element) programNodes.item(p);
                    String programName = programElem.getAttributeNS(ADT_NS, "name");

                    NodeList testClassNodes = programElem.getElementsByTagName("testClass");
                    for (int i = 0; i < testClassNodes.getLength(); i++) {
                        Element classElem = (Element) testClassNodes.item(i);
                        testClasses.add(parseTestClass(classElem, programName));
                    }
                }
            } else {
                // Fallback: flat testClass elements (no program wrapper)
                NodeList testClassNodes = doc.getElementsByTagName("testClass");
                for (int i = 0; i < testClassNodes.getLength(); i++) {
                    Element classElem = (Element) testClassNodes.item(i);
                    testClasses.add(parseTestClass(classElem, null));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse ABAP Unit results", e);
        }

        return testClasses;
    }

    /**
     * Parse a single testClass element into a TestClass object.
     */
    private TestClass parseTestClass(Element classElem, String programName) {
        Map<String, Object> classData = XmlExtractor.extractFields(
                classElem, TEST_CLASS_EXTRACTOR.getFieldExtractors());

        List<TestMethod> methods = new ArrayList<>();
        NodeList methodNodes = classElem.getElementsByTagName("testMethod");

        for (int j = 0; j < methodNodes.getLength(); j++) {
            Element methodElem = (Element) methodNodes.item(j);
            Map<String, Object> methodData = XmlExtractor.extractFields(
                    methodElem, TEST_METHOD_EXTRACTOR.getFieldExtractors());

            List<Alert> methodAlerts = parseAlerts(methodElem);
            methods.add(toTestMethod(methodData, methodAlerts));
        }

        List<Alert> classAlerts = parseAlerts(classElem);
        return toTestClass(classData, programName, methods, classAlerts);
    }

    private TestMethod toTestMethod(Map<String, Object> data, List<Alert> alerts) {
        return new TestMethod(
                (String) data.get("name"),
                (String) data.get("uri"),
                (Long) data.get("executionTimeMs"),
                (String) data.get("unit"),
                alerts
        );
    }

    private TestClass toTestClass(Map<String, Object> data, String programName, List<TestMethod> methods, List<Alert> alerts) {
        return new TestClass(
                (String) data.get("name"),
                (String) data.get("uri"),
                programName,
                methods,
                alerts
        );
    }

    /**
     * Parse alerts from a parent element.
     * Handles both direct children and alerts inside an {@code <alerts>} wrapper.
     */
    private List<Alert> parseAlerts(Element parent) {
        List<Alert> alerts = new ArrayList<>();
        NodeList alertNodes = parent.getElementsByTagName("alert");

        for (int i = 0; i < alertNodes.getLength(); i++) {
            Element alertElem = (Element) alertNodes.item(i);

            // Only process alerts that belong to this parent element
            // Alerts can be either direct children or inside an <alerts> wrapper
            Node alertParent = alertElem.getParentNode();
            boolean isDirectChild = alertParent == parent;
            boolean isInAlertsWrapper = alertParent.getNodeName().equals("alerts")
                    && alertParent.getParentNode() == parent;
            if (!isDirectChild && !isInAlertsWrapper) {
                continue;
            }

            // Use the extractor to get field values
            Map<String, Object> data = XmlExtractor.extractFields(alertElem, ALERT_EXTRACTOR.getFieldExtractors());
            alerts.add(new Alert(
                    (String) data.get("kind"),
                    (String) data.get("severity"),
                    (String) data.get("title"),
                    (String) data.get("details")
            ));
        }

        return alerts;
    }

    /**
     * Extract coverage measurement URI from ABAP Unit response XML.
     * The URI is found in the external/coverage element.
     */
    private String extractCoverageMeasurementUri(String xml) {
        try {
            Document doc = XmlExtractor.parseXml(xml);
            NodeList externalNodes = doc.getElementsByTagName("external");

            for (int i = 0; i < externalNodes.getLength(); i++) {
                Element externalElem = (Element) externalNodes.item(i);
                NodeList coverageNodes = externalElem.getElementsByTagName("coverage");

                for (int j = 0; j < coverageNodes.getLength(); j++) {
                    Element coverageElem = (Element) coverageNodes.item(j);
                    // Get the adtcore:uri attribute
                    String uri = coverageElem.getAttributeNS(ADT_NS, "uri");
                    if (uri != null && !uri.isEmpty()) {
                        return uri;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract coverage URI from ABAP Unit response", e);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ABAP Unit Results Formatting
    // ═══════════════════════════════════════════════════════════════════════════

    private String formatAbapUnitResults(List<TestClass> testClasses, String coverageUri) {
        StringBuilder sb = new StringBuilder();

        sb.append("ABAP Unit Results\n");
        sb.append(boxDivider(80)).append("\n");

        if (testClasses.isEmpty()) {
            sb.append("\nNo test classes found.\n");
            return sb.toString();
        }

        // Count results
        int totalMethods = 0;
        int passedMethods = 0;
        int failedMethods = 0;
        int warningMethods = 0;

        for (TestClass tc : testClasses) {
            for (TestMethod tm : tc.methods) {
                totalMethods++;
                boolean hasFailure = tm.alerts.stream().anyMatch(a -> AlertSeverity.isFailure(a.severity));
                boolean hasWarning = tm.alerts.stream().anyMatch(a -> AlertSeverity.isWarning(a.severity));

                if (hasFailure) {
                    failedMethods++;
                } else if (hasWarning) {
                    warningMethods++;
                } else {
                    passedMethods++;
                }
            }
        }

        // Summary line
        sb.append(String.format("\nSummary: %s %d passed",
                passedMethods > 0 ? "+" : "", passedMethods));
        if (failedMethods > 0) {
            sb.append(String.format(", X %d failed", failedMethods));
        }
        if (warningMethods > 0) {
            sb.append(String.format(", ! %d with warnings", warningMethods));
        }
        sb.append(String.format(" (%d total)\n\n", totalMethods));

        // Determine if we have multiple programs (foreign test scenario)
        long distinctPrograms = testClasses.stream()
                .map(tc -> tc.programName != null ? tc.programName : "")
                .distinct()
                .count();
        boolean showProgramHeaders = distinctPrograms > 1;

        // Test classes and methods, grouped by program when applicable
        String currentProgram = null;
        for (TestClass tc : testClasses) {
            // Show program header when switching programs in multi-program results
            if (showProgramHeaders && tc.programName != null) {
                if (!tc.programName.equals(currentProgram)) {
                    currentProgram = tc.programName;
                    sb.append(String.format("[Program] %s\n", currentProgram));
                }
            }

            sb.append(String.format("[TestClass] %s\n", tc.name));

            // Show class-level alerts
            for (Alert alert : tc.alerts) {
                String icon = AlertSeverity.isFailure(alert.severity) ? "X" : "!";
                sb.append(String.format("  %s %s: %s\n", icon, alert.kind, alert.title));
            }

            // Methods
            sb.append(String.format("  %-40s %-10s %s\n", "Method", "Duration", "Result"));
            sb.append("  ").append(boxDivider(70)).append("\n");

            for (TestMethod tm : tc.methods) {
                boolean hasFailure = tm.alerts.stream().anyMatch(a -> AlertSeverity.isFailure(a.severity));
                boolean hasWarning = tm.alerts.stream().anyMatch(a -> AlertSeverity.isWarning(a.severity));

                String icon;
                String result;
                if (hasFailure) {
                    icon = "X";
                    result = "FAIL";
                } else if (hasWarning) {
                    icon = "!";
                    result = "WARN";
                } else {
                    icon = "+";
                    result = "PASS";
                }

                String duration = formatDuration(tm.executionTimeMs, tm.unit);
                sb.append(String.format("  %s %-38s %-10s %s\n",
                        icon, truncate(tm.name, 38), duration, result));

                // Show method alerts
                for (Alert alert : tm.alerts) {
                    sb.append(String.format("      +-- %s: %s\n", alert.kind, alert.title));
                    if (alert.details != null && !alert.details.isEmpty()) {
                        sb.append(String.format("         %s\n", truncate(alert.details, 60)));
                    }
                }
            }
            sb.append("\n");
        }

        // Add coverage data section if coverage was requested and URI is available
        if (coverageUri != null) {
            sb.append("\n[Coverage] Coverage Data Available\n");
            sb.append(boxDivider(70)).append("\n");
            sb.append("Measurement URI: ").append(coverageUri).append("\n\n");
            sb.append("Use GetCoverageResult tool with this URI to retrieve coverage percentages.\n");
            sb.append("Use GetStatementCoverage tool for line-by-line coverage details.\n");
        }

        return sb.toString();
    }
}
