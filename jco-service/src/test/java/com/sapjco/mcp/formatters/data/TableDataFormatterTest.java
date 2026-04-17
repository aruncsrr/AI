package com.sapjco.mcp.formatters.data;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TableDataFormatter.
 */
class TableDataFormatterTest {

    private TableDataFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new TableDataFormatter();
    }

    @Test
    void getToolName_returnsPreviewTableData() {
        assertEquals("PreviewTableData", formatter.getToolName());
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

        assertTrue(result.contains("Table Data"));
        assertTrue(result.contains("No data found"));
    }

    @Test
    void format_singleColumn_showsColumnData() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="CARRID" dataPreview:type="C"
                                          dataPreview:description="Airline Code" dataPreview:keyAttribute="true"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>LH</dataPreview:data>
                        <dataPreview:data>AA</dataPreview:data>
                        <dataPreview:data>UA</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Columns: 1"));
        assertTrue(result.contains("Rows: 3"));
        assertTrue(result.contains("CARRID"));
        assertTrue(result.contains("LH"));
        assertTrue(result.contains("AA"));
        assertTrue(result.contains("UA"));
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
                        <dataPreview:data>AA</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="CONNID" dataPreview:type="N" dataPreview:keyAttribute="true"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>0400</dataPreview:data>
                        <dataPreview:data>0017</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="FLDATE" dataPreview:type="D"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>20240101</dataPreview:data>
                        <dataPreview:data>20240115</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Columns: 3"));
        assertTrue(result.contains("Rows: 2"));
        assertTrue(result.contains("CARRID"));
        assertTrue(result.contains("CONNID"));
        assertTrue(result.contains("FLDATE"));
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
    void format_columnDetails_showsTypeAndDescription() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="PRICE" dataPreview:type="P"
                                          dataPreview:description="Flight Price"/>
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
        assertTrue(result.contains("Flight Price")); // Description
    }

    @Test
    void format_withTableName_showsTableName() throws FormattingException {
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

        String result = formatter.format(xml, "SFLIGHT", 100);

        assertTrue(result.contains("Table Data: SFLIGHT"));
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

        String result = formatter.format(xml, "TEST_TABLE", 50);

        assertTrue(result.contains("Rows: 3"));
        assertTrue(result.contains("max requested: 50"));
    }

    @Test
    void format_longColumnValue_truncates() throws FormattingException {
        String longValue = "A".repeat(100);
        String xml = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="LONG_COL" dataPreview:type="C"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>%s</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """, longValue);

        String result = formatter.format(xml);

        // Should not contain the full 100-char string
        assertFalse(result.contains(longValue));
        assertTrue(result.contains("LONG_COL"));
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
                        <dataPreview:data></dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Rows: 3"));
        assertTrue(result.contains("Value"));
        assertFalse(result.contains("null"));
    }

    @Test
    void format_nonKeyColumn_noKeyMarker() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="NON_KEY" dataPreview:type="C"
                                          dataPreview:keyAttribute="false"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>Value</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("NON_KEY"));
        // Should not have key indicator in column details
        String[] lines = result.split("\n");
        boolean foundColumnLine = false;
        for (String line : lines) {
            if (line.contains("NON_KEY") && line.contains("C")) {
                foundColumnLine = true;
                assertFalse(line.contains("Yes"));
            }
        }
        assertTrue(foundColumnLine);
    }
}
