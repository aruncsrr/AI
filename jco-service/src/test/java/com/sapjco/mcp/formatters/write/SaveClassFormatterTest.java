package com.sapjco.mcp.formatters.write;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SaveClassFormatter.
 */
class SaveClassFormatterTest {

    private SaveClassFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new SaveClassFormatter();
    }

    @Test
    void getToolName_returnsSaveClass() {
        assertEquals("SaveClass", formatter.getToolName());
    }

    @Test
    void format_nullResponse_showsSuccess() throws FormattingException {
        String result = formatter.format(null);

        assertTrue(result.contains("Save Class Result"));
        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("saved successfully"));
        assertTrue(result.contains("NOT activated"));
        assertTrue(result.contains("ActivateObject"));
    }

    @Test
    void format_emptyResponse_showsSuccess() throws FormattingException {
        String result = formatter.format("");

        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("saved successfully"));
    }

    @Test
    void format_blankResponse_showsSuccess() throws FormattingException {
        String result = formatter.format("   ");

        assertTrue(result.contains("Status: SUCCESS"));
    }

    @Test
    void format_successResponse_showsSuccess() throws FormattingException {
        String result = formatter.format("OK");

        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("saved successfully"));
    }

    @Test
    void format_errorResponse_showsFailed() throws FormattingException {
        String response = "<error>Syntax error in line 42</error>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Error Details:"));
    }

    @Test
    void format_typeEResponse_showsFailed() throws FormattingException {
        String response = "<message type=\"E\">Object locked</message>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
    }

    @Test
    void format_severityErrorResponse_showsFailed() throws FormattingException {
        String response = "<entry severity=\"error\">Compilation error</entry>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
    }

    @Test
    void format_exceptionResponse_showsFailed() throws FormattingException {
        String response = "<exception>Runtime error occurred</exception>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
    }

    @Test
    void format_errorWithMessage_extractsMessage() throws FormattingException {
        String response = "<error><message>Field LV_TEST is unknown</message></error>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Field LV_TEST is unknown"));
    }

    @Test
    void format_errorWithoutMessage_showsRawResponse() throws FormattingException {
        String response = "<error>Some error without message tags</error>";

        String result = formatter.format(response);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Some error without message tags"));
    }
}
