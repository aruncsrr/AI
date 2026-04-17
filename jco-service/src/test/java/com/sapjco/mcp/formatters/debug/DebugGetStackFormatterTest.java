package com.sapjco.mcp.formatters.debug;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DebugGetStackFormatter.
 */
class DebugGetStackFormatterTest {

    private DebugGetStackFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new DebugGetStackFormatter();
    }

    @Test
    void getToolName_returnsDebugGetStack() {
        assertEquals("DebugGetStack", formatter.getToolName());
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
    void format_preformattedResponse_passesThrough() throws FormattingException {
        String preformatted = "Current Position:\n  Program: ZCL_TEST\n  Line: 42\n\nCall Stack (3 frames)";

        String result = formatter.format(preformatted);

        assertTrue(result.contains("Debug Call Stack"));
        assertTrue(result.contains("Current Position"));
        assertTrue(result.contains("ZCL_TEST"));
    }

    @Test
    void format_singleStackFrame_showsCurrentPosition() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger"
                       xmlns:adtcore="http://www.sap.com/adt/core">
                <stackEntry stackPosition="1" programName="ZCL_TEST" includeName="testclasses"
                           line="42" eventType="METHOD" eventName="TEST_METHOD"
                           adtcore:uri="/sap/bc/adt/oo/classes/zcl_test" isActive="true"/>
            </dbg:stack>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Debug Call Stack"));
        assertTrue(result.contains("Current Position"));
        assertTrue(result.contains("ZCL_TEST"));
        assertTrue(result.contains("TEST_METHOD"));
        assertTrue(result.contains("42"));
    }

    @Test
    void format_multipleStackFrames_showsAllFrames() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger"
                       xmlns:adtcore="http://www.sap.com/adt/core">
                <stackEntry stackPosition="1" programName="ZCL_TEST" includeName="testclasses"
                           line="42" eventType="METHOD" eventName="TEST_METHOD" isActive="true"/>
                <stackEntry stackPosition="2" programName="ZCL_CALLER" includeName="main"
                           line="100" eventType="METHOD" eventName="CALL_TEST" isActive="false"/>
                <stackEntry stackPosition="3" programName="ZPROGRAM" includeName="source"
                           line="50" eventType="FORM" eventName="START_TESTS" isActive="false"/>
            </dbg:stack>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Call Stack (3 frames)"));
        assertTrue(result.contains("TEST_METHOD"));
        assertTrue(result.contains("CALL_TEST"));
        assertTrue(result.contains("START_TESTS"));
    }

    @Test
    void format_noActiveFrame_showsStackOnly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger">
                <stackEntry stackPosition="1" programName="ZCL_TEST" line="42"
                           eventType="METHOD" eventName="TEST_METHOD" isActive="false"/>
            </dbg:stack>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Call Stack"));
        assertTrue(result.contains("TEST_METHOD"));
    }

    @Test
    void format_programNameWithPadding_removesPadding() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger">
                <stackEntry stackPosition="1" programName="ZCL_TEST=====================CP"
                           line="42" eventType="METHOD" eventName="TEST" isActive="true"/>
            </dbg:stack>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ZCL_TEST"));
        assertFalse(result.contains("====="));
    }

    @Test
    void format_systemProgram_included() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger">
                <stackEntry stackPosition="1" programName="CL_ABAP_UNIT" includeName="main"
                           line="100" eventType="METHOD" eventName="RUN_TEST"
                           isActive="true" systemProgram="true"/>
            </dbg:stack>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("CL_ABAP_UNIT"));
        assertTrue(result.contains("RUN_TEST"));
    }

    @Test
    void format_dynpFrameFiltered_excluded() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger">
                <stackEntry stackPosition="1" programName="ZCL_TEST" line="42"
                           eventType="METHOD" eventName="TEST" isActive="true"/>
                <stackEntry stackPosition="2" programName="SAPMSSYS" line="1"
                           eventType="PBO" eventName="SCREEN_0100" stackType="DYNP"/>
            </dbg:stack>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("TEST"));
        assertFalse(result.contains("SCREEN_0100")); // DYNP frames filtered out
    }

    @Test
    void format_longEventName_truncates() throws FormattingException {
        String longName = "VERY_LONG_METHOD_NAME_THAT_EXCEEDS_NORMAL_LENGTH_LIMITS";
        String xml = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger">
                <stackEntry stackPosition="1" programName="ZCL_TEST" line="42"
                           eventType="METHOD" eventName="%s" isActive="true"/>
            </dbg:stack>
            """, longName);

        String result = formatter.format(xml);

        assertTrue(result.contains("...") || result.length() < longName.length() + 500);
    }

    @Test
    void format_withUri_showsUri() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger"
                       xmlns:adtcore="http://www.sap.com/adt/core">
                <stackEntry stackPosition="1" programName="ZCL_TEST" includeName="testclasses"
                           line="42" eventType="METHOD" eventName="TEST"
                           adtcore:uri="/sap/bc/adt/oo/classes/zcl_test/includes/testclasses#start=42"
                           isActive="true"/>
            </dbg:stack>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("/sap/bc/adt/oo/classes/zcl_test"));
    }

    @Test
    void format_emptyStack_showsHeader() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dbg:stack xmlns:dbg="http://www.sap.com/adt/debugger">
            </dbg:stack>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Debug Call Stack"));
        assertTrue(result.contains("Call Stack (0 frames)"));
    }
}
