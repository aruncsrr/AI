package com.sapjco.mcp.formatters.bopf;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BopfBusinessObjectFormatter.
 */
class BopfBusinessObjectFormatterTest {

    private BopfBusinessObjectFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new BopfBusinessObjectFormatter();
    }

    @Test
    void getToolName_returnsGetBopfBusinessObject() {
        assertEquals("GetBopfBusinessObject", formatter.getToolName());
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
    void format_minimalBo_showsHeader() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="/BOBF/DEMO_SALES_ORDER"
                               adtcore:type="BOBF/BO"
                               bo:programmingModel="RAP"
                               bo:objectCategory="businessObject">
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("BOPF Business Object"));
        assertTrue(result.contains("Properties:"));
        assertTrue(result.contains("/BOBF/DEMO_SALES_ORDER"));
    }

    @Test
    void format_withMetadata_showsAllProperties() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="/SCMTMS/TOR"
                               adtcore:type="BOBF/BO"
                               bo:programmingModel="classicBOPF"
                               bo:objectCategory="transientBO">
            </bo:businessObject>
            """;

        String result = formatter.format(xml, "/SCMTMS/TOR", "active");

        assertTrue(result.contains("/SCMTMS/TOR"));
        assertTrue(result.contains("version: active"));
        assertTrue(result.contains("classicBOPF"));
        assertTrue(result.contains("transientBO"));
    }

    @Test
    void format_singleNode_showsNodeSection() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_TEST">
                <bo:nodes bo:name="ROOT" bo:nodeID="NODE001">
                    <bo:persistentStructureRef adtcore:name="ZS_ROOT"/>
                    <bo:persistentTableRef adtcore:name="ZT_ROOT"/>
                </bo:nodes>
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Nodes (1):"));
        assertTrue(result.contains("ROOT"));
        assertTrue(result.contains("ZS_ROOT"));
        assertTrue(result.contains("ZT_ROOT"));
    }

    @Test
    void format_multipleNodes_showsAllNodes() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_TEST">
                <bo:nodes bo:name="ROOT" bo:nodeID="N1">
                    <bo:persistentStructureRef adtcore:name="ZS_ROOT"/>
                </bo:nodes>
                <bo:nodes bo:name="ITEM" bo:nodeID="N2">
                    <bo:persistentStructureRef adtcore:name="ZS_ITEM"/>
                </bo:nodes>
                <bo:nodes bo:name="SUBITEM" bo:nodeID="N3">
                    <bo:persistentStructureRef adtcore:name="ZS_SUBITEM"/>
                </bo:nodes>
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Nodes (3):"));
        assertTrue(result.contains("ROOT"));
        assertTrue(result.contains("ITEM"));
        assertTrue(result.contains("SUBITEM"));
    }

    @Test
    void format_withActions_showsActionsSection() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_TEST">
                <bo:actions bo:name="CREATE" bo:actionID="A1" bo:category="standard">
                    <bo:implementationClassRef adtcore:name="ZCL_BO_CREATE"/>
                </bo:actions>
                <bo:actions bo:name="DELETE" bo:actionID="A2" bo:category="standard">
                    <bo:implementationClassRef adtcore:name="ZCL_BO_DELETE"/>
                </bo:actions>
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Actions (2):"));
        assertTrue(result.contains("CREATE"));
        assertTrue(result.contains("DELETE"));
        assertTrue(result.contains("ZCL_BO_CREATE"));
        assertTrue(result.contains("ZCL_BO_DELETE"));
        assertTrue(result.contains("standard"));
    }

    @Test
    void format_withAssociations_showsAssociationsSection() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_TEST">
                <bo:associations bo:name="TO_ITEMS" bo:associationID="AS1"
                                 bo:implementationType="composition" bo:multiplicity="0..*">
                    <bo:targetNodeRef adtcore:name="ITEM"/>
                </bo:associations>
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Associations (1):"));
        assertTrue(result.contains("TO_ITEMS"));
        assertTrue(result.contains("composition"));
        assertTrue(result.contains("0..*"));
        assertTrue(result.contains("ITEM"));
    }

    @Test
    void format_withQueries_showsQueriesSection() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_TEST">
                <bo:queries bo:name="SELECT_ALL" bo:queryID="Q1" bo:category="native">
                    <bo:implementationClassRef adtcore:name="ZCL_QUERY_SELECT_ALL"/>
                </bo:queries>
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Queries (1):"));
        assertTrue(result.contains("SELECT_ALL"));
        assertTrue(result.contains("native"));
        assertTrue(result.contains("ZCL_QUERY_SELECT_ALL"));
    }

    @Test
    void format_withDeterminations_showsDeterminationsSection() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_TEST">
                <bo:determinations bo:name="DET_SET_DEFAULTS" bo:category="afterModify">
                    <bo:implementationClassRef adtcore:name="ZCL_DET_SET_DEFAULTS"/>
                </bo:determinations>
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Determinations (1):"));
        assertTrue(result.contains("DET_SET_DEFAULTS"));
        assertTrue(result.contains("afterModify"));
        assertTrue(result.contains("ZCL_DET_SET_DEFAULTS"));
    }

    @Test
    void format_withValidations_showsValidationsSection() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_TEST">
                <bo:validations bo:name="VAL_CHECK_STATUS" bo:validationID="V1" bo:category="consistency">
                    <bo:implementationClassRef adtcore:name="ZCL_VAL_CHECK_STATUS"/>
                </bo:validations>
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Validations (1):"));
        assertTrue(result.contains("VAL_CHECK_STATUS"));
        assertTrue(result.contains("consistency"));
        assertTrue(result.contains("ZCL_VAL_CHECK_STATUS"));
    }

    @Test
    void format_fullBo_showsSummary() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_FULL">
                <bo:nodes bo:name="ROOT" bo:nodeID="N1"/>
                <bo:nodes bo:name="ITEM" bo:nodeID="N2"/>
                <bo:actions bo:name="ACT1" bo:actionID="A1"/>
                <bo:associations bo:name="AS1" bo:associationID="AS1"/>
                <bo:queries bo:name="Q1" bo:queryID="Q1"/>
                <bo:determinations bo:name="D1"/>
                <bo:validations bo:name="V1" bo:validationID="V1"/>
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Total Components:"));
        assertTrue(result.contains("Nodes: 2"));
        assertTrue(result.contains("Actions: 1"));
        assertTrue(result.contains("Associations: 1"));
        assertTrue(result.contains("Queries: 1"));
        assertTrue(result.contains("Determinations: 1"));
        assertTrue(result.contains("Validations: 1"));
    }

    @Test
    void format_namespacedBo_handlesCorrectly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="/SCMTMS/TOR"
                               adtcore:type="BOBF/BO">
                <bo:nodes bo:name="ROOT" bo:nodeID="N1">
                    <bo:persistentStructureRef adtcore:name="/SCMTMS/S_TOR_ROOT"/>
                </bo:nodes>
            </bo:businessObject>
            """;

        String result = formatter.format(xml, "/SCMTMS/TOR", "active");

        assertTrue(result.contains("/SCMTMS/TOR"));
        assertTrue(result.contains("/SCMTMS/S_TOR_ROOT"));
    }

    @Test
    void format_longNames_truncates() throws FormattingException {
        String longName = "VERY_LONG_ACTION_NAME_THAT_EXCEEDS_NORMAL_COLUMN_WIDTH_LIMIT";
        String xml = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_TEST">
                <bo:actions bo:name="%s" bo:actionID="A1" bo:category="standard"/>
            </bo:businessObject>
            """, longName);

        String result = formatter.format(xml);

        assertTrue(result.contains("Actions (1):"));
        // Result should not contain the full long name repeated
        assertFalse(result.contains(longName + longName));
    }

    @Test
    void format_emptyComponents_showsZeroCounts() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_EMPTY">
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Total Components: 0"));
        assertTrue(result.contains("Nodes: 0"));
        assertTrue(result.contains("Actions: 0"));
    }

    @Test
    void format_hint_showsRawResponseTip() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bo:businessObject xmlns:bo="http://www.sap.com/adt/bopf/bo"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZBO_TEST">
            </bo:businessObject>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("raw_response=true") || result.contains("output_to_file=true"));
    }
}
