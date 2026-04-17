package com.sapjco.mcp;

import com.sapjco.mcp.formatters.testing.CoverageResultFormatter;
import com.sapjco.mcp.formatters.testing.StatementCoverageFormatter;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.service.MetadataExtractorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-platform locale regression test.
 *
 * Runs under Locale.GERMANY to catch locale-sensitive String.format calls
 * that would produce different output on Windows with European locales.
 * For example: "75,0%" instead of "75.0%", "2.000 of 3.000" instead of "2,000 of 3,000".
 */
class CrossPlatformLocaleTest {

    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void fileStorageService_formatExcerptSection_usesLocaleRoot() {
        FileStorageService service = new FileStorageService();

        // Create content larger than the default excerpt size (2000 chars)
        String content = "x".repeat(3000);
        String result = service.formatExcerptSection(content, "abap");

        // Under German locale, %,d would produce "2.000 of 3.000" (dots as grouping separator)
        // With Locale.ROOT, it should produce "2,000 of 3,000" (commas)
        assertTrue(result.contains("2,000 of 3,000"),
                "Expected '2,000 of 3,000' but got: " + result);
        assertFalse(result.contains("2.000 of 3.000"),
                "German locale grouping separator leaked into output");
    }

    @Test
    void metadataExtractorService_executionTime_usesLocaleRoot() {
        MetadataExtractorService service = new MetadataExtractorService();

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit">
                  <program uri="/sap/bc/adt/oo/classes/zcl_test" type="CLAS" name="ZCL_TEST"
                           uriType="semantic" adtcore:name="ZCL_TEST"
                           xmlns:adtcore="http://www.sap.com/adt/core">
                    <testClasses>
                      <testClass name="LTC_TEST" uri="/sap/bc/adt/oo/classes/zcl_test"
                                 uriType="semantic" adtcore:name="LTC_TEST"
                                 executionTime="0" xmlns:adtcore="http://www.sap.com/adt/core">
                        <testMethods>
                          <testMethod name="TEST_IT" executionTime="300"
                                      uri="/sap/bc/adt/oo/classes/zcl_test"
                                      uriType="semantic" adtcore:name="TEST_IT"
                                      xmlns:adtcore="http://www.sap.com/adt/core" />
                        </testMethods>
                      </testClass>
                    </testClasses>
                  </program>
                </aunit:runResult>
                """;

        Map<String, Object> metadata = service.extractAbapUnitMetadata(xml);

        // Under German locale, %.3f would produce "300,000" (comma as decimal separator)
        // With Locale.ROOT, it should produce "300.000" (period)
        String executionTime = (String) metadata.get("totalExecutionTime");
        assertEquals("300.000", executionTime,
                "Execution time should use period as decimal separator, not comma");
    }

    @Test
    void coverageResultFormatter_percentage_usesLocaleRoot() throws Exception {
        CoverageResultFormatter formatter = new CoverageResultFormatter();

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <coverage:result xmlns:coverage="http://www.sap.com/adt/coverage">
                  <coverage:node name="ZCL_TEST" type="CLAS"
                                 uri="/sap/bc/adt/oo/classes/zcl_test">
                    <coverage:coverages>
                      <coverage:coverage type="statement" total="4" executed="3"/>
                      <coverage:coverage type="branch" total="2" executed="1"/>
                      <coverage:coverage type="procedure" total="1" executed="1"/>
                    </coverage:coverages>
                  </coverage:node>
                </coverage:result>
                """;

        String result = formatter.format(xml);

        // Under German locale, %.1f%% would produce "75,0%" (comma)
        // With Locale.ROOT, it should produce "75.0%" (period)
        assertTrue(result.contains("75.0%"),
                "Expected '75.0%' but got: " + result);
        assertFalse(result.contains("75,0%"),
                "German locale decimal separator leaked into coverage output");
    }

    @Test
    void statementCoverageFormatter_percentage_usesLocaleRoot() throws Exception {
        StatementCoverageFormatter formatter = new StatementCoverageFormatter();

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <cov:statementsBulkResponse xmlns:cov="http://www.sap.com/adt/coverage"
                                            xmlns:adtcore="http://www.sap.com/adt/core"
                                            xmlns:atom="http://www.w3.org/2005/Atom">
                  <cov:statementsResponse name="implementations">
                    <cov:statement executed="1" line="10" column="1"/>
                    <cov:statement executed="1" line="11" column="1"/>
                    <cov:statement executed="0" line="12" column="1"/>
                  </cov:statementsResponse>
                </cov:statementsBulkResponse>
                """;

        String result = formatter.format(xml);

        // Under German locale, %.1f%% would produce "66,7%" (comma)
        // With Locale.ROOT, it should produce "66.7%" (period)
        assertTrue(result.contains("66.7%"),
                "Expected '66.7%' but got: " + result);
        assertFalse(result.contains("66,7%"),
                "German locale decimal separator leaked into statement coverage output");
    }
}
