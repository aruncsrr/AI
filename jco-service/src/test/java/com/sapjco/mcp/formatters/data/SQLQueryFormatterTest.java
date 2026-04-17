package com.sapjco.mcp.formatters.data;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SQLQueryFormatter.
 */
class SQLQueryFormatterTest {

    private SQLQueryFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new SQLQueryFormatter();
    }

    @Test
    void getToolName_returnsSelectSQLQuery() {
        assertEquals("SelectSQLQuery", formatter.getToolName());
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

        assertTrue(result.contains("SQL Query Results"));
        assertTrue(result.contains("No data found"));
    }

    @Test
    void format_singleColumn_showsColumnData() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="CARRID" dataPreview:type="C"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>LH</dataPreview:data>
                        <dataPreview:data>AA</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("SQL Query Results"));
        assertTrue(result.contains("Columns: 1"));
        assertTrue(result.contains("Rows: 2"));
        assertTrue(result.contains("CARRID"));
        assertTrue(result.contains("LH"));
        assertTrue(result.contains("AA"));
    }

    @Test
    void format_withQueryContext_showsQuery() throws FormattingException {
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

        String result = formatter.format(xml, "SELECT * FROM SFLIGHT WHERE CARRID = 'LH'", 100);

        assertTrue(result.contains("Query:"));
        assertTrue(result.contains("SELECT * FROM SFLIGHT"));
    }

    @Test
    void format_multipleColumns_showsAllColumns() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/datapreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="CARRID" dataPreview:type="C"/>
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
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Columns: 2"));
        assertTrue(result.contains("CARRID"));
        assertTrue(result.contains("CONNID"));
    }

    @Test
    void format_longQuery_truncates() throws FormattingException {
        String longQuery = "SELECT " + "FIELD_NAME, ".repeat(50) + " FROM VERY_LONG_TABLE_NAME";
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

        String result = formatter.format(xml, longQuery, 100);

        assertTrue(result.contains("Query:"));
        assertTrue(result.contains("..."));
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
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

        String result = formatter.format(xml, "SELECT *", 50);

        assertTrue(result.contains("Rows: 2"));
        assertTrue(result.contains("max requested: 50"));
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
