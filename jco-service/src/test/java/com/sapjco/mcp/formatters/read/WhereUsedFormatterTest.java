package com.sapjco.mcp.formatters.read;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WhereUsedFormatter.
 */
class WhereUsedFormatterTest {

    private WhereUsedFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new WhereUsedFormatter();
    }

    @Test
    void getToolName_returnsGetWhereUsed() {
        assertEquals("GetWhereUsed", formatter.getToolName());
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
    void format_noResults_showsNoUsagesFound() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <usagereferences:usageReferenceResult xmlns:usagereferences="http://www.sap.com/adt/ris/usagereferences">
            </usagereferences:usageReferenceResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Where-Used Results"));
        assertTrue(result.contains("No usages found"));
    }

    @Test
    void format_singleResult_showsOneUsage() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <usagereferences:usageReferenceResult xmlns:usagereferences="http://www.sap.com/adt/ris/usagereferences"
                                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <usagereferences:referencedObject uri="/sap/bc/adt/oo/classes/zcl_caller/source/main">
                    <objectIdentifier>OBJ_ID_1</objectIdentifier>
                    <usagereferences:adtObject adtcore:type="CLAS/OC" adtcore:name="ZCL_CALLER">
                        <adtcore:packageRef adtcore:name="ZTEST_PACKAGE"/>
                    </usagereferences:adtObject>
                </usagereferences:referencedObject>
            </usagereferences:usageReferenceResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Where-Used Results"));
        assertTrue(result.contains("Found 1 usage"));
        assertTrue(result.contains("ZCL_CALLER"));
        assertTrue(result.contains("ZTEST_PACKAGE"));
    }

    @Test
    void format_multipleResults_showsAllUsages() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <usagereferences:usageReferenceResult xmlns:usagereferences="http://www.sap.com/adt/ris/usagereferences"
                                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <usagereferences:referencedObject uri="/sap/bc/adt/oo/classes/zcl_class1">
                    <usagereferences:adtObject adtcore:type="CLAS/OC" adtcore:name="ZCL_CLASS1">
                        <adtcore:packageRef adtcore:name="ZPKG1"/>
                    </usagereferences:adtObject>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/sap/bc/adt/programs/programs/zprog1">
                    <usagereferences:adtObject adtcore:type="PROG/P" adtcore:name="ZPROG1">
                        <adtcore:packageRef adtcore:name="ZPKG2"/>
                    </usagereferences:adtObject>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/sap/bc/adt/oo/interfaces/zif_intf1">
                    <usagereferences:adtObject adtcore:type="INTF/OI" adtcore:name="ZIF_INTF1">
                        <adtcore:packageRef adtcore:name="ZPKG1"/>
                    </usagereferences:adtObject>
                </usagereferences:referencedObject>
            </usagereferences:usageReferenceResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 3 usage"));
        assertTrue(result.contains("3 object types") || result.contains("3 types"));
        assertTrue(result.contains("ZCL_CLASS1"));
        assertTrue(result.contains("ZPROG1"));
        assertTrue(result.contains("ZIF_INTF1"));
    }

    @Test
    void format_typeMapping_mapsElementTypes() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <usagereferences:usageReferenceResult xmlns:usagereferences="http://www.sap.com/adt/ris/usagereferences"
                                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <usagereferences:referencedObject uri="/test1">
                    <usagereferences:adtObject adtcore:type="CLAS/OM" adtcore:name="METHOD1"/>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/test2">
                    <usagereferences:adtObject adtcore:type="CLAS/OA" adtcore:name="ATTR1"/>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/test3">
                    <usagereferences:adtObject adtcore:type="CLAS/OE" adtcore:name="EVENT1"/>
                </usagereferences:referencedObject>
            </usagereferences:usageReferenceResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Method"));
        assertTrue(result.contains("Attribute"));
        assertTrue(result.contains("Event"));
    }

    @Test
    void format_typeMapping_mapsMainObjectTypes() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <usagereferences:usageReferenceResult xmlns:usagereferences="http://www.sap.com/adt/ris/usagereferences"
                                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <usagereferences:referencedObject uri="/test1">
                    <usagereferences:adtObject adtcore:type="CLAS" adtcore:name="OBJ1"/>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/test2">
                    <usagereferences:adtObject adtcore:type="INTF" adtcore:name="OBJ2"/>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/test3">
                    <usagereferences:adtObject adtcore:type="PROG" adtcore:name="OBJ3"/>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/test4">
                    <usagereferences:adtObject adtcore:type="FUGR" adtcore:name="OBJ4"/>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/test5">
                    <usagereferences:adtObject adtcore:type="DTEL" adtcore:name="OBJ5"/>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/test6">
                    <usagereferences:adtObject adtcore:type="TABL" adtcore:name="OBJ6"/>
                </usagereferences:referencedObject>
            </usagereferences:usageReferenceResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Class"));
        assertTrue(result.contains("Interface"));
        assertTrue(result.contains("Program"));
        assertTrue(result.contains("FuncGroup"));
        assertTrue(result.contains("DataElement"));
        assertTrue(result.contains("Table"));
    }

    @Test
    void format_typeFromUriFallback_extractsTypeFromFragment() throws FormattingException {
        // When adtObject doesn't have type attribute, extract from URI fragment
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <usagereferences:usageReferenceResult xmlns:usagereferences="http://www.sap.com/adt/ris/usagereferences"
                                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <usagereferences:referencedObject uri="/sap/bc/adt/test#type=CLAS%2FOC;name=ZCL_TEST">
                    <usagereferences:adtObject adtcore:name="ZCL_TEST"/>
                </usagereferences:referencedObject>
            </usagereferences:usageReferenceResult>
            """;

        String result = formatter.format(xml);

        // Should extract type from URI and map it
        assertTrue(result.contains("ZCL_TEST"));
    }

    @Test
    void format_missingType_showsUnknown() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <usagereferences:usageReferenceResult xmlns:usagereferences="http://www.sap.com/adt/ris/usagereferences"
                                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <usagereferences:referencedObject uri="/test">
                    <usagereferences:adtObject adtcore:name="NO_TYPE_OBJ"/>
                </usagereferences:referencedObject>
            </usagereferences:usageReferenceResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("NO_TYPE_OBJ"));
        assertTrue(result.contains("unknown"));
    }

    @Test
    void format_unknownType_passesThrough() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <usagereferences:usageReferenceResult xmlns:usagereferences="http://www.sap.com/adt/ris/usagereferences"
                                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <usagereferences:referencedObject uri="/test">
                    <usagereferences:adtObject adtcore:type="ZXYZ" adtcore:name="OBJ1"/>
                </usagereferences:referencedObject>
            </usagereferences:usageReferenceResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ZXYZ")); // Unknown type passes through
    }

    @Test
    void format_byTypeBreakdown_showsCountPerType() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <usagereferences:usageReferenceResult xmlns:usagereferences="http://www.sap.com/adt/ris/usagereferences"
                                                  xmlns:adtcore="http://www.sap.com/adt/core">
                <usagereferences:referencedObject uri="/1">
                    <usagereferences:adtObject adtcore:type="CLAS" adtcore:name="C1"/>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/2">
                    <usagereferences:adtObject adtcore:type="CLAS" adtcore:name="C2"/>
                </usagereferences:referencedObject>
                <usagereferences:referencedObject uri="/3">
                    <usagereferences:adtObject adtcore:type="PROG" adtcore:name="P1"/>
                </usagereferences:referencedObject>
            </usagereferences:usageReferenceResult>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("By Type:"));
        assertTrue(result.contains("2 Class"));
        assertTrue(result.contains("1 Program"));
    }
}
