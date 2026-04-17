package com.sapjco.mcp.formatters.write;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ActivateObjectFormatter.
 */
class ActivateObjectFormatterTest {

    private ActivateObjectFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ActivateObjectFormatter();
    }

    @Test
    void getToolName_returnsActivateObject() {
        assertEquals("ActivateObject", formatter.getToolName());
    }

    @Test
    void format_nullResponse_showsSuccess() throws FormattingException {
        String result = formatter.format(null);

        assertTrue(result.contains("Activation Result"));
        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("activated successfully"));
    }

    @Test
    void format_emptyResponse_showsSuccess() throws FormattingException {
        String result = formatter.format("");

        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("activated successfully"));
    }

    @Test
    void format_blankResponse_showsSuccess() throws FormattingException {
        String result = formatter.format("   ");

        assertTrue(result.contains("Status: SUCCESS"));
    }

    @Test
    void format_plainTextSuccess_showsSuccess() throws FormattingException {
        String result = formatter.format("Object activated successfully");

        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("activated successfully"));
    }

    @Test
    void format_malformedXmlWithErrorIndicator_showsFailed() throws FormattingException {
        // Malformed XML triggers containsError check - finds <error tag
        String result = formatter.format("<error Something went wrong");

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Error Details:"));
    }

    @Test
    void format_plainTextWithTypeE_showsFailed() throws FormattingException {
        String result = formatter.format("message type=\"E\" text=\"Syntax error\"");

        assertTrue(result.contains("Status: FAILED"));
    }

    @Test
    void format_malformedXmlWithExceptionIndicator_showsFailed() throws FormattingException {
        // Malformed XML triggers containsError check - finds <exception tag
        String result = formatter.format("<exception Runtime exception");

        assertTrue(result.contains("Status: FAILED"));
    }

    @Test
    void format_validXmlWithErrorElement_showsSuccess() throws FormattingException {
        // Valid XML <error> parses but doesn't match message/result/entry elements
        // so it's treated as success (no recognized error markers)
        String result = formatter.format("<error>Something went wrong</error>");

        assertTrue(result.contains("Status: SUCCESS"));
    }

    @Test
    void format_xmlWithErrors_showsFailedStatus() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <activation>
                <message type="E">Syntax error in line 42</message>
                <message type="E">Missing closing bracket</message>
            </activation>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Errors: 2"));
        assertTrue(result.contains("Error Details:"));
    }

    @Test
    void format_xmlWithWarnings_showsSuccessWithWarnings() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <activation>
                <message type="W">Deprecated method usage</message>
            </activation>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: SUCCESS (with warnings)"));
        assertTrue(result.contains("Warnings: 1"));
    }

    @Test
    void format_xmlWithErrorAndWarning_showsFailed() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <activation>
                <message type="E">Syntax error</message>
                <message type="W">Deprecated method</message>
            </activation>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Errors: 1"));
        assertTrue(result.contains("Warnings: 1"));
    }

    @Test
    void format_xmlNoErrors_showsSuccess() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <activation>
                <result status="ok"/>
            </activation>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: SUCCESS"));
        assertTrue(result.contains("activated successfully"));
    }

    @Test
    void format_xmlWithEntrySeverityError_showsFailed() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <activation>
                <entry severity="error">
                    <shortText>Type mismatch</shortText>
                </entry>
            </activation>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Type mismatch"));
    }

    @Test
    void format_xmlWithEntrySeverityWarning_showsSuccessWithWarnings() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <activation>
                <entry severity="warning">
                    <shortText>Consider using NEW syntax</shortText>
                </entry>
            </activation>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: SUCCESS (with warnings)"));
        assertTrue(result.contains("Consider using NEW syntax"));
    }

    @Test
    void format_xmlWithErrorTypeText_showsMessage() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <activation>
                <message type="error">Missing declaration for variable LV_TEST</message>
            </activation>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: FAILED"));
        assertTrue(result.contains("Missing declaration"));
    }

    @Test
    void format_xmlWithWarningTypeText_showsMessage() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <activation>
                <message type="warning">Unused variable LV_TEMP</message>
            </activation>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Status: SUCCESS (with warnings)"));
        assertTrue(result.contains("Unused variable"));
    }

    @Test
    void format_malformedXml_treatsAsPlainText() throws FormattingException {
        String result = formatter.format("<activation><message>");

        // Should not throw, should treat as success since no error indicators
        assertTrue(result.contains("Status: SUCCESS"));
    }

    @Test
    void format_containsErrorKeyword_showsFailed() throws FormattingException {
        String result = formatter.format("severity=\"error\" detected");

        assertTrue(result.contains("Status: FAILED"));
    }
}
