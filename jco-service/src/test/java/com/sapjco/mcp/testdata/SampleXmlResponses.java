package com.sapjco.mcp.testdata;

/**
 * Sample XML responses for testing handlers.
 * These are simplified versions of actual SAP ADT API responses.
 */
public final class SampleXmlResponses {

    private SampleXmlResponses() {
        // Utility class - no instantiation
    }

    // ==================== ABAP Unit ====================

    /**
     * ABAP Unit response with all tests passing.
     */
    public static final String ABAP_UNIT_SUCCESS = """
            <?xml version="1.0" encoding="utf-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit">
                <program adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class"
                         adtcore:type="CLAS/OC"
                         adtcore:name="ZCL_TEST_CLASS"
                         xmlns:adtcore="http://www.sap.com/adt/core">
                    <testClasses>
                        <testClass adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses"
                                   adtcore:type="CLAS/OL"
                                   adtcore:name="LTCL_TEST">
                            <testMethods>
                                <testMethod adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses#start=10,1"
                                            adtcore:type="CLAS/OM"
                                            adtcore:name="TEST_SUCCESS"
                                            executionTime="10"
                                            unit="ms">
                                </testMethod>
                                <testMethod adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses#start=20,1"
                                            adtcore:type="CLAS/OM"
                                            adtcore:name="TEST_ANOTHER"
                                            executionTime="5"
                                            unit="ms">
                                </testMethod>
                            </testMethods>
                        </testClass>
                    </testClasses>
                </program>
            </aunit:runResult>
            """;

    /**
     * ABAP Unit response with test failures.
     * Note: alert is a direct child of testMethod and uses severity="fatal"
     * to match the handler's severity detection logic.
     */
    public static final String ABAP_UNIT_FAILURE = """
            <?xml version="1.0" encoding="utf-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit">
                <program adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class"
                         adtcore:type="CLAS/OC"
                         adtcore:name="ZCL_TEST_CLASS"
                         xmlns:adtcore="http://www.sap.com/adt/core">
                    <testClasses>
                        <testClass adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses"
                                   adtcore:type="CLAS/OL"
                                   adtcore:name="LTCL_TEST">
                            <testMethods>
                                <testMethod adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses#start=10,1"
                                            adtcore:type="CLAS/OM"
                                            adtcore:name="TEST_FAILURE"
                                            executionTime="15"
                                            unit="ms">
                                    <alert kind="failedAssertion" severity="fatal">
                                        <title>Assertion failed</title>
                                        <details>
                                            <detail text="Expected: 'A'"/>
                                            <detail text="Actual: 'B'"/>
                                        </details>
                                        <stack>
                                            <stackEntry adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses#start=15,5"
                                                        adtcore:type="CLAS/OM"
                                                        adtcore:name="TEST_FAILURE"
                                                        adtcore:description="ZCL_TEST_CLASS=>LTCL_TEST->TEST_FAILURE"/>
                                        </stack>
                                    </alert>
                                </testMethod>
                            </testMethods>
                        </testClass>
                    </testClasses>
                </program>
            </aunit:runResult>
            """;

    /**
     * ABAP Unit response with critical severity failure.
     * Real SAP systems typically return severity="critical" for failed assertions,
     * not severity="fatal". This test ensures we handle real SAP responses correctly.
     */
    public static final String ABAP_UNIT_CRITICAL_FAILURE = """
            <?xml version="1.0" encoding="utf-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit">
                <program adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class"
                         adtcore:type="CLAS/OC"
                         adtcore:name="ZCL_TEST_CLASS"
                         xmlns:adtcore="http://www.sap.com/adt/core">
                    <testClasses>
                        <testClass adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses"
                                   adtcore:type="CLAS/OL"
                                   adtcore:name="LTCL_TEST">
                            <testMethods>
                                <testMethod adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses#start=10,1"
                                            adtcore:type="CLAS/OM"
                                            adtcore:name="TEST_PASSING"
                                            executionTime="5"
                                            unit="ms">
                                </testMethod>
                                <testMethod adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses#start=20,1"
                                            adtcore:type="CLAS/OM"
                                            adtcore:name="TEST_FAILING"
                                            executionTime="15"
                                            unit="ms">
                                    <alert kind="failedAssertion" severity="critical">
                                        <title>Critical Assertion Error: 'Intentional failure: 1 does not equal 2'</title>
                                        <details>
                                            <detail text="Expected: 1"/>
                                            <detail text="Actual: 2"/>
                                        </details>
                                        <stack>
                                            <stackEntry adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses#start=25,5"
                                                        adtcore:type="CLAS/OM"
                                                        adtcore:name="TEST_FAILING"
                                                        adtcore:description="ZCL_TEST_CLASS=>LTCL_TEST->TEST_FAILING"/>
                                        </stack>
                                    </alert>
                                </testMethod>
                            </testMethods>
                        </testClass>
                    </testClasses>
                </program>
            </aunit:runResult>
            """;

