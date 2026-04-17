package com.sapjco.mcp.formatters.testing;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ATCCheckVariantsFormatter.
 */
class ATCCheckVariantsFormatterTest {

    private ATCCheckVariantsFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ATCCheckVariantsFormatter();
    }

    @Test
    void getToolName_returnsListATCCheckVariants() {
        assertEquals("ListATCCheckVariants", formatter.getToolName());
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
    void format_noVariants_showsNotFound() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nameditem:namedItemList xmlns:nameditem="http://www.sap.com/adt/nameditem">
            </nameditem:namedItemList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("ATC Check Variants"));
        assertTrue(result.contains("No check variants found"));
    }

    @Test
    void format_singleVariant_showsNameAndDescription() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nameditem:namedItemList xmlns:nameditem="http://www.sap.com/adt/nameditem">
                <nameditem:namedItem>
                    <nameditem:name>DEFAULT</nameditem:name>
                    <nameditem:description>Default Variant</nameditem:description>
                </nameditem:namedItem>
            </nameditem:namedItemList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 1 variant"));
        assertTrue(result.contains("[1] DEFAULT - Default Variant"));
    }

    @Test
    void format_multipleVariants_showsAll() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nameditem:namedItemList xmlns:nameditem="http://www.sap.com/adt/nameditem">
                <nameditem:namedItem>
                    <nameditem:name>DEFAULT</nameditem:name>
                    <nameditem:description>Default Variant</nameditem:description>
                </nameditem:namedItem>
                <nameditem:namedItem>
                    <nameditem:name>CUSTOM_CHECK</nameditem:name>
                    <nameditem:description>Custom Project Checks</nameditem:description>
                </nameditem:namedItem>
                <nameditem:namedItem>
                    <nameditem:name>SECURITY</nameditem:name>
                    <nameditem:description>Security Focused Checks</nameditem:description>
                </nameditem:namedItem>
            </nameditem:namedItemList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 3 variants"));
        assertTrue(result.contains("[1] DEFAULT"));
        assertTrue(result.contains("[2] CUSTOM_CHECK"));
        assertTrue(result.contains("[3] SECURITY"));
        assertTrue(result.contains("Custom Project Checks"));
        assertTrue(result.contains("Security Focused Checks"));
    }

    @Test
    void format_variantWithoutDescription_showsNameOnly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nameditem:namedItemList xmlns:nameditem="http://www.sap.com/adt/nameditem">
                <nameditem:namedItem>
                    <nameditem:name>NODESC</nameditem:name>
                </nameditem:namedItem>
            </nameditem:namedItemList>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("[1] NODESC"));
        assertFalse(result.contains(" - "));
    }

    @Test
    void format_pluralization_correctGrammar() throws FormattingException {
        String singleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nameditem:namedItemList xmlns:nameditem="http://www.sap.com/adt/nameditem">
                <nameditem:namedItem>
                    <nameditem:name>ONE</nameditem:name>
                </nameditem:namedItem>
            </nameditem:namedItemList>
            """;

        String multiXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nameditem:namedItemList xmlns:nameditem="http://www.sap.com/adt/nameditem">
                <nameditem:namedItem><nameditem:name>A</nameditem:name></nameditem:namedItem>
                <nameditem:namedItem><nameditem:name>B</nameditem:name></nameditem:namedItem>
            </nameditem:namedItemList>
            """;

        assertTrue(formatter.format(singleXml).contains("1 variant\n"));
        assertTrue(formatter.format(multiXml).contains("2 variants\n"));
    }
}
