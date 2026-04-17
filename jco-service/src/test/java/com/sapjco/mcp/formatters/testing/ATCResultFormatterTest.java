package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ATCResultFormatter.
 */
class ATCResultFormatterTest {

    private ATCResultFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ATCResultFormatter();
    }

    @Test
    void getToolName_returnsGetATCResult() {
        assertEquals("GetATCResult", formatter.getToolName());
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
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result">
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ATC Result Findings"));
        assertTrue(result.contains("[OK] No findings"));
    }

    @Test
    void format_singleError_showsDetails() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding"
                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkId="CHK1"
                    atcfinding:checkTitle="Missing Exception Handling"
                    atcfinding:messageTitle="Exception not caught"
                    adtcore:uri="/sap/bc/adt/oo/classes/CL_TEST/source/main#start=42,5"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[E] 1 error"));
        assertTrue(result.contains("Missing Exception Handling"));
        assertTrue(result.contains("Exception not caught"));
        assertTrue(result.contains("Line: 42"));
    }

    @Test
    void format_mixedPriorities_showsCorrectCounts() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding"
                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkTitle="Error 1"/>
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Warning 1"/>
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Warning 2"/>
                <atcfinding:finding atcfinding:priority="3" atcfinding:checkTitle="Info 1"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[E] 1 error"));
        assertTrue(result.contains("[W] 2 warnings"));
        assertTrue(result.contains("[I] 1 info"));
        assertTrue(result.contains("4 total"));
    }

    @Test
    void format_priority4_showsP4Icon() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="4" atcfinding:checkTitle="Low Priority"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[P4]"));
        assertTrue(result.contains("Low Priority"));
    }

    @Test
    void format_withDocumentationLinks_showsCount() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding"
                                  xmlns:atom="http://www.w3.org/2005/Atom">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkTitle="Error">
                    <atom:link href="/sap/bc/adt/documentation/atc/documents/123/verdict/CL_TEST/CLAS/CHK1/001/ABC"
                               rel="http://www.sap.com/adt/relations/documentation" type="text/html"/>
                </atcfinding:finding>
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Warning">
                    <atom:link href="/sap/bc/adt/documentation/atc/documents/123/verdict/CL_TEST/CLAS/CHK2/002/DEF"
                               rel="http://www.sap.com/adt/relations/documentation" type="text/html"/>
                </atcfinding:finding>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Documentation links: 2"));
        assertTrue(result.contains("GetATCFindingDocumentation"));
    }

    @Test
    void format_withoutDocumentationLinks_omitsLinkCount() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkTitle="Error"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertFalse(result.contains("Documentation links"));
    }

    @Test
    void format_exemptionDash_treatedAsActive() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkTitle="Test"
                    atcfinding:exemptionApproval="-"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: active"));
        assertFalse(result.contains("Status: exempt"));
    }

    @Test
    void format_exemptionApproved_treatedAsExempt() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkTitle="Exempted"
                    atcfinding:exemptionApproval="APPROVED"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: exempt"));
    }

    @Test
    void format_noExemption_treatedAsActive() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkTitle="Active"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: active"));
    }

    @Test
    void format_noLineInfo_showsDash() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkTitle="Global"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Line: -"));
    }

    @Test
    void format_defaultPriority_showsInfo() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:checkTitle="No Priority"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[I] 1 info"));
    }

    @Test
    void format_locationAttribute_extractsLineNumber() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Empty Catch"
                    atcfinding:messageTitle="Empty catch should be removed!"
                    atcfinding:location="/sap/bc/adt/oo/classes/cl_test/includes/testclasses#start=150,0"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Line: 150"));
        assertTrue(result.contains("Empty Catch"));
    }

    @Test
    void format_uriPreferredOverLocation() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding"
                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkTitle="Test"
                    adtcore:uri="/sap/bc/adt/oo/classes/cl_test/source/main#start=42,5"
                    atcfinding:location="/sap/bc/adt/oo/classes/cl_test/source/main#start=99,0"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Line: 42"));
    }

    @Test
    void format_processorShown() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Test Check"
                    atcfinding:processor="JOHNDOE"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Processor: JOHNDOE"));
    }

    @Test
    void format_noProcessor_omitsProcessor() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Test Check"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertFalse(result.contains("Processor:"));
    }

    @Test
    void format_objectNameGrouping() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="1" atcfinding:checkTitle="Error in Foo"
                    atcfinding:location="/sap/bc/adt/oo/classes/cl_foo/source/main#start=10,0"/>
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Warning in Bar"
                    atcfinding:location="/sap/bc/adt/oo/classes/cl_bar/source/main#start=20,0"/>
                <atcfinding:finding atcfinding:priority="3" atcfinding:checkTitle="Info in Foo"
                    atcfinding:location="/sap/bc/adt/oo/classes/cl_foo/includes/testclasses#start=30,0"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("--- CL_FOO ---"));
        assertTrue(result.contains("--- CL_BAR ---"));
        // CL_FOO findings should be grouped together
        int fooHeader = result.indexOf("--- CL_FOO ---");
        int barHeader = result.indexOf("--- CL_BAR ---");
        int errorInFoo = result.indexOf("Error in Foo");
        int infoInFoo = result.indexOf("Info in Foo");
        assertTrue(errorInFoo > fooHeader);
        assertTrue(infoInFoo > fooHeader);
    }

    @Test
    void format_docLinkPerFinding() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding"
                                  xmlns:atom="http://www.w3.org/2005/Atom">
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Empty Catch">
                    <atom:link href="/sap/bc/adt/documentation/atc/documents/itemid/ABC/index/123"
                               rel="http://www.sap.com/adt/relations/documentation" type="text/html"/>
                </atcfinding:finding>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Doc: /sap/bc/adt/documentation/atc/documents/itemid/ABC/index/123"));
    }

    @Test
    void format_noDocLink_omitsDocLine() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="No Doc"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertFalse(result.contains("Doc:"));
    }

    @Test
    void format_objectNameFromInterfacePath() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Test"
                    atcfinding:location="/sap/bc/adt/oo/interfaces/if_my_intf/source/main#start=5,0"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("--- IF_MY_INTF ---"));
    }

    @Test
    void format_noLocation_showsUnknownGroup() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result"
                                  xmlns:atcfinding="http://www.sap.com/adt/atc/finding">
                <atcfinding:finding atcfinding:priority="2" atcfinding:checkTitle="Global Check"/>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("--- (unknown) ---"));
    }
}
