package com.sapjco.mcp.formatters.debug;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DebugGetVariablesFormatter.
 */
class DebugGetVariablesFormatterTest {

    private DebugGetVariablesFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new DebugGetVariablesFormatter();
    }

    @Test
    void getToolName_returnsDebugGetVariables() {
        assertEquals("DebugGetVariables", formatter.getToolName());
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
    void format_preformattedLocalVariables_passesThrough() throws FormattingException {
        String preformatted = "Local Variables:\n  LV_VALUE = 42\n  LV_TEXT = 'Hello'";

        String result = formatter.format(preformatted);

        assertTrue(result.contains("Debug Variables"));
        assertTrue(result.contains("Local Variables"));
    }

    @Test
    void format_preformattedRootVariables_passesThrough() throws FormattingException {
        String preformatted = "Root Variables:\n  SY-SUBRC = 0\n  SY-DATUM = 20240101";

        String result = formatter.format(preformatted);

        assertTrue(result.contains("Debug Variables"));
        assertTrue(result.contains("Root Variables"));
    }

    @Test
    void format_preformattedVariablesFor_passesThrough() throws FormattingException {
        String preformatted = "Variables for LT_DATA:\n  Field1 = Value1";

        String result = formatter.format(preformatted);

        assertTrue(result.contains("Debug Variables"));
        assertTrue(result.contains("Variables for"));
    }

    @Test
    void format_singleVariable_showsDetails() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <STPDA_ADT_VARIABLE>
                        <ID>VAR_1</ID>
                        <NAME>LV_VALUE</NAME>
                        <DECLARED_TYPE_NAME>I</DECLARED_TYPE_NAME>
                        <ACTUAL_TYPE_NAME>I</ACTUAL_TYPE_NAME>
                        <VALUE>42</VALUE>
                        <HEX_VALUE>0000002A</HEX_VALUE>
                        <META_TYPE>simple</META_TYPE>
                        <LENGTH>4</LENGTH>
                    </STPDA_ADT_VARIABLE>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Debug Variables"));
        assertTrue(result.contains("LV_VALUE"));
        assertTrue(result.contains("42"));
        assertTrue(result.contains("Total: 1 variable"));
    }

    @Test
    void format_multipleVariables_showsAllVariables() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <STPDA_ADT_VARIABLE>
                        <ID>VAR_1</ID>
                        <NAME>LV_INTEGER</NAME>
                        <DECLARED_TYPE_NAME>I</DECLARED_TYPE_NAME>
                        <VALUE>100</VALUE>
                        <META_TYPE>simple</META_TYPE>
                    </STPDA_ADT_VARIABLE>
                    <STPDA_ADT_VARIABLE>
                        <ID>VAR_2</ID>
                        <NAME>LV_STRING</NAME>
                        <DECLARED_TYPE_NAME>STRING</DECLARED_TYPE_NAME>
                        <VALUE>Hello World</VALUE>
                        <META_TYPE>simple</META_TYPE>
                    </STPDA_ADT_VARIABLE>
                    <STPDA_ADT_VARIABLE>
                        <ID>VAR_3</ID>
                        <NAME>LV_DATE</NAME>
                        <DECLARED_TYPE_NAME>D</DECLARED_TYPE_NAME>
                        <VALUE>20240101</VALUE>
                        <META_TYPE>simple</META_TYPE>
                    </STPDA_ADT_VARIABLE>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("LV_INTEGER"));
        assertTrue(result.contains("LV_STRING"));
        assertTrue(result.contains("LV_DATE"));
        assertTrue(result.contains("Total: 3 variables"));
    }

    @Test
    void format_structureVariable_showsExpandableHint() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <STPDA_ADT_VARIABLE>
                        <ID>STRUCT_1</ID>
                        <NAME>LS_DATA</NAME>
                        <DECLARED_TYPE_NAME>TY_DATA</DECLARED_TYPE_NAME>
                        <META_TYPE>structure</META_TYPE>
                    </STPDA_ADT_VARIABLE>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("LS_DATA"));
        assertTrue(result.contains("[structure]"));
        assertTrue(result.contains("Yes")); // Expandable column
        assertTrue(result.contains("expandable"));
    }

    @Test
    void format_tableVariable_showsRowCount() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <STPDA_ADT_VARIABLE>
                        <ID>TAB_1</ID>
                        <NAME>LT_RESULTS</NAME>
                        <DECLARED_TYPE_NAME>TT_RESULTS</DECLARED_TYPE_NAME>
                        <META_TYPE>table</META_TYPE>
                        <TABLE_LINES>25</TABLE_LINES>
                    </STPDA_ADT_VARIABLE>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("LT_RESULTS"));
        assertTrue(result.contains("[table: 25 rows]"));
        assertTrue(result.contains("Yes")); // Expandable column
    }

    @Test
    void format_mixedVariableTypes_showsAll() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <STPDA_ADT_VARIABLE>
                        <ID>1</ID>
                        <NAME>LV_SIMPLE</NAME>
                        <DECLARED_TYPE_NAME>STRING</DECLARED_TYPE_NAME>
                        <VALUE>Test</VALUE>
                        <META_TYPE>simple</META_TYPE>
                    </STPDA_ADT_VARIABLE>
                    <STPDA_ADT_VARIABLE>
                        <ID>2</ID>
                        <NAME>LS_STRUCT</NAME>
                        <DECLARED_TYPE_NAME>TY_STRUCT</DECLARED_TYPE_NAME>
                        <META_TYPE>structure</META_TYPE>
                    </STPDA_ADT_VARIABLE>
                    <STPDA_ADT_VARIABLE>
                        <ID>3</ID>
                        <NAME>LT_TABLE</NAME>
                        <DECLARED_TYPE_NAME>TT_TABLE</DECLARED_TYPE_NAME>
                        <META_TYPE>table</META_TYPE>
                        <TABLE_LINES>10</TABLE_LINES>
                    </STPDA_ADT_VARIABLE>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Test")); // Simple value
        assertTrue(result.contains("[structure]"));
        assertTrue(result.contains("[table: 10 rows]"));
        assertTrue(result.contains("2 expandable"));
    }

    @Test
    void format_hexValue_showsInHexSection() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <STPDA_ADT_VARIABLE>
                        <ID>1</ID>
                        <NAME>LV_BYTE</NAME>
                        <DECLARED_TYPE_NAME>X</DECLARED_TYPE_NAME>
                        <VALUE>42</VALUE>
                        <HEX_VALUE>2A</HEX_VALUE>
                        <META_TYPE>simple</META_TYPE>
                    </STPDA_ADT_VARIABLE>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Hex Values:"));
        assertTrue(result.contains("2A"));
    }

    @Test
    void format_noVariables_showsHelpText() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Debug Variables"));
        assertTrue(result.contains("No variables found"));
        assertTrue(result.contains("@ROOT"));
        assertTrue(result.contains("@LOCALS"));
    }

    @Test
    void format_longVariableName_truncates() throws FormattingException {
        String longName = "LV_VERY_LONG_VARIABLE_NAME_THAT_EXCEEDS_COLUMN_WIDTH";
        String xml = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <STPDA_ADT_VARIABLE>
                        <ID>1</ID>
                        <NAME>%s</NAME>
                        <DECLARED_TYPE_NAME>STRING</DECLARED_TYPE_NAME>
                        <VALUE>Test</VALUE>
                        <META_TYPE>simple</META_TYPE>
                    </STPDA_ADT_VARIABLE>
                </asx:values>
            </asx:abap>
            """, longName);

        String result = formatter.format(xml);

        // Should handle long names gracefully
        assertTrue(result.contains("Total: 1 variable"));
    }

    @Test
    void format_nullValues_handlesGracefully() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <STPDA_ADT_VARIABLE>
                        <ID>1</ID>
                        <NAME>LV_NULL</NAME>
                    </STPDA_ADT_VARIABLE>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("LV_NULL"));
        assertFalse(result.contains("null"));
    }
}
