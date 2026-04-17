package com.sapjco.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetadataExtractorService.
 */
class MetadataExtractorServiceTest {

    private MetadataExtractorService service;

    @BeforeEach
    void setUp() {
        service = new MetadataExtractorService();
    }

    // ========================= extract() dispatch tests =========================

    @Nested
    class ExtractDispatchTests {

        @Test
        void extract_nullContent_returnsEmptyMap() {
            Map<String, Object> result = service.extract("CheckSyntax", null);
            assertTrue(result.isEmpty());
        }

        @Test
        void extract_emptyContent_returnsEmptyMap() {
            Map<String, Object> result = service.extract("CheckSyntax", "");
            assertTrue(result.isEmpty());
        }

        @Test
        void extract_blankContent_returnsEmptyMap() {
            Map<String, Object> result = service.extract("CheckSyntax", "   ");
            assertTrue(result.isEmpty());
        }

        @Test
        void extract_unknownTool_returnsEmptyMap() {
            Map<String, Object> result = service.extract("UnknownTool", "<xml>content</xml>");
            assertTrue(result.isEmpty());
        }

        @Test
        void extract_dispatchesToCorrectExtractor() {
            // CheckSyntax
            String syntaxXml = "checkExecuted=\"true\" hasErrors=\"false\"";
            Map<String, Object> result = service.extract("CheckSyntax", syntaxXml);
            assertEquals(true, result.get("checkExecuted"));

            // GetWhereUsed
            String whereUsedXml = "numberOfResults=\"5\"";
            result = service.extract("GetWhereUsed", whereUsedXml);
            assertEquals(5, result.get("numberOfResults"));
        }
    }

    // ========================= Syntax Check Metadata =========================

    @Nested
    class SyntaxCheckMetadataTests {

        @Test
        void extractSyntaxCheckMetadata_checkExecutedTrue() {
            String xml = """
                <chkrun:checkRunResult checkExecuted="true" hasErrors="false" hasWarnings="false">
                </chkrun:checkRunResult>
                """;

            Map<String, Object> result = service.extractSyntaxCheckMetadata(xml);

            assertEquals(true, result.get("checkExecuted"));
            assertEquals(false, result.get("hasErrors"));
            assertEquals(false, result.get("hasWarnings"));
            assertEquals("VALID", result.get("status"));
        }

        @Test
        void extractSyntaxCheckMetadata_activationExecuted() {
            String xml = "activationExecuted=\"true\"";

            Map<String, Object> result = service.extractSyntaxCheckMetadata(xml);

            assertEquals(true, result.get("activationExecuted"));
        }

        @Test
        void extractSyntaxCheckMetadata_hasErrors() {
            String xml = """
                <result hasErrors="true" hasWarnings="true">
                    <chkrun:checkMessage type="E">
                        <chkrun:shortText>Variable X is undefined</chkrun:shortText>
                    </chkrun:checkMessage>
                    <chkrun:checkMessage type="W">
                        <chkrun:shortText>Unused variable Y</chkrun:shortText>
                    </chkrun:checkMessage>
                </result>
                """;

            Map<String, Object> result = service.extractSyntaxCheckMetadata(xml);

            assertEquals(true, result.get("hasErrors"));
            assertEquals(true, result.get("hasWarnings"));
            assertEquals(1, result.get("errors"));
            assertEquals(1, result.get("warnings"));
            assertEquals("INVALID", result.get("status"));

            @SuppressWarnings("unchecked")
            List<String> topErrors = (List<String>) result.get("topErrors");
            assertNotNull(topErrors);
            assertEquals(1, topErrors.size());
            assertTrue(topErrors.get(0).contains("Variable X is undefined"));
        }

        @Test
        void extractSyntaxCheckMetadata_warningsOnly() {
            String xml = """
                <result hasErrors="false" hasWarnings="true">
                    <chkrun:checkMessage type="W">
                        <chkrun:shortText>Warning message</chkrun:shortText>
                    </chkrun:checkMessage>
                </result>
                """;

            Map<String, Object> result = service.extractSyntaxCheckMetadata(xml);

            assertEquals(0, result.get("errors"));
            assertEquals(1, result.get("warnings"));
            assertEquals("VALID (with warnings)", result.get("status"));
        }

