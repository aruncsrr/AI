package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ATCResultsFormatter.
 */
class ATCResultsFormatterTest {

    private ATCResultsFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ATCResultsFormatter();
    }

    @Test
    void getToolName_returnsListATCResults() {
        assertEquals("ListATCResults", formatter.getToolName());
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
    void format_noResults_showsNotFound() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result">
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ATC Results Browser"));
        assertTrue(result.contains("No ATC results found"));
    }

    @Test
    void format_singleResult_showsAllFields() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result">
                <atcresult:result>
                    <atcresult:displayId>ABCDEF01234567890ABCDEF012345678</atcresult:displayId>
                    <atcresult:title>ATC Run for ZCL_TEST</atcresult:title>
                    <atcresult:checkVariant>DEFAULT</atcresult:checkVariant>
                    <atcresult:runSeries>DAILY_CHECK</atcresult:runSeries>
                    <atcresult:createdAt>2024-03-24T08:42:45Z</atcresult:createdAt>
                    <atcresult:aggregates>
                        <atcresult:numPrio1>2</atcresult:numPrio1>
                        <atcresult:numPrio2>5</atcresult:numPrio2>
                        <atcresult:numPrio3>10</atcresult:numPrio3>
                        <atcresult:numPrio4>3</atcresult:numPrio4>
                        <atcresult:numFailure>1</atcresult:numFailure>
                    </atcresult:aggregates>
                </atcresult:result>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 1 result"));
        assertTrue(result.contains("[1] ATC Run for ZCL_TEST"));
        assertTrue(result.contains("ID: ABCDEF01234567890ABCDEF012345678"));
        assertTrue(result.contains("Variant: DEFAULT"));
        assertTrue(result.contains("Series: DAILY_CHECK"));
        assertTrue(result.contains("Created: 2024-03-24T08:42:45Z"));
        assertTrue(result.contains("[E] 2"));
        assertTrue(result.contains("[W] 5"));
        assertTrue(result.contains("[I] 10"));
        assertTrue(result.contains("[P4] 3"));
        assertTrue(result.contains("20 total"));
        assertTrue(result.contains("Failures: 1"));
    }

    @Test
    void format_multipleResults_showsAll() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result">
                <atcresult:result>
                    <atcresult:displayId>AAAA</atcresult:displayId>
                    <atcresult:title>First Run</atcresult:title>
                    <atcresult:checkVariant>VAR1</atcresult:checkVariant>
                    <atcresult:aggregates>
                        <atcresult:numPrio1>0</atcresult:numPrio1>
                        <atcresult:numPrio2>0</atcresult:numPrio2>
                        <atcresult:numPrio3>0</atcresult:numPrio3>
                        <atcresult:numPrio4>0</atcresult:numPrio4>
                        <atcresult:numFailure>0</atcresult:numFailure>
                    </atcresult:aggregates>
                </atcresult:result>
                <atcresult:result>
                    <atcresult:displayId>BBBB</atcresult:displayId>
                    <atcresult:title>Second Run</atcresult:title>
                    <atcresult:checkVariant>VAR2</atcresult:checkVariant>
                    <atcresult:aggregates>
                        <atcresult:numPrio1>1</atcresult:numPrio1>
                        <atcresult:numPrio2>2</atcresult:numPrio2>
                        <atcresult:numPrio3>3</atcresult:numPrio3>
                        <atcresult:numPrio4>0</atcresult:numPrio4>
                        <atcresult:numFailure>0</atcresult:numFailure>
                    </atcresult:aggregates>
                </atcresult:result>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 2 results"));
        assertTrue(result.contains("[1] First Run"));
        assertTrue(result.contains("[2] Second Run"));
        assertTrue(result.contains("ID: AAAA"));
        assertTrue(result.contains("ID: BBBB"));
    }

    @Test
    void format_resultWithZeroFindings_showsZeroCounts() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result">
                <atcresult:result>
                    <atcresult:displayId>CLEAN</atcresult:displayId>
                    <atcresult:title>Clean Run</atcresult:title>
                    <atcresult:checkVariant>DEFAULT</atcresult:checkVariant>
                    <atcresult:aggregates>
                        <atcresult:numPrio1>0</atcresult:numPrio1>
                        <atcresult:numPrio2>0</atcresult:numPrio2>
                        <atcresult:numPrio3>0</atcresult:numPrio3>
                        <atcresult:numPrio4>0</atcresult:numPrio4>
                        <atcresult:numFailure>0</atcresult:numFailure>
                    </atcresult:aggregates>
                </atcresult:result>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[E] 0"));
        assertTrue(result.contains("[W] 0"));
        assertTrue(result.contains("0 total"));
        assertFalse(result.contains("Failures:"));
    }

    @Test
    void format_resultWithoutRunSeries_omitsSeries() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result">
                <atcresult:result>
                    <atcresult:displayId>NOSERIES</atcresult:displayId>
                    <atcresult:title>Manual Run</atcresult:title>
                    <atcresult:checkVariant>DEFAULT</atcresult:checkVariant>
                    <atcresult:aggregates>
                        <atcresult:numPrio1>0</atcresult:numPrio1>
                        <atcresult:numPrio2>0</atcresult:numPrio2>
                        <atcresult:numPrio3>0</atcresult:numPrio3>
                        <atcresult:numPrio4>0</atcresult:numPrio4>
                        <atcresult:numFailure>0</atcresult:numFailure>
                    </atcresult:aggregates>
                </atcresult:result>
            </atcresult:resultList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Variant: DEFAULT"));
        assertFalse(result.contains("Series:"));
    }

    @Test
    void format_pluralization_correctGrammar() throws FormattingException {
        String singleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atcresult:resultList xmlns:atcresult="http://www.sap.com/adt/atc/result">
                <atcresult:result>
                    <atcresult:displayId>X</atcresult:displayId>
                    <atcresult:title>One</atcresult:title>
                    <atcresult:aggregates>
                        <atcresult:numPrio1>0</atcresult:numPrio1>
                        <atcresult:numPrio2>0</atcresult:numPrio2>
                        <atcresult:numPrio3>0</atcresult:numPrio3>
                        <atcresult:numPrio4>0</atcresult:numPrio4>
                        <atcresult:numFailure>0</atcresult:numFailure>
                    </atcresult:aggregates>
                </atcresult:result>
            </atcresult:resultList>
            """;

        assertTrue(formatter.format(singleXml).contains("1 result\n"));
    }
}