    /**
     * ABAP Unit response with no tests found.
     */
    public static final String ABAP_UNIT_EMPTY = """
            <?xml version="1.0" encoding="utf-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit">
            </aunit:runResult>
            """;

    /**
     * ABAP Unit response with coverage URI.
     */
    public static final String ABAP_UNIT_WITH_COVERAGE = """
            <?xml version="1.0" encoding="utf-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit"
                            xmlns:adtcore="http://www.sap.com/adt/core">
                <external>
                    <coverage adtcore:uri="/sap/bc/adt/runtime/traces/coverage/measurements/00505681-1234-5678-ABCD-0000DEADBEEF"/>
                </external>
                <program adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class"
                         adtcore:type="CLAS/OC"
                         adtcore:name="ZCL_TEST_CLASS">
                    <testClasses>
                        <testClass adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses"
                                   adtcore:type="CLAS/OL"
                                   adtcore:name="LTCL_TEST">
                            <testMethods>
                                <testMethod adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/testclasses#start=10,1"
                                            adtcore:type="CLAS/OM"
                                            adtcore:name="TEST_SUCCESS"
                                            executionTime="10"
                                            unit="ms">
                                </testMethod>
                            </testMethods>
                        </testClass>
                    </testClasses>
                </program>
            </aunit:runResult>
            """;

    /**
     * ABAP Unit response with foreign (assigned) tests.
     * Contains two &lt;program&gt; elements: the own class and an external test class.
     * This simulates the response when test_scope includes foreign tests.
     */
    public static final String ABAP_UNIT_WITH_FOREIGN_TESTS = """
            <?xml version="1.0" encoding="utf-8"?>
            <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit">
                <program adtcore:uri="/sap/bc/adt/oo/classes/zcl_my_class"
                         adtcore:type="CLAS/OC"
                         adtcore:name="ZCL_MY_CLASS"
                         xmlns:adtcore="http://www.sap.com/adt/core">
                    <testClasses>
                        <testClass adtcore:uri="/sap/bc/adt/oo/classes/zcl_my_class/includes/testclasses"
                                   adtcore:type="CLAS/OL"
                                   adtcore:name="LTCL_OWN_TEST">
                            <testMethods>
                                <testMethod adtcore:uri="/sap/bc/adt/oo/classes/zcl_my_class/includes/testclasses#start=10,1"
                                            adtcore:type="CLAS/OM"
                                            adtcore:name="TEST_OWN_METHOD"
                                            executionTime="8"
                                            unit="ms">
                                </testMethod>
                            </testMethods>
                        </testClass>
                    </testClasses>
                </program>
                <program adtcore:uri="/sap/bc/adt/oo/classes/zcl_foreign_test"
                         adtcore:type="CLAS/OC"
                         adtcore:name="ZCL_FOREIGN_TEST"
                         xmlns:adtcore="http://www.sap.com/adt/core">
                    <testClasses>
                        <testClass adtcore:uri="/sap/bc/adt/oo/classes/zcl_foreign_test/includes/testclasses"
                                   adtcore:type="CLAS/OL"
                                   adtcore:name="LTCL_FOREIGN_TEST">
                            <testMethods>
                                <testMethod adtcore:uri="/sap/bc/adt/oo/classes/zcl_foreign_test/includes/testclasses#start=15,1"
                                            adtcore:type="CLAS/OM"
                                            adtcore:name="TEST_FOREIGN_METHOD"
                                            executionTime="12"
                                            unit="ms">
                                </testMethod>
                                <testMethod adtcore:uri="/sap/bc/adt/oo/classes/zcl_foreign_test/includes/testclasses#start=25,1"
                                            adtcore:type="CLAS/OM"
                                            adtcore:name="TEST_FOREIGN_FAIL"
                                            executionTime="20"
                                            unit="ms">
                                    <alert kind="failedAssertion" severity="critical">
                                        <title>Foreign test assertion failed</title>
                                        <details>
                                            <detail text="Expected: 'X'"/>
                                            <detail text="Actual: 'Y'"/>
                                        </details>
                                    </alert>
                                </testMethod>
                            </testMethods>
                        </testClass>
                    </testClasses>
                </program>
            </aunit:runResult>
            """;