        @Test
        void extractSyntaxCheckMetadata_errorWithLineNumber() {
            String xml = """
                <chkrun:checkMessage type="E" line="42">
                    <chkrun:shortText>Syntax error</chkrun:shortText>
                </chkrun:checkMessage>
                """;

            Map<String, Object> result = service.extractSyntaxCheckMetadata(xml);

            @SuppressWarnings("unchecked")
            List<String> topErrors = (List<String>) result.get("topErrors");
            assertNotNull(topErrors);
            assertTrue(topErrors.get(0).contains("line 42"));
        }

        @Test
        void extractSyntaxCheckMetadata_multipleErrors_limitsToFive() {
            StringBuilder xml = new StringBuilder("<result>");
            for (int i = 1; i <= 10; i++) {
                xml.append(String.format("""
                    <chkrun:checkMessage type="E">
                        <chkrun:shortText>Error %d</chkrun:shortText>
                    </chkrun:checkMessage>
                    """, i));
            }
            xml.append("</result>");

            Map<String, Object> result = service.extractSyntaxCheckMetadata(xml.toString());

            assertEquals(10, result.get("errors"));

            @SuppressWarnings("unchecked")
            List<String> topErrors = (List<String>) result.get("topErrors");
            assertNotNull(topErrors);
            assertEquals(5, topErrors.size()); // Limited to 5
        }

        @Test
        void extractSyntaxCheckMetadata_severityFallback() {
            String xml = """
                <chkrun:checkMessage severity="error">
                    <chkrun:shortText>Error via severity</chkrun:shortText>
                </chkrun:checkMessage>
                <chkrun:checkMessage severity="warning">
                    <chkrun:shortText>Warning via severity</chkrun:shortText>
                </chkrun:checkMessage>
                """;

            Map<String, Object> result = service.extractSyntaxCheckMetadata(xml);

            assertEquals(1, result.get("errors"));
            assertEquals(1, result.get("warnings"));
        }

        @Test
        void extractSyntaxCheckMetadata_infoMessages() {
            String xml = """
                <chkrun:checkMessage type="I">
                    <chkrun:shortText>Info message</chkrun:shortText>
                </chkrun:checkMessage>
                """;

            Map<String, Object> result = service.extractSyntaxCheckMetadata(xml);

            assertEquals(0, result.get("errors"));
            assertEquals(0, result.get("warnings"));
            assertEquals(1, result.get("infos"));
            assertEquals("VALID", result.get("status"));
        }
    }

    // ========================= Where-Used Metadata =========================

    @Nested
    class WhereUsedMetadataTests {

        @Test
        void extractWhereUsedMetadata_numberOfResults() {
            String xml = """
                <usagereferences:usageReferenceResult numberOfResults="42">
                </usagereferences:usageReferenceResult>
                """;

            Map<String, Object> result = service.extractWhereUsedMetadata(xml);

            assertEquals(42, result.get("numberOfResults"));
        }

        @Test
        void extractWhereUsedMetadata_countReferences() {
            String xml = """
                <usagereferences:referencedObject adtcore:type="CLAS/OC"/>
                <usagereferences:referencedObject adtcore:type="PROG/P"/>
                <usagereferences:referencedObject adtcore:type="CLAS/OC"/>
                """;

            Map<String, Object> result = service.extractWhereUsedMetadata(xml);

            assertEquals(3, result.get("references"));

            @SuppressWarnings("unchecked")
            Map<String, Integer> types = (Map<String, Integer>) result.get("types");
            assertNotNull(types);
            assertEquals(2, types.get("CLAS/OC"));
            assertEquals(1, types.get("PROG/P"));
        }

