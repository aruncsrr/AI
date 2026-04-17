package com.sapjco.mcp.formatters.read;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PackageContentsFormatter.
 */
class PackageContentsFormatterTest {

    private PackageContentsFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new PackageContentsFormatter();
    }

    @Test
    void getToolName_returnsGetPackageContents() {
        assertEquals("GetPackageContents", formatter.getToolName());
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
    void format_noObjects_showsNoObjectsFound() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <DATA/>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Package Contents"));
        assertTrue(result.contains("No objects found in package"));
    }

    @Test
    void format_singleObject_showsOneObject() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <DATA>
                        <SEU_ADT_REPOSITORY_OBJ_NODE>
                            <OBJECT_TYPE>CLAS</OBJECT_TYPE>
                            <OBJECT_NAME>ZCL_TEST_CLASS</OBJECT_NAME>
                            <DESCRIPTION>Test class for unit tests</DESCRIPTION>
                            <OBJECT_URI>/sap/bc/adt/oo/classes/zcl_test_class</OBJECT_URI>
                        </SEU_ADT_REPOSITORY_OBJ_NODE>
                    </DATA>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Package Contents"));
        assertTrue(result.contains("Found 1 object"));
        assertTrue(result.contains("ZCL_TEST_CLASS"));
        assertTrue(result.contains("Class")); // Type mapped from CLAS
        assertTrue(result.contains("Test class for unit tests"));
    }

    @Test
    void format_multipleObjects_showsAllObjects() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <DATA>
                        <SEU_ADT_REPOSITORY_OBJ_NODE>
                            <OBJECT_TYPE>CLAS</OBJECT_TYPE>
                            <OBJECT_NAME>ZCL_CLASS1</OBJECT_NAME>
                            <DESCRIPTION>First class</DESCRIPTION>
                        </SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE>
                            <OBJECT_TYPE>INTF</OBJECT_TYPE>
                            <OBJECT_NAME>ZIF_INTERFACE1</OBJECT_NAME>
                            <DESCRIPTION>First interface</DESCRIPTION>
                        </SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE>
                            <OBJECT_TYPE>PROG</OBJECT_TYPE>
                            <OBJECT_NAME>ZPROGRAM1</OBJECT_NAME>
                            <DESCRIPTION>First program</DESCRIPTION>
                        </SEU_ADT_REPOSITORY_OBJ_NODE>
                    </DATA>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 3 objects"));
        assertTrue(result.contains("3 types"));
        assertTrue(result.contains("ZCL_CLASS1"));
        assertTrue(result.contains("ZIF_INTERFACE1"));
        assertTrue(result.contains("ZPROGRAM1"));
        assertTrue(result.contains("Class"));
        assertTrue(result.contains("Interface"));
        assertTrue(result.contains("Program"));
    }

    @Test
    void format_multipleObjectsSameType_showsTypeSummary() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <DATA>
                        <SEU_ADT_REPOSITORY_OBJ_NODE>
                            <OBJECT_TYPE>CLAS</OBJECT_TYPE>
                            <OBJECT_NAME>ZCL_CLASS1</OBJECT_NAME>
                        </SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE>
                            <OBJECT_TYPE>CLAS</OBJECT_TYPE>
                            <OBJECT_NAME>ZCL_CLASS2</OBJECT_NAME>
                        </SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE>
                            <OBJECT_TYPE>CLAS</OBJECT_TYPE>
                            <OBJECT_NAME>ZCL_CLASS3</OBJECT_NAME>
                        </SEU_ADT_REPOSITORY_OBJ_NODE>
                    </DATA>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 3 objects"));
        assertTrue(result.contains("1 type")); // Singular
        assertTrue(result.contains("3 Class"));
    }

    @Test
    void format_allObjectTypes_mapsCorrectly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <DATA>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>CLAS</OBJECT_TYPE><OBJECT_NAME>O1</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>INTF</OBJECT_TYPE><OBJECT_NAME>O2</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>PROG</OBJECT_TYPE><OBJECT_NAME>O3</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>FUGR</OBJECT_TYPE><OBJECT_NAME>O4</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>FUNC</OBJECT_TYPE><OBJECT_NAME>O5</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>DTEL</OBJECT_TYPE><OBJECT_NAME>O6</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>TABL</OBJECT_TYPE><OBJECT_NAME>O7</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>DOMA</OBJECT_TYPE><OBJECT_NAME>O8</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>DDLS</OBJECT_TYPE><OBJECT_NAME>O9</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>DEVC</OBJECT_TYPE><OBJECT_NAME>O10</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>MSAG</OBJECT_TYPE><OBJECT_NAME>O11</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>TRAN</OBJECT_TYPE><OBJECT_NAME>O12</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>VIEW</OBJECT_TYPE><OBJECT_NAME>O13</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>SHLP</OBJECT_TYPE><OBJECT_NAME>O14</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>ENQU</OBJECT_TYPE><OBJECT_NAME>O15</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>XSLT</OBJECT_TYPE><OBJECT_NAME>O16</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                    </DATA>
                </asx:values>
            </asx:abap>
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
        assertTrue(result.contains("LockObj"));
        assertTrue(result.contains("XSLT"));
    }

    @Test
    void format_unknownType_passesThrough() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <DATA>
                        <SEU_ADT_REPOSITORY_OBJ_NODE>
                            <OBJECT_TYPE>ZXYZ</OBJECT_TYPE>
                            <OBJECT_NAME>CUSTOM_OBJ</OBJECT_NAME>
                        </SEU_ADT_REPOSITORY_OBJ_NODE>
                    </DATA>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ZXYZ")); // Unknown type passes through
    }

    @Test
    void format_missingDescription_handlesGracefully() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <DATA>
                        <SEU_ADT_REPOSITORY_OBJ_NODE>
                            <OBJECT_TYPE>CLAS</OBJECT_TYPE>
                            <OBJECT_NAME>ZCL_NO_DESC</OBJECT_NAME>
                        </SEU_ADT_REPOSITORY_OBJ_NODE>
                    </DATA>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ZCL_NO_DESC"));
        // Should not throw or show null
        assertFalse(result.contains("null"));
    }

    @Test
    void format_byTypeSorted_showsMostFrequentFirst() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <DATA>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>PROG</OBJECT_TYPE><OBJECT_NAME>P1</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>CLAS</OBJECT_TYPE><OBJECT_NAME>C1</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>CLAS</OBJECT_TYPE><OBJECT_NAME>C2</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                        <SEU_ADT_REPOSITORY_OBJ_NODE><OBJECT_TYPE>CLAS</OBJECT_TYPE><OBJECT_NAME>C3</OBJECT_NAME></SEU_ADT_REPOSITORY_OBJ_NODE>
                    </DATA>
                </asx:values>
            </asx:abap>
            """;

        String result = formatter.format(xml);

        // By Type should show "3 Class" before "1 Program" (sorted by count descending)
        int classIndex = result.indexOf("3 Class");
        int progIndex = result.indexOf("1 Program");
        assertTrue(classIndex < progIndex, "Most frequent type should appear first");
    }
}