    // ==================== Coverage Results ====================

    /**
     * Coverage result with summary, per-object breakdown, and atom:links for statement URIs.
     * Includes bulk_statements_uri and per-node statement_uris as per ADT pattern.
     */
    public static final String COVERAGE_RESULT = """
            <?xml version="1.0" encoding="utf-8"?>
            <cov:result xmlns:cov="http://www.sap.com/adt/cov"
                       xmlns:adtcore="http://www.sap.com/adt/core"
                       xmlns:atom="http://www.w3.org/2005/Atom"
                       name="Coverage Result">
                <atom:link href="/sap/bc/adt/runtime/traces/coverage/results/bulkstatements?measurementId=00505681-1234-5678-ABCD-0000DEADBEEF"
                          rel="http://www.sap.com/adt/relations/runtime/traces/coverage/results/bulkstatements"/>
                <summary>
                    <coverage type="statement" total="100" executed="75"/>
                    <coverage type="branch" total="20" executed="15"/>
                    <coverage type="procedure" total="10" executed="8"/>
                </summary>
                <node>
                    <objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class"
                                    adtcore:name="ZCL_TEST_CLASS"/>
                    <atom:link href="/sap/bc/adt/runtime/traces/coverage/results/statements?object=%2Fsap%2Fbc%2Fadt%2Foo%2Fclasses%2Fzcl_test_class&amp;measurementId=00505681-1234-5678-ABCD-0000DEADBEEF"
                              rel="http://www.sap.com/adt/relations/runtime/traces/coverage/results/statements"/>
                    <coverage type="statement" total="50" executed="40"/>
                    <coverage type="branch" total="10" executed="7"/>
                    <coverage type="procedure" total="5" executed="4"/>
                </node>
                <node>
                    <objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_helper"
                                    adtcore:name="ZCL_TEST_HELPER"/>
                    <atom:link href="/sap/bc/adt/runtime/traces/coverage/results/statements?object=%2Fsap%2Fbc%2Fadt%2Foo%2Fclasses%2Fzcl_test_helper&amp;measurementId=00505681-1234-5678-ABCD-0000DEADBEEF"
                              rel="http://www.sap.com/adt/relations/runtime/traces/coverage/results/statements"/>
                    <coverage type="statement" total="50" executed="35"/>
                    <coverage type="branch" total="10" executed="8"/>
                    <coverage type="procedure" total="5" executed="4"/>
                </node>
            </cov:result>
            """;

    /**
     * Empty coverage result.
     */
    public static final String COVERAGE_RESULT_EMPTY = """
            <?xml version="1.0" encoding="utf-8"?>
            <cov:result xmlns:cov="http://www.sap.com/adt/cov"
                       xmlns:adtcore="http://www.sap.com/adt/core"
                       name="Coverage Result">
            </cov:result>
            """;