        @Test
        void extractWhereUsedMetadata_packages() {
            String xml = """
                <adtcore:packageRef adtcore:name="ZPACKAGE1"/>
                <adtcore:packageRef adtcore:name="ZPACKAGE2"/>
                <adtcore:packageRef adtcore:name="ZPACKAGE1"/>
                """;

            Map<String, Object> result = service.extractWhereUsedMetadata(xml);

            @SuppressWarnings("unchecked")
            Set<String> packages = (Set<String>) result.get("packages");
            assertNotNull(packages);
            assertEquals(2, packages.size());
            assertTrue(packages.contains("ZPACKAGE1"));
            assertTrue(packages.contains("ZPACKAGE2"));
        }

        @Test
        void extractWhereUsedMetadata_objectIdentifiers() {
            String xml = """
                <objectIdentifier>ID1</objectIdentifier>
                <objectIdentifier>ID2</objectIdentifier>
                <objectIdentifier>ID3</objectIdentifier>
                """;

            Map<String, Object> result = service.extractWhereUsedMetadata(xml);

            assertEquals(3, result.get("objectIdentifiers"));
        }
    }

    // ========================= ABAP Unit Metadata =========================

    @Nested
    class AbapUnitMetadataTests {

        @Test
        void extractAbapUnitMetadata_countsPrograms() {
            String xml = """
                <program adtcore:name="ZCL_TEST1"/>
                <program adtcore:name="ZCL_TEST2"/>
                """;

            Map<String, Object> result = service.extractAbapUnitMetadata(xml);

            assertEquals(2, result.get("programs"));
        }

        @Test
        void extractAbapUnitMetadata_countsTestClasses() {
            String xml = """
                <testClass adtcore:name="LTCL_TEST1"/>
                <testClass adtcore:name="LTCL_TEST2"/>
                <testClass adtcore:name="LTCL_TEST3"/>
                """;

            Map<String, Object> result = service.extractAbapUnitMetadata(xml);

            assertEquals(3, result.get("testClasses"));
        }

        @Test
        void extractAbapUnitMetadata_countsTestMethods() {
            String xml = """
                <testMethod executionTime="0.001"/>
                <testMethod executionTime="0.002"/>
                """;

            Map<String, Object> result = service.extractAbapUnitMetadata(xml);

            assertEquals(2, result.get("testMethods"));
        }

        @Test
        void extractAbapUnitMetadata_sumsExecutionTime() {
            String xml = """
                <testMethod executionTime="0.123"/>
                <testMethod executionTime="0.456"/>
                <testMethod executionTime="0.321"/>
                """;

            Map<String, Object> result = service.extractAbapUnitMetadata(xml);

            assertEquals("0.900", result.get("totalExecutionTime"));
        }

        @Test
        void extractAbapUnitMetadata_countsAlerts() {
            String xml = """
                <alert severity="critical"/>
                <alert severity="tolerable"/>
                <alert severity="critical"/>
                """;

            Map<String, Object> result = service.extractAbapUnitMetadata(xml);

            assertEquals(3, result.get("alerts"));
            assertEquals("FAILED", result.get("status"));

            @SuppressWarnings("unchecked")
            Map<String, Integer> severities = (Map<String, Integer>) result.get("severities");
            assertNotNull(severities);
            assertEquals(2, severities.get("critical"));
            assertEquals(1, severities.get("tolerable"));
        }

        @Test
        void extractAbapUnitMetadata_noAlerts_passed() {
            String xml = """
                <testMethod executionTime="0.001"/>
                """;

            Map<String, Object> result = service.extractAbapUnitMetadata(xml);

            assertEquals(0, result.get("alerts"));
            assertEquals("PASSED", result.get("status"));
            assertNull(result.get("severities")); // Not included when no alerts
        }

        @Test
        void extractAbapUnitMetadata_malformedExecutionTime_skipped() {
            String xml = """
                <testMethod executionTime="0.1"/>
                <testMethod executionTime="invalid"/>
                <testMethod executionTime="0.2"/>
                """;

            Map<String, Object> result = service.extractAbapUnitMetadata(xml);

            assertEquals("0.300", result.get("totalExecutionTime"));
        }
    }

    // ========================= ATC Metadata =========================

    @Nested
    class AtcMetadataTests {

