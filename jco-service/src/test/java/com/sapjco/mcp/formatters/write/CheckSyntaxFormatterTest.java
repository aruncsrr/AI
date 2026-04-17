package com.sapjco.mcp.formatters.write;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckSyntaxFormatterTest {

    private CheckSyntaxFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new CheckSyntaxFormatter();
    }

    @Test
    void getToolName_returnsCheckSyntax() {
        assertThat(formatter.getToolName()).isEqualTo("CheckSyntax");
    }

    @Test
    void format_emptyResponse_returnsValid() throws FormattingException {
        String result = formatter.format("");
        assertThat(result).contains("Status: VALID");
        assertThat(result).contains("No syntax errors found");
    }

    @Test
    void format_nullResponse_returnsValid() throws FormattingException {
        String result = formatter.format(null);
        assertThat(result).contains("Status: VALID");
    }

    @Test
    void format_actualSapXml_parsesCorrectly() throws FormattingException {
        // Actual SAP ADT syntax check XML structure
        String sapXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkl:messages xmlns:chkl="http://www.sap.com/abapxml/checklist">
              <chkl:properties checkExecuted="false" activationExecuted="false"/>
              <msg objDescr="Class ZTEST_CLASS, Method TEST_METHOD" type="E" line="1"
                   href="/sap/bc/adt/oo/classes/ztest_class/source/main#start=739,8;end=739,26"
                   code="MESSAGE(GWR)">
                <shortText><txt>The field "LV_UNDECLARED_VAR" is unknown, but there is a field with the similar name "LV_DECLARED_VAR".</txt></shortText>
                <correctionHint kind="D" word="LV_UNDECLARED_VAR"/>
                <correctionHint kind="I" word="LV_DECLARED_VAR"/>
              </msg>
            </chkl:messages>
            """;

        String result = formatter.format(sapXml);

        // Verify status
        assertThat(result).contains("Status: INVALID");
        assertThat(result).contains("Syntax errors detected");

        // Verify counts
        assertThat(result).contains("Errors: 1");

        // Verify message text is extracted from nested <shortText><txt>...</txt></shortText>
        assertThat(result).contains("The field \"LV_UNDECLARED_VAR\" is unknown");

        // Verify location from objDescr attribute
        assertThat(result).contains("Location: Class ZTEST_CLASS, Method TEST_METHOD");

        // Verify line/column extracted from href fragment
        assertThat(result).contains("Line 739, Column 8");

        // Verify suggestion from correctionHint kind="I"
        assertThat(result).contains("Suggestion: LV_DECLARED_VAR");
    }

    @Test
    void format_multipleErrors_parsesAll() throws FormattingException {
        String sapXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkl:messages xmlns:chkl="http://www.sap.com/abapxml/checklist">
              <msg objDescr="Class A, Method M1" type="E"
                   href="/sap/bc/adt/oo/classes/a/source/main#start=10,5;end=10,15">
                <shortText><txt>First error message</txt></shortText>
              </msg>
              <msg objDescr="Class A, Method M2" type="W"
                   href="/sap/bc/adt/oo/classes/a/source/main#start=20,3;end=20,10">
                <shortText><txt>Warning message</txt></shortText>
              </msg>
              <msg objDescr="Class A, Method M3" type="E"
                   href="/sap/bc/adt/oo/classes/a/source/main#start=30,1;end=30,5">
                <shortText><txt>Second error message</txt></shortText>
              </msg>
            </chkl:messages>
            """;

        String result = formatter.format(sapXml);

        assertThat(result).contains("Status: INVALID");
        assertThat(result).contains("Errors: 2");
        assertThat(result).contains("Warnings: 1");

        assertThat(result).contains("[ERROR] First error message");
        assertThat(result).contains("[WARNING] Warning message");
        assertThat(result).contains("[ERROR] Second error message");

        assertThat(result).contains("Line 10, Column 5");
        assertThat(result).contains("Line 20, Column 3");
        assertThat(result).contains("Line 30, Column 1");
    }

    @Test
    void format_warningsOnly_returnsValidWithWarnings() throws FormattingException {
        String sapXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkl:messages xmlns:chkl="http://www.sap.com/abapxml/checklist">
              <msg type="W" href="/sap/bc/adt/oo/classes/a/source/main#start=5,1">
                <shortText><txt>Unused variable</txt></shortText>
              </msg>
            </chkl:messages>
            """;

        String result = formatter.format(sapXml);

        assertThat(result).contains("Status: VALID (with warnings)");
        assertThat(result).contains("Warnings: 1");
        assertThat(result).contains("Errors: 0");
    }

    @Test
    void format_hrefWithoutFragment_usesLineAttribute() throws FormattingException {
        String sapXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkl:messages xmlns:chkl="http://www.sap.com/abapxml/checklist">
              <msg type="E" line="42" column="10" href="/sap/bc/adt/oo/classes/a/source/main">
                <shortText><txt>Some error</txt></shortText>
              </msg>
            </chkl:messages>
            """;

        String result = formatter.format(sapXml);

        assertThat(result).contains("Line 42, Column 10");
    }

    @Test
    void format_noHref_usesLineAttribute() throws FormattingException {
        String sapXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkl:messages xmlns:chkl="http://www.sap.com/abapxml/checklist">
              <msg type="E" line="100" column="5">
                <shortText><txt>Error without href</txt></shortText>
              </msg>
            </chkl:messages>
            """;

        String result = formatter.format(sapXml);

        assertThat(result).contains("Line 100, Column 5");
    }

    @Test
    void format_checkExecutedTrue_showsYes() throws FormattingException {
        String sapXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <chkl:messages xmlns:chkl="http://www.sap.com/abapxml/checklist">
              <chkl:properties checkExecuted="true" activationExecuted="false"/>
            </chkl:messages>
            """;

        String result = formatter.format(sapXml);

        assertThat(result).contains("Check Executed: Yes");
        assertThat(result).contains("Status: VALID");
    }
}