    /**
     * Real SAP coverage result format with <coverages> wrapper inside each <node>.
     * This format has no top-level <summary> element - metrics are inside <node><coverages>.
     */
    public static final String COVERAGE_RESULT_SAP_FORMAT = """
            <?xml version="1.0" encoding="utf-8"?>
            <cov:result xmlns:cov="http://www.sap.com/adt/cov"
                       xmlns:adtcore="http://www.sap.com/adt/core"
                       xmlns:atom="http://www.w3.org/2005/Atom"
                       name="Coverage Result">
                <atom:link href="/sap/bc/adt/runtime/traces/coverage/results/bulkstatements?measurementId=00505681-REAL-SAP-ABCD-FORMAT123456"
                          rel="http://www.sap.com/adt/relations/runtime/traces/coverage/results/bulkstatements"/>
                <node>
                    <objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_real_class"
                                    adtcore:name="ZCL_REAL_CLASS"/>
                    <atom:link href="/sap/bc/adt/runtime/traces/coverage/results/statements?object=%2Fsap%2Fbc%2Fadt%2Foo%2Fclasses%2Fzcl_real_class&amp;measurementId=00505681-REAL-SAP-ABCD-FORMAT123456"
                              rel="http://www.sap.com/adt/relations/runtime/traces/coverage/results/statements"/>
                    <coverages>
                        <coverage type="statement" total="100" executed="88"/>
                        <coverage type="branch" total="20" executed="16"/>
                        <coverage type="procedure" total="10" executed="9"/>
                    </coverages>
                </node>
            </cov:result>
            """;

    /**
     * Statement-level coverage data in statementsBulkResponse format.
     * Returns coverage for processing blocks with per-statement execution counts.
     */
    public static final String STATEMENT_COVERAGE = """
            <?xml version="1.0" encoding="utf-8"?>
            <cov:statementsBulkResponse xmlns:cov="http://www.sap.com/adt/cov"
                                       xmlns:adtcore="http://www.sap.com/adt/core"
                                       xmlns:atom="http://www.w3.org/2005/Atom">
                <statementsResponse name="METHOD_ONE">
                    <atom:link href="/sap/bc/adt/oo/classes/zcl_test_class/includes/implementations"
                              rel="http://www.sap.com/adt/relations/source"/>
                    <statement executed="3">
                        <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/implementations#start=15,5;end=15,20"/>
                    </statement>
                    <statement executed="3">
                        <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/implementations#start=16,5;end=16,25"/>
                    </statement>
                    <statement executed="0">
                        <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/implementations#start=17,5;end=17,18"/>
                    </statement>
                    <statement executed="0">
                        <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/implementations#start=18,5;end=18,22"/>
                    </statement>
                    <statement executed="3">
                        <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/implementations#start=20,5;end=20,15"/>
                    </statement>
                </statementsResponse>
                <statementsResponse name="METHOD_TWO">
                    <atom:link href="/sap/bc/adt/oo/classes/zcl_test_class/includes/implementations"
                              rel="http://www.sap.com/adt/relations/source"/>
                    <statement executed="0">
                        <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/implementations#start=30,5;end=30,30"/>
                    </statement>
                    <statement executed="0">
                        <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/includes/implementations#start=31,5;end=31,25"/>
                    </statement>
                </statementsResponse>
            </cov:statementsBulkResponse>
            """;

    /**
     * Empty statement coverage in statementsBulkResponse format.
     */
    public static final String STATEMENT_COVERAGE_EMPTY = """
            <?xml version="1.0" encoding="utf-8"?>
            <cov:statementsBulkResponse xmlns:cov="http://www.sap.com/adt/cov"
                                       xmlns:adtcore="http://www.sap.com/adt/core">
            </cov:statementsBulkResponse>
            """;

    // ==================== Object Metadata (for activation status) ====================

    /**
     * Class metadata with active version only.
     * adtcore:version="active" indicates no pending changes.
     */
    public static final String CLASS_METADATA_ACTIVE = """
            <?xml version="1.0" encoding="utf-8"?>
            <class:abapClass xmlns:class="http://www.sap.com/adt/oo/classes"
                            xmlns:adtcore="http://www.sap.com/adt/core"
                            adtcore:name="ZCL_TEST_CLASS"
                            adtcore:type="CLAS/OC"
                            adtcore:version="active"
                            adtcore:description="Test Class">
            </class:abapClass>
            """;

    /**
     * Class metadata with inactive changes pending activation.
     * adtcore:version="activeWithInactiveVersion" indicates saved but not activated changes.
     */
    public static final String CLASS_METADATA_WITH_INACTIVE = """
            <?xml version="1.0" encoding="utf-8"?>
            <class:abapClass xmlns:class="http://www.sap.com/adt/oo/classes"
                            xmlns:adtcore="http://www.sap.com/adt/core"
                            adtcore:name="ZCL_TEST_CLASS"
                            adtcore:type="CLAS/OC"
                            adtcore:version="activeWithInactiveVersion"
                            adtcore:description="Test Class">
            </class:abapClass>
            """;