        @Test
        void extractAtcMetadata_countsObjects() {
            String xml = """
                <atcobject:object adtcore:name="ZCL_CLASS1"/>
                <atcobject:object adtcore:name="ZCL_CLASS2"/>
                <atcobject:object adtcore:name="ZCL_CLASS1"/>
                """;

            Map<String, Object> result = service.extractAtcMetadata(xml);

            assertEquals(2, result.get("objectsChecked")); // Unique

            @SuppressWarnings("unchecked")
            Set<String> objects = (Set<String>) result.get("objects");
            assertNotNull(objects);
            assertTrue(objects.contains("ZCL_CLASS1"));
            assertTrue(objects.contains("ZCL_CLASS2"));
        }

        @Test
        void extractAtcMetadata_countsFindings() {
            String xml = """
                <atcfinding:finding atcfinding:priority="1"/>
                <atcfinding:finding atcfinding:priority="2"/>
                <atcfinding:finding atcfinding:priority="1"/>
                """;

            Map<String, Object> result = service.extractAtcMetadata(xml);

            assertEquals(3, result.get("findings"));

            @SuppressWarnings("unchecked")
            Map<String, Integer> byPriority = (Map<String, Integer>) result.get("byPriority");
            assertNotNull(byPriority);
            assertEquals(2, byPriority.get("Error"));
            assertEquals(1, byPriority.get("Warning"));
        }

        @Test
        void extractAtcMetadata_priorityLabels() {
            String xml = """
                <atcfinding:finding atcfinding:priority="1"/>
                <atcfinding:finding atcfinding:priority="2"/>
                <atcfinding:finding atcfinding:priority="3"/>
                <atcfinding:finding atcfinding:priority="4"/>
                """;

            Map<String, Object> result = service.extractAtcMetadata(xml);

            @SuppressWarnings("unchecked")
            Map<String, Integer> byPriority = (Map<String, Integer>) result.get("byPriority");
            assertNotNull(byPriority);
            assertEquals(1, byPriority.get("Error"));
            assertEquals(1, byPriority.get("Warning"));
            assertEquals(1, byPriority.get("Info"));
            assertEquals(1, byPriority.get("Priority 4"));
        }

        @Test
        void extractAtcMetadata_topChecks() {
            String xml = """
                <atcfinding:finding atcfinding:checkTitle="Check A"/>
                <atcfinding:finding atcfinding:checkTitle="Check B"/>
                <atcfinding:finding atcfinding:checkTitle="Check A"/>
                <atcfinding:finding atcfinding:checkTitle="Check C"/>
                <atcfinding:finding atcfinding:checkTitle="Check A"/>
                """;

            Map<String, Object> result = service.extractAtcMetadata(xml);

            @SuppressWarnings("unchecked")
            List<String> topChecks = (List<String>) result.get("topChecks");
            assertNotNull(topChecks);
            assertEquals(3, topChecks.size());
            assertEquals("Check A (3)", topChecks.get(0)); // Most frequent first
        }
    }

    // ========================= Package Contents Metadata =========================

    @Nested
    class PackageMetadataTests {

        @Test
        void extractPackageMetadata_countsObjects() {
            String xml = """
                <SEU_ADT_REPOSITORY_OBJ_NODE>
                    <OBJECT_TYPE>CLAS</OBJECT_TYPE>
                    <OBJECT_NAME>ZCL_CLASS1</OBJECT_NAME>
                </SEU_ADT_REPOSITORY_OBJ_NODE>
                <SEU_ADT_REPOSITORY_OBJ_NODE>
                    <OBJECT_TYPE>PROG</OBJECT_TYPE>
                    <OBJECT_NAME>ZPROGRAM1</OBJECT_NAME>
                </SEU_ADT_REPOSITORY_OBJ_NODE>
                """;

            Map<String, Object> result = service.extractPackageMetadata(xml);

            assertEquals(2, result.get("objects"));

            @SuppressWarnings("unchecked")
            Map<String, Integer> byType = (Map<String, Integer>) result.get("byType");
            assertNotNull(byType);
            assertEquals(1, byType.get("CLAS"));
            assertEquals(1, byType.get("PROG"));
        }
    }

    // ========================= Search Metadata =========================

