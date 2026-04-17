package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CoverageResultFormatter.
 */
class CoverageResultFormatterTest {

    private CoverageResultFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new CoverageResultFormatter();
    }

    @Test
    void getToolName_returnsGetCoverageResult() {
        assertEquals("GetCoverageResult", formatter.getToolName());
    }

    @Test
    void format_nullResponse_throwsException() {
        assertThrows(FormattingException.class, () -> formatter.format(null));
    }

    @Test
    void format_emptyResponse_throwsException() {
        assertThrows(FormattingException.class, () -> formatter.format(""));
    }

    @Test
    void format_blankResponse_throwsException() {
        assertThrows(FormattingException.class, () -> formatter.format("   "));
    }

    @Test
    void format_emptyCoverage_showsNAMetrics() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage">
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[Coverage] Coverage Results"));
        assertTrue(result.contains("Summary"));
        assertTrue(result.contains("N/A"));
    }

    @Test
    void format_withSummary_showsPercentages() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage">
                <summary>
                    <coverages>
                        <coverage type="statement" total="100" executed="75"/>
                        <coverage type="branch" total="50" executed="30"/>
                        <coverage type="procedure" total="20" executed="18"/>
                    </coverages>
                </summary>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Statement:"));
        assertTrue(result.contains("75.0%"));
        assertTrue(result.contains("(75/100)"));
        assertTrue(result.contains("Branch:"));
        assertTrue(result.contains("60.0%"));
        assertTrue(result.contains("(30/50)"));
        assertTrue(result.contains("Procedure:"));
        assertTrue(result.contains("90.0%"));
        assertTrue(result.contains("(18/20)"));
    }

    @Test
    void format_nodeWithCoverages_showsPerObjectCoverage() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <node>
                    <objectReference adtcore:name="ZCL_MY_CLASS" adtcore:uri="/sap/bc/adt/oo/classes/zcl_my_class"/>
                    <coverages>
                        <coverage type="statement" total="50" executed="40"/>
                        <coverage type="branch" total="20" executed="10"/>
                        <coverage type="procedure" total="10" executed="8"/>
                    </coverages>
                </node>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Per-Object Coverage"));
        assertTrue(result.contains("ZCL_MY_CLASS"));
        assertTrue(result.contains("80.0%")); // statement
        assertTrue(result.contains("50.0%")); // branch
    }

    @Test
    void format_multipleNodes_showsAllObjects() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <node>
                    <objectReference adtcore:name="ZCL_CLASS_A" adtcore:uri="/sap/bc/adt/oo/classes/zcl_class_a"/>
                    <coverages>
                        <coverage type="statement" total="100" executed="90"/>
                        <coverage type="branch" total="40" executed="32"/>
                        <coverage type="procedure" total="15" executed="15"/>
                    </coverages>
                </node>
                <node>
                    <objectReference adtcore:name="ZCL_CLASS_B" adtcore:uri="/sap/bc/adt/oo/classes/zcl_class_b"/>
                    <coverages>
                        <coverage type="statement" total="50" executed="25"/>
                        <coverage type="branch" total="10" executed="5"/>
                        <coverage type="procedure" total="5" executed="3"/>
                    </coverages>
                </node>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ZCL_CLASS_A"));
        assertTrue(result.contains("ZCL_CLASS_B"));
        assertTrue(result.contains("90.0%")); // Class A statement
        assertTrue(result.contains("50.0%")); // Class B statement
    }

    @Test
    void format_withBulkStatementsUri_showsUri() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage"
                             xmlns:atom="http://www.w3.org/2005/Atom">
                <atom:link rel="http://www.sap.com/adt/relations/runtime/traces/coverage/results/bulkstatements"
                           href="/sap/bc/adt/runtime/traces/coverage/results/bulkstatements?measurementId=abc123"/>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("bulk_statements_uri:"));
        assertTrue(result.contains("bulkstatements?measurementId=abc123"));
    }

    @Test
    void format_nodeWithStatementUri_showsInList() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage"
                             xmlns:adtcore="http://www.sap.com/adt/core"
                             xmlns:atom="http://www.w3.org/2005/Atom">
                <node>
                    <objectReference adtcore:name="ZCL_TEST"/>
                    <atom:link rel="http://www.sap.com/adt/relations/runtime/traces/coverage/results/statements"
                               href="/sap/bc/adt/runtime/traces/coverage/results/statements?uri=%2Fsap%2Fbc%2Fadt"/>
                    <coverages>
                        <coverage type="statement" total="10" executed="5"/>
                    </coverages>
                </node>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("statement_uris:"));
        assertTrue(result.contains("statements?uri="));
    }

    @Test
    void format_zeroCoverage_showsZeroPercent() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <node>
                    <objectReference adtcore:name="ZCL_UNCOVERED"/>
                    <coverages>
                        <coverage type="statement" total="100" executed="0"/>
                        <coverage type="branch" total="50" executed="0"/>
                        <coverage type="procedure" total="10" executed="0"/>
                    </coverages>
                </node>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("0.0%"));
    }

    @Test
    void format_fullCoverage_shows100Percent() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <node>
                    <objectReference adtcore:name="ZCL_FULLY_COVERED"/>
                    <coverages>
                        <coverage type="statement" total="100" executed="100"/>
                        <coverage type="branch" total="50" executed="50"/>
                        <coverage type="procedure" total="10" executed="10"/>
                    </coverages>
                </node>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("100.0%"));
    }

    @Test
    void format_usageInstructions_showsGetStatementCoverage() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage">
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("GetStatementCoverage"));
        assertTrue(result.contains("bulk_statements_uri (required)"));
        assertTrue(result.contains("statement_uris (required)"));
    }

    @Test
    void format_longObjectName_truncates() throws FormattingException {
        String longName = "/SCMTMS/CL_VERY_LONG_CLASS_NAME_THAT_EXCEEDS_NORMAL_COLUMN_WIDTH";
        String xml = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <node>
                    <objectReference adtcore:name="%s"/>
                    <coverages>
                        <coverage type="statement" total="10" executed="5"/>
                    </coverages>
                </node>
            </coverage:result>
            """, longName);

        String result = formatter.format(xml);

        // Should handle long names gracefully
        assertTrue(result.contains("Per-Object Coverage"));
    }

    @Test
    void format_noObjectReference_showsUnknown() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage">
                <node>
                    <coverages>
                        <coverage type="statement" total="10" executed="5"/>
                    </coverages>
                </node>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Unknown"));
    }

    @Test
    void format_partialMetrics_showsAvailableOnes() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <node>
                    <objectReference adtcore:name="ZCL_PARTIAL"/>
                    <coverages>
                        <coverage type="statement" total="100" executed="80"/>
                    </coverages>
                </node>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("80.0%")); // statement
        assertTrue(result.contains("N/A")); // branch/procedure not provided
    }

    @Test
    void format_nonNamespacedLinks_alsoWorks() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage">
                <link rel="http://www.sap.com/adt/relations/runtime/traces/coverage/results/bulkstatements"
                      href="/sap/bc/adt/runtime/traces/coverage/results/bulkstatements?id=test"/>
            </coverage:result>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("bulk_statements_uri:"));
        assertTrue(result.contains("bulkstatements?id=test"));
    }
}