    /**
     * Class metadata for new object not yet activated.
     * adtcore:version="inactive" indicates object was created but never activated.
     */
    public static final String CLASS_METADATA_INACTIVE = """
            <?xml version="1.0" encoding="utf-8"?>
            <class:abapClass xmlns:class="http://www.sap.com/adt/oo/classes"
                            xmlns:adtcore="http://www.sap.com/adt/core"
                            adtcore:name="ZCL_NEW_CLASS"
                            adtcore:type="CLAS/OC"
                            adtcore:version="inactive"
                            adtcore:description="New Test Class">
            </class:abapClass>
            """;

    /**
     * Interface metadata with active version only.
     */
    public static final String INTERFACE_METADATA_ACTIVE = """
            <?xml version="1.0" encoding="utf-8"?>
            <intf:abapInterface xmlns:intf="http://www.sap.com/adt/oo/interfaces"
                               xmlns:adtcore="http://www.sap.com/adt/core"
                               adtcore:name="ZIF_TEST_INTERFACE"
                               adtcore:type="INTF/OI"
                               adtcore:version="active"
                               adtcore:description="Test Interface">
            </intf:abapInterface>
            """;

    /**
     * Program metadata with partly active state.
     * adtcore:version="partlyActive" indicates mixed activation state (some includes active, some not).
     */
    public static final String PROGRAM_METADATA_PARTLY_ACTIVE = """
            <?xml version="1.0" encoding="utf-8"?>
            <program:abapProgram xmlns:program="http://www.sap.com/adt/programs/programs"
                                xmlns:adtcore="http://www.sap.com/adt/core"
                                adtcore:name="ZTEST_PROGRAM"
                                adtcore:type="PROG/P"
                                adtcore:version="partlyActive"
                                adtcore:description="Test Program">
            </program:abapProgram>
            """;

    // ==================== Inactive Objects ====================

    /**
     * Response from the inactiveobjects endpoint showing objects with inactive versions.
     * Used to detect when an object has unactivated changes.
     * Note: URI uses uppercase class name to match the handler's buildObjectUri output.
     */
    public static final String INACTIVE_OBJECTS_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <ioc:inactiveObjects xmlns:ioc="http://www.sap.com/adt/ioc"
                                xmlns:adtcore="http://www.sap.com/adt/core">
                <ioc:entry>
                    <ioc:object adtcore:uri="/sap/bc/adt/oo/classes/ZCL_TEST_CLASS"
                               adtcore:type="CLAS/OC"
                               adtcore:name="ZCL_TEST_CLASS"
                               adtcore:description="Test Class"/>
                </ioc:entry>
            </ioc:inactiveObjects>
            """;

    /**
     * Empty response from inactiveobjects endpoint (no inactive objects).
     */
    public static final String INACTIVE_OBJECTS_EMPTY = """
            <?xml version="1.0" encoding="utf-8"?>
            <ioc:inactiveObjects xmlns:ioc="http://www.sap.com/adt/ioc"
                                xmlns:adtcore="http://www.sap.com/adt/core">
            </ioc:inactiveObjects>
            """;

    // ==================== Lock Response ====================

    /**
     * Successful lock response.
     */
    public static final String LOCK_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <abapsource:objectLock xmlns:abapsource="http://www.sap.com/adt/abapsource"
                                   lockHandle="ABC123XYZ"
                                   accessMode="MODIFY">
                <transport request="DEVK900001" task="DEVK900002"/>
            </abapsource:objectLock>
            """;

    /**
     * Lock response without transport (local object).
     */
    public static final String LOCK_RESPONSE_LOCAL = """
            <?xml version="1.0" encoding="utf-8"?>
            <abapsource:objectLock xmlns:abapsource="http://www.sap.com/adt/abapsource"
                                   lockHandle="DEF456UVW"
                                   accessMode="MODIFY">
            </abapsource:objectLock>
            """;

    // ==================== Search Results ====================