    @Nested
    class SearchMetadataTests {

        @Test
        void extractSearchMetadata_countsResults() {
            String xml = """
                <adtcore:objectReference adtcore:type="CLAS/OC" adtcore:name="ZCL_CLASS1"/>
                <adtcore:objectReference adtcore:type="INTF/OI" adtcore:name="ZIF_INTERFACE1"/>
                <adtcore:objectReference adtcore:type="CLAS/OC" adtcore:name="ZCL_CLASS2"/>
                """;

            Map<String, Object> result = service.extractSearchMetadata(xml);

            assertEquals(3, result.get("results"));

            @SuppressWarnings("unchecked")
            Map<String, Integer> byType = (Map<String, Integer>) result.get("byType");
            assertNotNull(byType);
            assertEquals(2, byType.get("CLAS/OC"));
            assertEquals(1, byType.get("INTF/OI"));
        }
    }

    // ========================= Data Preview Metadata =========================

    @Nested
    class DataMetadataTests {

        @Test
        void extractDataMetadata_totalRows() {
            String xml = """
                <dataPreview:totalRows>1000</dataPreview:totalRows>
                """;

            Map<String, Object> result = service.extractDataMetadata(xml);

            assertEquals(1000, result.get("totalRows"));
        }

        @Test
        void extractDataMetadata_returnedRows() {
            String xml = """
                <dataPreview:data>row1</dataPreview:data>
                <dataPreview:data>row2</dataPreview:data>
                <dataPreview:data>row3</dataPreview:data>
                """;

            Map<String, Object> result = service.extractDataMetadata(xml);

            assertEquals(3, result.get("returnedRows"));
        }

        @Test
        void extractDataMetadata_executionTime() {
            String xml = """
                <dataPreview:queryExecutionTime>123.45</dataPreview:queryExecutionTime>
                """;

            Map<String, Object> result = service.extractDataMetadata(xml);

            assertEquals("123.45 ms", result.get("executionTime"));
        }

        @Test
        void extractDataMetadata_columns() {
            String xml = """
                <dataPreview:metadata dataPreview:name="COL1"/>
                <dataPreview:metadata dataPreview:name="COL2"/>
                <dataPreview:metadata dataPreview:name="COL3"/>
                """;

            Map<String, Object> result = service.extractDataMetadata(xml);

            @SuppressWarnings("unchecked")
            List<String> columns = (List<String>) result.get("columns");
            assertNotNull(columns);
            assertEquals(3, columns.size());
            assertEquals(3, result.get("columnCount"));
        }

        @Test
        void extractDataMetadata_columnsLimitedToTen() {
            StringBuilder xml = new StringBuilder();
            for (int i = 1; i <= 15; i++) {
                xml.append(String.format("<dataPreview:metadata dataPreview:name=\"COL%d\"/>%n", i));
            }

            Map<String, Object> result = service.extractDataMetadata(xml.toString());

            @SuppressWarnings("unchecked")
            List<String> columns = (List<String>) result.get("columns");
            assertNotNull(columns);
            assertEquals(10, columns.size()); // Limited to 10
        }
    }

    // ========================= Transport Metadata =========================

    @Nested
    class TransportMetadataTests {

        @Test
        void extractTransportMetadata_owner() {
            String xml = """
                <tm:task tm:owner="D052860"/>
                """;

            Map<String, Object> result = service.extractTransportMetadata(xml);

            assertEquals("D052860", result.get("owner"));
        }

        @Test
        void extractTransportMetadata_statusLabels() {
            String xml = "tm:status=\"D\"";
            assertEquals("Modifiable", service.extractTransportMetadata(xml).get("status"));

            xml = "tm:status=\"R\"";
            assertEquals("Released", service.extractTransportMetadata(xml).get("status"));

            xml = "tm:status=\"L\"";
            assertEquals("Protected", service.extractTransportMetadata(xml).get("status"));

            xml = "tm:status=\"X\"";
            assertEquals("X", service.extractTransportMetadata(xml).get("status"));
        }

