package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AbapUnitFormatter.
 */
class AbapUnitFormatterTest {

    private AbapUnitFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new AbapUnitFormatter();
    }

    @Test
    void getToolName_returnsRunAbapUnit() {
        assertEquals("RunAbapUnit", formatter.getToolName());
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
    void format_noTestClasses_showsNoTestClassesFound() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit">
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ABAP Unit Results"));
        assertTrue(result.contains("No test classes found"));
    }

    @Test
    void format_singlePassingTest_showsPassedResult() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST" adtcore:uri="/sap/bc/adt/oo/classes/zcl_test">
                    <testClass adtcore:name="LTCL_TEST" adtcore:uri="/sap/bc/adt/oo/classes/zcl_test/includes/testclasses">
                        <testMethod adtcore:name="TEST_METHOD_1" executionTime="0.005" unit="s"/>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ABAP Unit Results"));
        assertTrue(result.contains("1 passed"));
        assertTrue(result.contains("LTCL_TEST"));
        assertTrue(result.contains("TEST_METHOD_1"));
        assertTrue(result.contains("PASS"));
    }

    @Test
    void format_multiplePassingTests_showsAllPassed() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST">
                    <testClass adtcore:name="LTCL_TEST">
                        <testMethod adtcore:name="TEST_1" executionTime="0.001" unit="s"/>
                        <testMethod adtcore:name="TEST_2" executionTime="0.002" unit="s"/>
                        <testMethod adtcore:name="TEST_3" executionTime="0.003" unit="s"/>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("3 passed"));
        assertTrue(result.contains("3 total"));
        assertTrue(result.contains("TEST_1"));
        assertTrue(result.contains("TEST_2"));
        assertTrue(result.contains("TEST_3"));
    }

    @Test
    void format_failingTest_showsFailedResult() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST">
                    <testClass adtcore:name="LTCL_TEST">
                        <testMethod adtcore:name="TEST_FAIL" executionTime="0.010" unit="s">
                            <alerts>
                                <alert kind="failedAssertion" severity="critical">
                                    <title>Assertion failed</title>
                                    <details>Expected 1 but got 2</details>
                                </alert>
                            </alerts>
                        </testMethod>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("1 failed"));
        assertTrue(result.contains("FAIL"));
        assertTrue(result.contains("TEST_FAIL"));
        assertTrue(result.contains("failedAssertion"));
        assertTrue(result.contains("Assertion failed"));
    }

    @Test
    void format_mixedResults_showsSummary() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST">
                    <testClass adtcore:name="LTCL_TEST">
                        <testMethod adtcore:name="TEST_PASS_1" executionTime="0.001" unit="s"/>
                        <testMethod adtcore:name="TEST_PASS_2" executionTime="0.002" unit="s"/>
                        <testMethod adtcore:name="TEST_FAIL" executionTime="0.010" unit="s">
                            <alerts>
                                <alert kind="error" severity="critical">
                                    <title>Test error</title>
                                </alert>
                            </alerts>
                        </testMethod>
                        <testMethod adtcore:name="TEST_WARN" executionTime="0.005" unit="s">
                            <alerts>
                                <alert kind="warning" severity="tolerant">
                                    <title>Test warning</title>
                                </alert>
                            </alerts>
                        </testMethod>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("2 passed"));
        assertTrue(result.contains("1 failed"));
        assertTrue(result.contains("1 with warnings"));
        assertTrue(result.contains("4 total"));
    }

    @Test
    void format_multipleTestClasses_showsAllClasses() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST">
                    <testClass adtcore:name="LTCL_TEST_1">
                        <testMethod adtcore:name="TEST_A" executionTime="0.001" unit="s"/>
                    </testClass>
                    <testClass adtcore:name="LTCL_TEST_2">
                        <testMethod adtcore:name="TEST_B" executionTime="0.002" unit="s"/>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[TestClass] LTCL_TEST_1"));
        assertTrue(result.contains("[TestClass] LTCL_TEST_2"));
        assertTrue(result.contains("TEST_A"));
        assertTrue(result.contains("TEST_B"));
    }

    @Test
    void format_classLevelAlert_showsClassAlert() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST">
                    <testClass adtcore:name="LTCL_TEST">
                        <alerts>
                            <alert kind="setup" severity="critical">
                                <title>Setup failed</title>
                            </alert>
                        </alerts>
                        <testMethod adtcore:name="TEST_1" executionTime="0.001" unit="s"/>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("setup"));
        assertTrue(result.contains("Setup failed"));
    }

    @Test
    void format_executionTimeInMilliseconds_formatsCorrectly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST">
                    <testClass adtcore:name="LTCL_TEST">
                        <testMethod adtcore:name="TEST_1" executionTime="150" unit="ms"/>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ms") || result.contains("150"));
    }

    @Test
    void format_withCoverageUri_showsCoverageSection() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST">
                    <testClass adtcore:name="LTCL_TEST">
                        <testMethod adtcore:name="TEST_1" executionTime="0.001" unit="s"/>
                    </testClass>
                </program>
                <external>
                    <coverage adtcore:uri="/sap/bc/adt/runtime/traces/coverage/measurements/abc123"/>
                </external>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[Coverage]") || result.contains("Coverage Data Available"));
        assertTrue(result.contains("abc123") || result.contains("Measurement URI"));
    }

    @Test
    void format_alertDetails_showsDetails() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST">
                    <testClass adtcore:name="LTCL_TEST">
                        <testMethod adtcore:name="TEST_1" executionTime="0.001" unit="s">
                            <alerts>
                                <alert kind="failedAssertion" severity="critical">
                                    <title>CL_ABAP_UNIT_ASSERT=>ASSERT_EQUALS</title>
                                    <details>Expected: 42, Actual: 0</details>
                                </alert>
                            </alerts>
                        </testMethod>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ASSERT_EQUALS"));
        assertTrue(result.contains("Expected") || result.contains("42"));
    }

    @Test
    void format_multiplePrograms_showsProgramHeaders() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_MY_CLASS">
                    <testClass adtcore:name="LTCL_OWN_TEST">
                        <testMethod adtcore:name="TEST_OWN" executionTime="5" unit="ms"/>
                    </testClass>
                </program>
                <program adtcore:name="ZCL_FOREIGN_TEST">
                    <testClass adtcore:name="LTCL_FOREIGN_TEST">
                        <testMethod adtcore:name="TEST_FOREIGN" executionTime="10" unit="ms"/>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[Program] ZCL_MY_CLASS"));
        assertTrue(result.contains("[Program] ZCL_FOREIGN_TEST"));
        assertTrue(result.contains("[TestClass] LTCL_OWN_TEST"));
        assertTrue(result.contains("[TestClass] LTCL_FOREIGN_TEST"));
        assertTrue(result.contains("TEST_OWN"));
        assertTrue(result.contains("TEST_FOREIGN"));
        assertTrue(result.contains("2 passed"));
        assertTrue(result.contains("2 total"));
    }

    @Test
    void format_singleProgram_noProgramHeader() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_TEST">
                    <testClass adtcore:name="LTCL_TEST">
                        <testMethod adtcore:name="TEST_1" executionTime="5" unit="ms"/>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertFalse(result.contains("[Program]"), "Single-program output should not show [Program] headers");
        assertTrue(result.contains("[TestClass] LTCL_TEST"));
        assertTrue(result.contains("TEST_1"));
    }

    @Test
    void format_foreignTestFailure_showsCorrectly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                             xmlns:adtcore="http://www.sap.com/adt/core">
                <program adtcore:name="ZCL_MY_CLASS">
                    <testClass adtcore:name="LTCL_OWN">
                        <testMethod adtcore:name="TEST_PASS" executionTime="3" unit="ms"/>
                    </testClass>
                </program>
                <program adtcore:name="ZCL_FOREIGN_TESTS">
                    <testClass adtcore:name="LTCL_FOREIGN">
                        <testMethod adtcore:name="TEST_FOREIGN_FAIL" executionTime="15" unit="ms">
                            <alerts>
                                <alert kind="failedAssertion" severity="critical">
                                    <title>Foreign assertion failed</title>
                                    <details>Expected X got Y</details>
                                </alert>
                            </alerts>
                        </testMethod>
                    </testClass>
                </program>
            </aunit:runResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[Program] ZCL_MY_CLASS"));
        assertTrue(result.contains("[Program] ZCL_FOREIGN_TESTS"));
        assertTrue(result.contains("1 passed"));
        assertTrue(result.contains("1 failed"));
        assertTrue(result.contains("FAIL"));
        assertTrue(result.contains("Foreign assertion failed"));
    }
}
