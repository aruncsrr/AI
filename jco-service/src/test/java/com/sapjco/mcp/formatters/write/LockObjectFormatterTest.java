package com.sapjco.mcp.formatters.write;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LockObjectFormatter.
 */
class LockObjectFormatterTest {

    private LockObjectFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new LockObjectFormatter();
    }

    @Test
    void getToolName_returnsLockObject() {
        assertEquals("LockObject", formatter.getToolName());
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
    void format_successWithLockHandle_showsLockHandle() throws FormattingException {
        String response = "LOCK_HANDLE=ABC123DEF456&CORRNR=NPLK900001";

        String result = formatter.format(response);

        assertTrue(result.contains("Lock Object Result"));
        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("Lock Handle: ABC123DEF456"));
        assertTrue(result.contains("Transport: NPLK900001"));
    }

    @Test
    void format_xmlWithLockHandle_extractsHandle() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <lock lockHandle="XYZ789">
                <transport>DEVK123456</transport>
            </lock>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("Lock Handle: XYZ789"));
    }

    @Test
    void format_xmlWithObjectLock_extractsHandle() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
                <objectLock lockHandle="HANDLE_001"/>
            </response>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("Lock Handle: HANDLE_001"));
    }

    @Test
    void format_xmlWithTransport_extractsTransport() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
                <transport>NPLK900099</transport>
            </response>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("Transport: NPLK900099"));
    }

    @Test
    void format_xmlWithCorrNr_extractsTransport() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
                <corrNr>DEVK900123</corrNr>
            </response>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Transport: DEVK900123"));
    }

    @Test
    void format_errorResponse_showsFailed() throws FormattingException {
        String response = "<error>Cannot lock object</error>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Could not lock the object"));
    }

    @Test
    void format_lockedByAnotherUser_showsLockedMessage() throws FormattingException {
        String response = "Object is locked by USER123 in system";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("locked by"));
    }

    @Test
    void format_successWithUnlockReminder_showsReminder() throws FormattingException {
        String response = "LOCK_HANDLE=TEST123";

        String result = formatter.format(response);

        assertTrue(result.contains("UnlockObject"));
        assertTrue(result.contains("SaveClass"));
    }

    @Test
    void format_typeEResponse_showsFailed() throws FormattingException {
        String response = "<message type=\"E\">Lock failed</message>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
    }

    @Test
    void format_severityErrorResponse_showsFailed() throws FormattingException {
        String response = "<entry severity=\"error\">Lock conflict</entry>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
    }

    @Test
    void format_errorWithMessage_extractsMessage() throws FormattingException {
        String response = "<error><message>Object ZCL_TEST locked</message></error>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Object ZCL_TEST locked"));
    }
}
