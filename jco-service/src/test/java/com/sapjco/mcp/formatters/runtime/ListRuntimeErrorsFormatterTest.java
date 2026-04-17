package com.sapjco.mcp.formatters.runtime;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ListRuntimeErrorsFormatter.
 */
class ListRuntimeErrorsFormatterTest {

    private ListRuntimeErrorsFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ListRuntimeErrorsFormatter();
    }

    @Test
    void getToolName_returnsListRuntimeErrors() {
        assertEquals("ListRuntimeErrors", formatter.getToolName());
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
    void format_noEntries_showsNoErrors() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atom:feed xmlns:atom="http://www.w3.org/2005/Atom">
                <atom:title>Runtime Error Dumps</atom:title>
            </atom:feed>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Runtime Error Dumps (0 entries)"));
        assertTrue(result.contains("No runtime errors found"));
    }

    @Test
    void format_singleEntry_showsOneError() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atom:feed xmlns:atom="http://www.w3.org/2005/Atom">
                <atom:entry>
                    <atom:author><atom:name>TESTUSER</atom:name></atom:author>
                    <atom:category term="ASSERTION_FAILED" label="ABAP runtime error"/>
                    <atom:category term="/SCMTMS/CL_BUFVAR_DISPATCHER==CP" label="Terminated ABAP program"/>
                    <atom:published>2026-03-07T14:01:08Z</atom:published>
                    <atom:link rel="self" type="text/plain"
                        href="/sap/bc/adt/runtime/dump/20260307150108ldcierx_ERX_00%20TESTUSER%20114/formatted"/>
                </atom:entry>
            </atom:feed>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Runtime Error Dumps (1 entry)"));
        assertTrue(result.contains("ASSERTION_FAILED"));
        assertTrue(result.contains("/SCMTMS/CL_BUFVAR_DISPATCHER==CP"));
        assertTrue(result.contains("TESTUSER"));
        assertTrue(result.contains("2026-03-07T14:01:08Z"));
    }

    @Test
    void format_multipleEntries_showsAllErrors() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atom:feed xmlns:atom="http://www.w3.org/2005/Atom">
                <atom:entry>
                    <atom:author><atom:name>USERX</atom:name></atom:author>
                    <atom:category term="ASSERTION_FAILED" label="ABAP runtime error"/>
                    <atom:category term="PROG_A" label="Terminated ABAP program"/>
                    <atom:published>2026-03-07T14:01:08Z</atom:published>
                    <atom:link rel="self" type="text/plain"
                        href="/sap/bc/adt/runtime/dump/DUMP_ID_1/formatted"/>
                </atom:entry>
                <atom:entry>
                    <atom:author><atom:name>USERY</atom:name></atom:author>
                    <atom:category term="CALL_FUNCTION_NOT_REMOTE" label="ABAP runtime error"/>
                    <atom:category term="PROG_B" label="Terminated ABAP program"/>
                    <atom:published>2026-03-07T12:03:07Z</atom:published>
                    <atom:link rel="self" type="text/plain"
                        href="/sap/bc/adt/runtime/dump/DUMP_ID_2/formatted"/>
                </atom:entry>
                <atom:entry>
                    <atom:author><atom:name>USERZ</atom:name></atom:author>
                    <atom:category term="RABAX_STATE" label="ABAP runtime error"/>
                    <atom:category term="PROG_C" label="Terminated ABAP program"/>
                    <atom:published>2026-03-07T10:00:00Z</atom:published>
                    <atom:link rel="self" type="text/plain"
                        href="/sap/bc/adt/runtime/dump/DUMP_ID_3/formatted"/>
                </atom:entry>
            </atom:feed>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Runtime Error Dumps (3 entries)"));
        assertTrue(result.contains("ASSERTION_FAILED"));
        assertTrue(result.contains("CALL_FUNCTION_NOT_REMOTE"));
        assertTrue(result.contains("RABAX_STATE"));
        assertTrue(result.contains("PROG_A"));
        assertTrue(result.contains("PROG_B"));
        assertTrue(result.contains("PROG_C"));
        assertTrue(result.contains("USERX"));
        assertTrue(result.contains("USERY"));
        assertTrue(result.contains("USERZ"));
    }

    @Test
    void format_extractsDumpId_fromSelfLink() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atom:feed xmlns:atom="http://www.w3.org/2005/Atom">
                <atom:entry>
                    <atom:author><atom:name>USER1</atom:name></atom:author>
                    <atom:category term="ERROR_TYPE" label="ABAP runtime error"/>
                    <atom:category term="ZPROG" label="Terminated ABAP program"/>
                    <atom:published>2026-03-07T12:00:00Z</atom:published>
                    <atom:link rel="self" type="text/plain"
                        href="/sap/bc/adt/runtime/dump/20260307150108ldcierx_ERX_00%20USER1%20114/formatted"/>
                    <atom:link rel="alternate" type="application/xml"
                        href="/sap/bc/adt/runtime/dump/20260307150108ldcierx_ERX_00%20USER1%20114"/>
                </atom:entry>
            </atom:feed>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Dump IDs (for use with GetRuntimeError)"));
        assertTrue(result.contains("20260307150108ldcierx_ERX_00%20USER1%20114"));
        // Should NOT contain the "/formatted" suffix
        assertFalse(result.contains("/formatted"));
    }

    @Test
    void format_missingOptionalFields_handlesGracefully() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <atom:feed xmlns:atom="http://www.w3.org/2005/Atom">
                <atom:entry>
                    <atom:published>2026-03-07T12:00:00Z</atom:published>
                </atom:entry>
            </atom:feed>
            """;

        String result = formatter.format(xml);

        // Should not throw, should still show one entry
        assertTrue(result.contains("Runtime Error Dumps (1 entry)"));
        assertTrue(result.contains("2026-03-07T12:00:00Z"));
    }
}