    /**
     * Search results with multiple objects.
     */
    public static final String SEARCH_RESULTS = """
            <?xml version="1.0" encoding="utf-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_one"
                                         adtcore:type="CLAS/OC"
                                         adtcore:name="ZCL_TEST_ONE"
                                         adtcore:description="Test Class One">
                    <adtcore:packageRef adtcore:uri="/sap/bc/adt/packages/ztest"
                                        adtcore:name="ZTEST"/>
                </adtcore:objectReference>
                <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_two"
                                         adtcore:type="CLAS/OC"
                                         adtcore:name="ZCL_TEST_TWO"
                                         adtcore:description="Test Class Two">
                    <adtcore:packageRef adtcore:uri="/sap/bc/adt/packages/ztest"
                                        adtcore:name="ZTEST"/>
                </adtcore:objectReference>
            </adtcore:objectReferences>
            """;

    /**
     * Empty search results.
     */
    public static final String SEARCH_RESULTS_EMPTY = """
            <?xml version="1.0" encoding="utf-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
            </adtcore:objectReferences>
            """;

    // ==================== Table/Structure Metadata ====================

    /**
     * Table definition response.
     */
    public static final String TABLE_METADATA = """
            <?xml version="1.0" encoding="utf-8"?>
            <ddic:table xmlns:ddic="http://www.sap.com/adt/ddic"
                       xmlns:adtcore="http://www.sap.com/adt/core"
                       adtcore:name="ZTESTTABLE"
                       adtcore:type="TABL/DT"
                       adtcore:description="Test Table"
                       abapLanguageVersion="standard"
                       deliveryClass="A"
                       bufferingPermitted="true">
                <ddic:tableFields>
                    <ddic:tableField name="MANDT" position="1" isKey="true" dataElement="MANDT"/>
                    <ddic:tableField name="ID" position="2" isKey="true" dataElement="SYSUUID_C32"/>
                    <ddic:tableField name="NAME" position="3" isKey="false" dataElement="CHAR50"/>
                </ddic:tableFields>
            </ddic:table>
            """;

    // ==================== ATC Results ====================

    /**
     * ATC findings with various severities.
     */
    public static final String ATC_FINDINGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc"
                         atc:id="ATC12345"
                         atc:timestamp="2024-01-15T10:30:00Z">
                <atc:objects>
                    <atc:object adtcore:name="ZCL_TEST_CLASS" adtcore:type="CLAS"
                               xmlns:adtcore="http://www.sap.com/adt/core">
                        <atc:findings>
                            <atc:finding atc:checkId="CL_CI_TEST_PRAGMA"
                                        atc:checkTitle="Missing pragma"
                                        atc:messageId="001"
                                        atc:messageTitle="Use pragma for test method"
                                        atc:priority="1">
                                <atc:location adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/source/main#start=10,1"/>
                            </atc:finding>
                            <atc:finding atc:checkId="CL_CI_TEST_WARNING"
                                        atc:checkTitle="Code style warning"
                                        atc:messageId="002"
                                        atc:messageTitle="Consider using inline declaration"
                                        atc:priority="2">
                                <atc:location adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class/source/main#start=25,1"/>
                            </atc:finding>
                        </atc:findings>
                    </atc:object>
                </atc:objects>
            </atc:worklist>
            """;

    /**
     * ATC with no findings.
     */
    public static final String ATC_NO_FINDINGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <atc:worklist xmlns:atc="http://www.sap.com/adt/atc"
                         atc:id="ATC12346"
                         atc:timestamp="2024-01-15T10:35:00Z">
                <atc:objects/>
            </atc:worklist>
            """;

    // ==================== Transport Requests ====================

    /**
     * Transport request list.
     */
    public static final String TRANSPORT_REQUESTS = """
            <?xml version="1.0" encoding="utf-8"?>
            <tm:transports xmlns:tm="http://www.sap.com/adt/cts/transport">
                <tm:transport tm:number="DEVK900001"
                             tm:description="Feature implementation"
                             tm:status="D"
                             tm:owner="DEVELOPER">
                    <tm:tasks>
                        <tm:task tm:number="DEVK900002"
                                tm:description="Task 1"
                                tm:status="D"
                                tm:owner="DEVELOPER"/>
                    </tm:tasks>
                </tm:transport>
                <tm:transport tm:number="DEVK900010"
                             tm:description="Bug fix"
                             tm:status="D"
                             tm:owner="DEVELOPER">
                </tm:transport>
            </tm:transports>
            """;

    // ==================== Where-Used Results ====================

