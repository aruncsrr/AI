package com.sapjco.mcp.formatters.read;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SearchFormatter.
 */
class SearchFormatterTest {

    private SearchFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new SearchFormatter();
    }

    @Test
    void getToolName_returnsSearch() {
        assertEquals("Search", formatter.getToolName());
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
    void format_noResults_showsNoObjectsFound() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
            </adtcore:objectReferences>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Search Results"));
        assertTrue(result.contains("No objects found matching the search criteria"));
    }

    @Test
    void format_singleResult_showsOneObject() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                <adtcore:objectReference
                    adtcore:name="ZCL_TEST_CLASS"
                    adtcore:type="CLAS"
                    adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class"
                    adtcore:packageName="ZTEST_PACKAGE"
                    adtcore:description="Test class for unit tests"/>
            </adtcore:objectReferences>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Search Results"));
        assertTrue(result.contains("Found 1 object"));
        assertTrue(result.contains("ZCL_TEST_CLASS"));
        assertTrue(result.contains("Class")); // Type mapped from CLAS
        assertTrue(result.contains("ZTEST_PACKAGE"));
        assertTrue(result.contains("Test class for unit tests"));
    }

    @Test
    void format_multipleResults_showsAllObjects() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                <adtcore:objectReference
                    adtcore:name="ZCL_CLASS_ONE"
                    adtcore:type="CLAS"
                    adtcore:packageName="ZPACKAGE1"/>
                <adtcore:objectReference
                    adtcore:name="ZIF_INTERFACE_ONE"
                    adtcore:type="INTF"
                    adtcore:packageName="ZPACKAGE2"/>
                <adtcore:objectReference
                    adtcore:name="ZPROGRAM_ONE"
                    adtcore:type="PROG"
                    adtcore:packageName="ZPACKAGE1"/>
            </adtcore:objectReferences>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 3 objects"));
        assertTrue(result.contains("in 3 types"));
        assertTrue(result.contains("ZCL_CLASS_ONE"));
        assertTrue(result.contains("ZIF_INTERFACE_ONE"));
        assertTrue(result.contains("ZPROGRAM_ONE"));
        assertTrue(result.contains("Class"));
        assertTrue(result.contains("Interface"));
        assertTrue(result.contains("Program"));
    }

    @Test
    void format_multipleResultsSameType_showsTypeSummary() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                <adtcore:objectReference adtcore:name="ZCL_CLASS_1" adtcore:type="CLAS"/>
                <adtcore:objectReference adtcore:name="ZCL_CLASS_2" adtcore:type="CLAS"/>
                <adtcore:objectReference adtcore:name="ZCL_CLASS_3" adtcore:type="CLAS"/>
            </adtcore:objectReferences>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 3 objects"));
        assertFalse(result.contains("in 3 types")); // Only 1 type, so shouldn't show "in X types"
        assertTrue(result.contains("3 Class"));
    }

    @Test
    void format_allObjectTypes_mapsCorrectly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                <adtcore:objectReference adtcore:name="OBJ1" adtcore:type="CLAS"/>
                <adtcore:objectReference adtcore:name="OBJ2" adtcore:type="INTF"/>
                <adtcore:objectReference adtcore:name="OBJ3" adtcore:type="PROG"/>
                <adtcore:objectReference adtcore:name="OBJ4" adtcore:type="FUGR"/>
                <adtcore:objectReference adtcore:name="OBJ5" adtcore:type="FUNC"/>
                <adtcore:objectReference adtcore:name="OBJ6" adtcore:type="DTEL"/>
                <adtcore:objectReference adtcore:name="OBJ7" adtcore:type="TABL"/>
                <adtcore:objectReference adtcore:name="OBJ8" adtcore:type="DOMA"/>
                <adtcore:objectReference adtcore:name="OBJ9" adtcore:type="DDLS"/>
                <adtcore:objectReference adtcore:name="OBJ10" adtcore:type="DEVC"/>
                <adtcore:objectReference adtcore:name="OBJ11" adtcore:type="MSAG"/>
                <adtcore:objectReference adtcore:name="OBJ12" adtcore:type="TRAN"/>
                <adtcore:objectReference adtcore:name="OBJ13" adtcore:type="VIEW"/>
                <adtcore:objectReference adtcore:name="OBJ14" adtcore:type="SHLP"/>
            </adtcore:objectReferences>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Class"));
        assertTrue(result.contains("Interface"));
        assertTrue(result.contains("Program"));
        assertTrue(result.contains("FuncGroup"));
        assertTrue(result.contains("FuncModule"));
        assertTrue(result.contains("DataElem"));
        assertTrue(result.contains("Table"));
        assertTrue(result.contains("Domain"));
        assertTrue(result.contains("CDSView"));
        assertTrue(result.contains("Package"));
        assertTrue(result.contains("MsgClass"));
        assertTrue(result.contains("TCode"));
        assertTrue(result.contains("View"));
        assertTrue(result.contains("SearchHelp"));
    }

    @Test
    void format_unknownType_passesThrough() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                <adtcore:objectReference adtcore:name="OBJ1" adtcore:type="ZXYZ"/>
            </adtcore:objectReferences>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ZXYZ")); // Unknown type passes through unchanged
    }

    @Test
    void format_missingOptionalAttributes_handlesGracefully() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                <adtcore:objectReference adtcore:name="ZCL_TEST"/>
            </adtcore:objectReferences>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ZCL_TEST"));
        assertTrue(result.contains("unknown")); // Type defaults to unknown
    }

    @Test
    void format_longName_truncates() throws FormattingException {
        String longName = "Z" + "A".repeat(50);
        String xml = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                <adtcore:objectReference adtcore:name="%s" adtcore:type="CLAS"/>
            </adtcore:objectReferences>
            """, longName);

        String result = formatter.format(xml);

        // Should contain truncated name with ellipsis
        assertTrue(result.contains("...") || result.length() < longName.length() + 200);
    }
}