        @Test
        void extractTransportMetadata_countsTasks() {
            String xml = """
                <tm:task/>
                <tm:task/>
                <tm:task/>
                """;

            Map<String, Object> result = service.extractTransportMetadata(xml);

            assertEquals(3, result.get("tasks"));
        }

        @Test
        void extractTransportMetadata_countsObjects() {
            String xml = """
                <tm:abap_object tm:pgmid="R3TR" tm:type="CLAS"/>
                <tm:abap_object tm:pgmid="R3TR" tm:type="PROG"/>
                <tm:abap_object tm:pgmid="LIMU" tm:type="METH"/>
                """;

            Map<String, Object> result = service.extractTransportMetadata(xml);

            assertEquals(3, result.get("objects"));

            @SuppressWarnings("unchecked")
            Map<String, Integer> byPgmidType = (Map<String, Integer>) result.get("byPgmidType");
            assertNotNull(byPgmidType);
            assertEquals(1, byPgmidType.get("R3TR CLAS"));
            assertEquals(1, byPgmidType.get("R3TR PROG"));
            assertEquals(1, byPgmidType.get("LIMU METH"));
        }
    }

    // ========================= Diff Metadata =========================

    @Nested
    class DiffMetadataTests {

        @Test
        void extractDiffMetadata_countsAdditions() {
            String diff = """
                @@ -1,3 +1,5 @@
                 unchanged line
                +added line 1
                +added line 2
                 another unchanged
                """;

            Map<String, Object> result = service.extractDiffMetadata(diff);

            assertEquals(2, result.get("additions"));
        }

        @Test
        void extractDiffMetadata_countsDeletions() {
            String diff = """
                @@ -1,5 +1,3 @@
                 unchanged line
                -deleted line 1
                -deleted line 2
                 another unchanged
                """;

            Map<String, Object> result = service.extractDiffMetadata(diff);

            assertEquals(2, result.get("deletions"));
        }

        @Test
        void extractDiffMetadata_countsHunks() {
            String diff = """
                @@ -1,3 +1,4 @@
                +added
                @@ -10,3 +11,4 @@
                +another added
                @@ -20,3 +22,4 @@
                +third added
                """;

            Map<String, Object> result = service.extractDiffMetadata(diff);

            assertEquals(3, result.get("hunks"));
        }

        @Test
        void extractDiffMetadata_summary() {
            String diff = """
                @@ -1,3 +1,5 @@
                +added 1
                +added 2
                -deleted 1
                @@ -10,3 +12,4 @@
                +added 3
                """;

            Map<String, Object> result = service.extractDiffMetadata(diff);

            assertEquals("+3 / -1 in 2 hunks", result.get("summary"));
        }

        @Test
        void extractDiffMetadata_ignoresDiffHeaders() {
            // Ensure --- and +++ lines (diff headers) are not counted
            String diff = """
                --- a/file.txt
                +++ b/file.txt
                @@ -1,3 +1,4 @@
                +real addition
                """;

            Map<String, Object> result = service.extractDiffMetadata(diff);

            assertEquals(1, result.get("additions"));
            assertEquals(0, result.get("deletions"));
        }
    }

    // ========================= Coverage Metadata =========================

    @Nested
    class CoverageMetadataTests {

        @Test
        void extractCoverageMetadata_statementCoverage() {
            String xml = "statementCoverage=\"75.5\"";

            Map<String, Object> result = service.extractCoverageMetadata(xml);

            assertEquals("75.5%", result.get("statementCoverage"));
        }

        @Test
        void extractCoverageMetadata_branchCoverage() {
            String xml = "branchCoverage=\"60.0\"";

            Map<String, Object> result = service.extractCoverageMetadata(xml);

            assertEquals("60.0%", result.get("branchCoverage"));
        }

        @Test
        void extractCoverageMetadata_procedureCoverage() {
            String xml = "procedureCoverage=\"80.0\"";

            Map<String, Object> result = service.extractCoverageMetadata(xml);

            assertEquals("80.0%", result.get("procedureCoverage"));
        }