    /**
     * Where-used results.
     */
    public static final String WHERE_USED_RESULTS = """
            <?xml version="1.0" encoding="utf-8"?>
            <usageReferences:usageReferencesResult xmlns:usageReferences="http://www.sap.com/adt/ris/usageReferences">
                <usageReferences:usedByReferences>
                    <usageReferences:reference objectIdentifier="ABC123"
                                              uri="/sap/bc/adt/oo/classes/zcl_caller"
                                              type="CLAS/OC"
                                              name="ZCL_CALLER">
                        <usageReferences:packageRef name="ZTEST"/>
                    </usageReferences:reference>
                    <usageReferences:reference objectIdentifier="DEF456"
                                              uri="/sap/bc/adt/programs/programs/ztest_report"
                                              type="PROG/P"
                                              name="ZTEST_REPORT">
                        <usageReferences:packageRef name="ZTEST"/>
                    </usageReferences:reference>
                </usageReferences:usedByReferences>
            </usageReferences:usageReferencesResult>
            """;

    // ==================== Version History ====================

    /**
     * Version history response.
     */
    public static final String VERSION_HISTORY = """
            <?xml version="1.0" encoding="utf-8"?>
            <vit:versionInfoCollection xmlns:vit="http://www.sap.com/adt/vit">
                <vit:versionInfo vit:versionNumber="00001"
                                vit:author="DEVELOPER"
                                vit:date="2024-01-10T09:30:00Z"
                                vit:transport="DEVK900001"
                                vit:description="Initial implementation"/>
                <vit:versionInfo vit:versionNumber="00002"
                                vit:author="DEVELOPER"
                                vit:date="2024-01-15T14:20:00Z"
                                vit:transport="DEVK900010"
                                vit:description="Bug fix"/>
            </vit:versionInfoCollection>
            """;

    // ==================== Data Preview ====================

    /**
     * Data preview response (columnar format).
     */
    public static final String DATA_PREVIEW = """
            <?xml version="1.0" encoding="utf-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/dataPreview">
                <dataPreview:columns>
                    <dataPreview:metadata>
                        <dataPreview:column name="MANDT" type="CHAR" length="3" isKey="true"/>
                        <dataPreview:column name="CARRID" type="CHAR" length="3" isKey="true"/>
                        <dataPreview:column name="CONNID" type="NUMC" length="4" isKey="true"/>
                        <dataPreview:column name="FLDATE" type="DATS" length="8" isKey="true"/>
                    </dataPreview:metadata>
                    <dataPreview:dataSet>
                        <dataPreview:data>
                            <dataPreview:MANDT>100</dataPreview:MANDT>
                            <dataPreview:CARRID>LH</dataPreview:CARRID>
                            <dataPreview:CONNID>0400</dataPreview:CONNID>
                            <dataPreview:FLDATE>20240115</dataPreview:FLDATE>
                        </dataPreview:data>
                        <dataPreview:data>
                            <dataPreview:MANDT>100</dataPreview:MANDT>
                            <dataPreview:CARRID>AA</dataPreview:CARRID>
                            <dataPreview:CONNID>0017</dataPreview:CONNID>
                            <dataPreview:FLDATE>20240120</dataPreview:FLDATE>
                        </dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

    // ==================== Activation ====================

    /**
     * Activation success response.
     */
    public static final String ACTIVATION_SUCCESS = """
            <?xml version="1.0" encoding="utf-8"?>
            <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                <adtcore:objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_test_class"
                                         adtcore:type="CLAS/OC"
                                         adtcore:name="ZCL_TEST_CLASS"/>
            </adtcore:objectReferences>
            """;

    /**
     * Activation with syntax errors.
     */
    public static final String ACTIVATION_SYNTAX_ERROR = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkrun:checkMessageList xmlns:chkrun="http://www.sap.com/adt/checkrun">
                <chkrun:checkMessage uri="/sap/bc/adt/oo/classes/zcl_test_class/source/main#start=10,5"
                                    type="E"
                                    shortText="Statement &quot;DATA&quot; expected.">
                    <atom:link href="/sap/bc/adt/oo/classes/zcl_test_class/source/main#start=10,5"
                              rel="http://www.sap.com/adt/relations/sources"
                              xmlns:atom="http://www.w3.org/2005/Atom"/>
                </chkrun:checkMessage>
            </chkrun:checkMessageList>
            """;

