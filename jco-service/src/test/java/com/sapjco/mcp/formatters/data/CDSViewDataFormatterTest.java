package com.sapjco.mcp.formatters.data;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CDSViewDataFormatter.
 */
class CDSViewDataFormatterTest {

    private CDSViewDataFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new CDSViewDataFormatter();
    }

    @Test
    void getToolName_returnsPreviewCDSView() {
        assertEquals("PreviewCDSView", formatter.getToolName());
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
    void format_noData_showsNoDataMessage() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("CDS View Data"));
        assertTrue(result.contains("No data found"));
    }

    @Test
    void format_singleColumn_showsColumnData() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="AIRLINE" dataPreview:type="C"
                                          dataPreview:description="Airline Code"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>LH</dataPreview:data>
                        <dataPreview:data>AA</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("CDS View Data"));
        assertTrue(result.contains("Columns: 1"));
        assertTrue(result.contains("Rows: 2"));
        assertTrue(result.contains("AIRLINE"));
        assertTrue(result.contains("LH"));
        assertTrue(result.contains("AA"));
    }

    @Test
    void format_withViewName_showsViewName() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="COL1" dataPreview:type="C"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>DATA</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml, "I_FLIGHT", 100, null);

        assertTrue(result.contains("CDS View Data: I_FLIGHT"));
    }

    @Test
    void format_withParameters_showsParameters() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="COL1" dataPreview:type="C"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>DATA</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("P_DATE", "20240101");
        params.put("P_LANGUAGE", "EN");

        String result = formatter.format(xml, "I_FLIGHT_PARAM", 100, params);

        assertTrue(result.contains("Parameters:"));
        assertTrue(result.contains("P_DATE"));
        assertTrue(result.contains("20240101"));
    }

    @Test
    void format_keyColumn_showsKeyMarker() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="KEY_FIELD" dataPreview:type="C"
                                          dataPreview:keyAttribute="true" dataPreview:description="Primary Key"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>VALUE1</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("KEY_FIELD"));
        assertTrue(result.contains("*")); // Key marker
        assertTrue(result.contains("Column Details"));
        assertTrue(result.contains("Yes")); // Key indicator in details
    }

    @Test
    void format_multipleColumns_showsAllColumns() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="CARRID" dataPreview:type="C" dataPreview:keyAttribute="true"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>LH</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="CONNID" dataPreview:type="N"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>0400</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="PRICE" dataPreview:type="P" dataPreview:description="Flight Price"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>500.00</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Columns: 3"));
        assertTrue(result.contains("CARRID"));
        assertTrue(result.contains("CONNID"));
        assertTrue(result.contains("PRICE"));
    }

    @Test
    void format_columnDetails_showsTypeAndDescription() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="PRICE" dataPreview:type="P"
                                          dataPreview:description="Total Flight Price"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>500.00</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Column Details"));
        assertTrue(result.contains("PRICE"));
        assertTrue(result.contains("P")); // Type
        assertTrue(result.contains("Total Flight Price")); // Description
    }

    @Test
    void format_maxRowsInfo_showsInHeader() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="COL1" dataPreview:type="C"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>D1</dataPreview:data>
                        <dataPreview:data>D2</dataPreview:data>
                        <dataPreview:data>D3</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml, "I_VIEW", 50, null);

        assertTrue(result.contains("Rows: 3"));
        assertTrue(result.contains("max requested: 50"));
    }

    @Test
    void format_emptyParameters_noParametersSection() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="COL1" dataPreview:type="C"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>DATA</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml, "I_VIEW", 100, new HashMap<>());

        assertFalse(result.contains("Parameters:"));
    }

    @Test
    void format_emptyValues_handlesGracefully() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="NULLABLE" dataPreview:type="C"/>
                    <dataPreview:dataSet>
                        <dataPreview:data></dataPreview:data>
                        <dataPreview:data>Value</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Rows: 2"));
        assertTrue(result.contains("Value"));
        assertFalse(result.contains("null"));
    }
}
