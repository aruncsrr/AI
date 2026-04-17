package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ATCFormatter.
 */
class ATCFormatterTest {

    private ATCFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ATCFormatter();
    }

    @Test
    void getToolName_returnsRunATC() {
        assertEquals("RunATC", formatter.getToolName());
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
    void format_noFindings_showsClean() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc">
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ATC Results"));
        assertTrue(result.contains("[OK] No findings"));
        assertTrue(result.contains("clean"));
    }

    @Test
    void format_singleError_showsErrorSummary() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc"
                          xmlns:adtcore="http://www.sap.com/adt/core">
                <finding priority="1" checkId="CHK1" checkTitle="Missing Exception Handling"
                         messageTitle="Exception not caught" adtcore:uri="/sap/bc/adt/test#start=42,5"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[E] 1 error"));
        assertTrue(result.contains("Missing Exception Handling"));
        assertTrue(result.contains("Exception not caught"));
        assertTrue(result.contains("Line: 42"));
    }

    @Test
    void format_singleWarning_showsWarningSummary() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc"
                          xmlns:adtcore="http://www.sap.com/adt/core">
                <finding priority="2" checkId="W001" checkTitle="Unused Variable"
                         messageTitle="Variable LV_TEMP is never used" adtcore:uri="#start=10,1"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[W] 1 warning"));
        assertTrue(result.contains("Unused Variable"));
        assertTrue(result.contains("LV_TEMP is never used"));
    }

    @Test
    void format_singleInfo_showsInfoSummary() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc"
                          xmlns:adtcore="http://www.sap.com/adt/core">
                <finding priority="3" checkId="I001" checkTitle="Code Style"
                         messageTitle="Consider using DATA() inline declaration"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[I] 1 info"));
        assertTrue(result.contains("Code Style"));
        assertTrue(result.contains("DATA() inline declaration"));
    }

    @Test
    void format_mixedPriorities_showsCorrectCounts() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc"
                          xmlns:adtcore="http://www.sap.com/adt/core">
                <finding priority="1" checkTitle="Error 1"/>
                <finding priority="1" checkTitle="Error 2"/>
                <finding priority="2" checkTitle="Warning 1"/>
                <finding priority="2" checkTitle="Warning 2"/>
                <finding priority="2" checkTitle="Warning 3"/>
                <finding priority="3" checkTitle="Info 1"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[E] 2 errors"));
        assertTrue(result.contains("[W] 3 warnings"));
        assertTrue(result.contains("[I] 1 info"));
        assertTrue(result.contains("6 total"));
    }

    @Test
    void format_uriWithLineColumn_parsesCorrectly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc"
                          xmlns:adtcore="http://www.sap.com/adt/core">
                <finding priority="1" checkTitle="Test"
                         adtcore:uri="/sap/bc/adt/oo/classes/ZCL_TEST/source/main#start=100,15;end=100,25"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Line: 100"));
    }

    @Test
    void format_noLineInfo_showsDash() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc">
                <finding priority="1" checkTitle="Global Check"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Line: -"));
    }

    @Test
    void format_exemptFinding_showsExemptStatus() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc">
                <finding priority="1" checkTitle="Exempted Check"
                         exemptionApproval="APPROVED"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: exempt"));
    }

    @Test
    void format_activeFinding_showsActiveStatus() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc">
                <finding priority="1" checkTitle="Active Check"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: active"));
    }

    @Test
    void format_findingsNumbered_showsSequentialNumbers() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc">
                <finding priority="1" checkTitle="First"/>
                <finding priority="2" checkTitle="Second"/>
                <finding priority="3" checkTitle="Third"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[1]"));
        assertTrue(result.contains("[2]"));
        assertTrue(result.contains("[3]"));
    }

    @Test
    void format_priorityIcons_displayCorrectly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc">
                <finding priority="1" checkTitle="Error"/>
                <finding priority="2" checkTitle="Warning"/>
                <finding priority="3" checkTitle="Info"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[E]"));
        assertTrue(result.contains("[W]"));
        assertTrue(result.contains("[I]"));
    }

    @Test
    void format_defaultPriority_showsInfo() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc">
                <finding checkTitle="No Priority Set"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        // Default priority is 3 (info)
        assertTrue(result.contains("[I] 1 info"));
    }

    @Test
    void format_singletonPluralization_correctGrammar() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc">
                <finding priority="1" checkTitle="One Error"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("1 error,"));
        assertTrue(result.contains("0 warnings"));
        assertTrue(result.contains("0 infos"));
    }

    @Test
    void format_malformedUri_handlesGracefully() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc"
                          xmlns:adtcore="http://www.sap.com/adt/core">
                <finding priority="1" checkTitle="Test"
                         adtcore:uri="#start=malformed"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Line: -"));
    }

    @Test
    void format_nullCheckTitle_handlesGracefully() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc">
                <finding priority="1" messageTitle="Just a message"/>
            </atc:worklist>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[E]"));
        assertTrue(result.contains("Just a message"));
    }
}