        @Test
        void extractCoverageMetadata_allMetrics() {
            String xml = """
                statementCoverage="85.5" branchCoverage="70.0" procedureCoverage="90.0"
                """;

            Map<String, Object> result = service.extractCoverageMetadata(xml);

            assertEquals("85.5%", result.get("statementCoverage"));
            assertEquals("70.0%", result.get("branchCoverage"));
            assertEquals("90.0%", result.get("procedureCoverage"));
        }
    }

    // ========================= Version History Metadata =========================

    @Nested
    class VersionHistoryMetadataTests {

        @Test
        void extractVersionHistoryMetadata_countsVersions() {
            String xml = """
                <atom:entry></atom:entry>
                <atom:entry></atom:entry>
                <atom:entry></atom:entry>
                """;

            Map<String, Object> result = service.extractVersionHistoryMetadata(xml);

            assertEquals(3, result.get("versions"));
        }

        @Test
        void extractVersionHistoryMetadata_latestAuthor() {
            String xml = """
                <atom:entry>
                    <atom:name>D052860</atom:name>
                </atom:entry>
                """;

            Map<String, Object> result = service.extractVersionHistoryMetadata(xml);

            assertEquals("D052860", result.get("latestAuthor"));
        }

        @Test
        void extractVersionHistoryMetadata_latestDate() {
            String xml = """
                <atom:entry>
                    <atom:updated>2024-01-15T10:30:00Z</atom:updated>
                </atom:entry>
                """;

            Map<String, Object> result = service.extractVersionHistoryMetadata(xml);

            assertEquals("2024-01-15T10:30:00Z", result.get("latestDate"));
        }
    }

    // ========================= BOPF Metadata =========================

    @Nested
    class BopfMetadataTests {

        @Test
        void extractBopfMetadata_name() {
            String xml = "adtcore:name=\"/SCMTMS/TOR\"";

            Map<String, Object> result = service.extractBopfMetadata(xml);

            assertEquals("/SCMTMS/TOR", result.get("name"));
        }

        @Test
        void extractBopfMetadata_type() {
            String xml = "adtcore:type=\"BOBF/BO\"";

            Map<String, Object> result = service.extractBopfMetadata(xml);

            assertEquals("BOBF/BO", result.get("type"));
        }

        @Test
        void extractBopfMetadata_programmingModel() {
            String xml = "bo:programmingModel=\"DRAFT_ENABLED\"";

            Map<String, Object> result = service.extractBopfMetadata(xml);

            assertEquals("DRAFT_ENABLED", result.get("programmingModel"));
        }

        @Test
        void extractBopfMetadata_objectCategory() {
            String xml = "bo:objectCategory=\"BUSINESS_OBJECT\"";

            Map<String, Object> result = service.extractBopfMetadata(xml);

            assertEquals("BUSINESS_OBJECT", result.get("objectCategory"));
        }

        @Test
        void extractBopfMetadata_countsComponents() {
            String xml = """
                <bo:nodes bo:name="ROOT"/>
                <bo:nodes bo:name="ITEM"/>
                <bo:actions bo:name="CREATE"/>
                <bo:associations bo:name="TO_ITEM"/>
                <bo:queries bo:name="SELECT_ALL"/>
                <bo:determinations bo:name="DET_1"/>
                <bo:determinations bo:name="DET_2"/>
                <bo:validations bo:name="VAL_1"/>
                """;

            Map<String, Object> result = service.extractBopfMetadata(xml);

            @SuppressWarnings("unchecked")
            Map<String, Integer> components = (Map<String, Integer>) result.get("components");
            assertNotNull(components);
            assertEquals(2, components.get("nodes"));
            assertEquals(1, components.get("actions"));
            assertEquals(1, components.get("associations"));
            assertEquals(1, components.get("queries"));
            assertEquals(2, components.get("determinations"));
            assertEquals(1, components.get("validations"));

            assertEquals(8, result.get("totalComponents"));
        }
    }

    // ========================= Utility Methods =========================

    @Nested
    class UtilityMethodTests {

        @Test
        void countMatches_nullText_returnsZero() {
            assertEquals(0, service.countMatches(null, Pattern.compile("test")));
        }

        @Test
        void countMatches_nullPattern_returnsZero() {
            assertEquals(0, service.countMatches("test content", null));
        }