    // ==================== Package Contents ====================

    /**
     * Package contents response.
     */
    public static final String PACKAGE_CONTENTS = """
            <?xml version="1.0" encoding="utf-8"?>
            <asx:abap xmlns:asx="http://www.sap.com/abapxml">
                <asx:values>
                    <DATA>
                        <OBJECT_NAME>ZCL_TEST_ONE</OBJECT_NAME>
                        <OBJECT_TYPE>CLAS</OBJECT_TYPE>
                        <OBJECT_TEXT>Test Class One</OBJECT_TEXT>
                    </DATA>
                    <DATA>
                        <OBJECT_NAME>ZCL_TEST_TWO</OBJECT_NAME>
                        <OBJECT_TYPE>CLAS</OBJECT_TYPE>
                        <OBJECT_TEXT>Test Class Two</OBJECT_TEXT>
                    </DATA>
                    <DATA>
                        <OBJECT_NAME>ZIF_TEST_INTERFACE</OBJECT_NAME>
                        <OBJECT_TYPE>INTF</OBJECT_TYPE>
                        <OBJECT_TEXT>Test Interface</OBJECT_TEXT>
                    </DATA>
                </asx:values>
            </asx:abap>
            """;

    // ==================== Runtime Errors (ST22) ====================

    /**
     * Runtime error dump list (Atom feed).
     */
    public static final String RUNTIME_ERROR_LIST = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>ABAP Runtime Errors</title>
                <updated>2024-01-15T12:00:00Z</updated>
                <entry>
                    <title>COMPUTE_INT_ZERODIVIDE</title>
                    <id>20240115120000_sap-host_TESTUSER_001_00</id>
                    <updated>2024-01-15T12:00:00Z</updated>
                    <author><name>TESTUSER</name></author>
                    <summary>Division by zero in program ZTEST_PROGRAM</summary>
                </entry>
                <entry>
                    <title>MESSAGE_TYPE_X</title>
                    <id>20240115110000_sap-host_TESTUSER_001_01</id>
                    <updated>2024-01-15T11:00:00Z</updated>
                    <author><name>TESTUSER</name></author>
                    <summary>Short dump in class ZCL_MY_CLASS</summary>
                </entry>
            </feed>
            """;

    /**
     * Empty runtime error dump list.
     */
    public static final String RUNTIME_ERROR_LIST_EMPTY = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>ABAP Runtime Errors</title>
                <updated>2024-01-15T12:00:00Z</updated>
            </feed>
            """;

    /**
     * Formatted runtime error dump (text/plain).
     */
    public static final String RUNTIME_ERROR_FORMATTED = """
            *** Short dump of 15.01.2024 12:00:00 ***
            Runtime Error          COMPUTE_INT_ZERODIVIDE
            Exception              CX_SY_ZERODIVIDE
            Program                ZTEST_PROGRAM
            Include                ZTEST_PROGRAM
            Source Position        42
            User                   TESTUSER
            Client                 001

            What happened?
                An attempt was made to divide by zero.

            Source Code Extract:
                40  DATA(lv_result) = lv_numerator / lv_divisor.
                41  " lv_divisor was 0
                42 >  rv_value = lv_result.
            """;

    /**
     * Runtime error metadata (XML).
     */
    public static final String RUNTIME_ERROR_METADATA = """
            <?xml version="1.0" encoding="utf-8"?>
            <dump:dump xmlns:dump="http://www.sap.com/adt/runtime/dump"
                       xmlns:atom="http://www.w3.org/2005/Atom">
                <dump:id>20240115120000_sap-host_TESTUSER_001_00</dump:id>
                <dump:errorId>COMPUTE_INT_ZERODIVIDE</dump:errorId>
                <dump:program>ZTEST_PROGRAM</dump:program>
                <dump:user>TESTUSER</dump:user>
                <dump:timestamp>2024-01-15T12:00:00Z</dump:timestamp>
                <atom:link rel="formatted" href="/sap/bc/adt/runtime/dump/20240115120000_sap-host_TESTUSER_001_00/formatted"/>
                <atom:link rel="summary" href="/sap/bc/adt/runtime/dump/20240115120000_sap-host_TESTUSER_001_00/summary"/>
            </dump:dump>
            """;
}