        @Test
        void countMatches_noMatches_returnsZero() {
            assertEquals(0, service.countMatches("abc def", Pattern.compile("xyz")));
        }

        @Test
        void countMatches_multipleMatches() {
            assertEquals(3, service.countMatches("aaa", Pattern.compile("a")));
        }

        @Test
        void extractUniqueMatches_nullText_returnsEmpty() {
            Set<String> result = service.extractUniqueMatches(null, Pattern.compile("(\\w+)"));
            assertTrue(result.isEmpty());
        }

        @Test
        void extractUniqueMatches_nullPattern_returnsEmpty() {
            Set<String> result = service.extractUniqueMatches("test", null);
            assertTrue(result.isEmpty());
        }

        @Test
        void extractUniqueMatches_extractsUniqueValues() {
            Pattern pattern = Pattern.compile("name=\"(\\w+)\"");
            String text = "name=\"A\" name=\"B\" name=\"A\" name=\"C\"";

            Set<String> result = service.extractUniqueMatches(text, pattern);

            assertEquals(3, result.size());
            assertTrue(result.contains("A"));
            assertTrue(result.contains("B"));
            assertTrue(result.contains("C"));
        }

        @Test
        void groupByPattern_nullText_returnsEmpty() {
            Map<String, Integer> result = service.groupByPattern(null,
                Pattern.compile("<item>"), Pattern.compile("type=\"(\\w+)\""));
            assertTrue(result.isEmpty());
        }

        @Test
        void groupByPattern_nullElementPattern_returnsEmpty() {
            Map<String, Integer> result = service.groupByPattern("content",
                null, Pattern.compile("type=\"(\\w+)\""));
            assertTrue(result.isEmpty());
        }

        @Test
        void groupByPattern_nullAttrPattern_returnsEmpty() {
            Map<String, Integer> result = service.groupByPattern("content",
                Pattern.compile("<item>"), null);
            assertTrue(result.isEmpty());
        }

        @Test
        void groupByPattern_groupsCorrectly() {
            Pattern element = Pattern.compile("<item[^>]+>");
            Pattern attr = Pattern.compile("type=\"(\\w+)\"");
            String text = "<item type=\"A\"/> <item type=\"B\"/> <item type=\"A\"/>";

            Map<String, Integer> result = service.groupByPattern(text, element, attr);

            assertEquals(2, result.get("A"));
            assertEquals(1, result.get("B"));
        }
    }

    // ========================= Format Metadata =========================

    @Nested
    class FormatMetadataTests {

        @Test
        void formatMetadata_nullMap_returnsEmpty() {
            assertEquals("", service.formatMetadata(null));
        }

        @Test
        void formatMetadata_emptyMap_returnsEmpty() {
            assertEquals("", service.formatMetadata(Map.of()));
        }

        @Test
        void formatMetadata_simpleValues() {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("count", 42);
            meta.put("status", "OK");

            String result = service.formatMetadata(meta);

            assertTrue(result.contains("Count: 42"));
            assertTrue(result.contains("Status: OK"));
        }

        @Test
        void formatMetadata_nestedMap() {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            Map<String, Integer> nested = new java.util.LinkedHashMap<>();
            nested.put("errors", 5);
            nested.put("warnings", 3);
            meta.put("byType", nested);

            String result = service.formatMetadata(meta);

            assertTrue(result.contains("By Type:"));
            assertTrue(result.contains("errors: 5"));
            assertTrue(result.contains("warnings: 3"));
        }

        @Test
        void formatMetadata_collection() {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("packages", List.of("PKG1", "PKG2", "PKG3"));

            String result = service.formatMetadata(meta);

            assertTrue(result.contains("Packages: PKG1, PKG2, PKG3"));
        }

        @Test
        void formatMetadata_camelCaseToTitleCase() {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("totalComponents", 10);
            meta.put("testClasses", 5);

            String result = service.formatMetadata(meta);

            assertTrue(result.contains("Total Components: 10"));
            assertTrue(result.contains("Test Classes: 5"));
        }
    }
}
