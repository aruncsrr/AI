package com.sapjco.mcp.formatters.transport;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransportRequestsFormatter.
 */
class TransportRequestsFormatterTest {

    private TransportRequestsFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new TransportRequestsFormatter();
    }

    @Test
    void getToolName_returnsGetTransportRequests() {
        assertEquals("GetTransportRequests", formatter.getToolName());
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
    void format_noTransports_showsNoTransportsMessage() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <transportCheck>
                <REQUESTS/>
            </transportCheck>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Transport Requests"));
        assertTrue(result.contains("No transport requests found"));
        assertTrue(result.contains("local package"));
    }

    @Test
    void format_singleTransport_showsTransport() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <transportCheck>
                <REQUESTS>
                    <CTS_REQUEST>
                        <REQ_HEADER>
                            <TRKORR>NPLK900001</TRKORR>
                            <TRFUNCTION>K</TRFUNCTION>
                            <TRSTATUS>D</TRSTATUS>
                            <AS4USER>D052860</AS4USER>
                            <AS4TEXT>Test transport request</AS4TEXT>
                        </REQ_HEADER>
                    </CTS_REQUEST>
                </REQUESTS>
            </transportCheck>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 1 transport request"));
        assertTrue(result.contains("NPLK900001"));
        assertTrue(result.contains("Workbench"));
        assertTrue(result.contains("Modif"));
        assertTrue(result.contains("D052860"));
        assertTrue(result.contains("Test transport request"));
    }

    @Test
    void format_multipleTransports_showsAllTransports() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <transportCheck>
                <REQUESTS>
                    <CTS_REQUEST>
                        <REQ_HEADER>
                            <TRKORR>NPLK900001</TRKORR>
                            <TRFUNCTION>K</TRFUNCTION>
                            <TRSTATUS>D</TRSTATUS>
                            <AS4USER>USER1</AS4USER>
                            <AS4TEXT>First transport</AS4TEXT>
                        </REQ_HEADER>
                    </CTS_REQUEST>
                    <CTS_REQUEST>
                        <REQ_HEADER>
                            <TRKORR>NPLK900002</TRKORR>
                            <TRFUNCTION>W</TRFUNCTION>
                            <TRSTATUS>R</TRSTATUS>
                            <AS4USER>USER2</AS4USER>
                            <AS4TEXT>Second transport</AS4TEXT>
                        </REQ_HEADER>
                    </CTS_REQUEST>
                </REQUESTS>
            </transportCheck>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Found 2 transport requests"));
        assertTrue(result.contains("NPLK900001"));
        assertTrue(result.contains("NPLK900002"));
        assertTrue(result.contains("Workbench"));
        assertTrue(result.contains("Custom"));
        assertTrue(result.contains("Released"));
    }

    @Test
    void format_withObjectContext_showsObjectInfo() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <transportCheck>
                <REQUESTS>
                    <CTS_REQUEST>
                        <REQ_HEADER>
                            <TRKORR>DEVK123456</TRKORR>
                        </REQ_HEADER>
                    </CTS_REQUEST>
                </REQUESTS>
            </transportCheck>
            """;

        String result = formatter.format(xml, "ZCL_MY_CLASS", "class");

        assertTrue(result.contains("ZCL_MY_CLASS"));
        assertTrue(result.contains("(class)"));
        assertTrue(result.contains("DEVK123456"));
    }

    @Test
    void format_protectedStatus_showsProtect() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <transportCheck>
                <REQUESTS>
                    <CTS_REQUEST>
                        <REQ_HEADER>
                            <TRKORR>PRDK900001</TRKORR>
                            <TRSTATUS>L</TRSTATUS>
                        </REQ_HEADER>
                    </CTS_REQUEST>
                </REQUESTS>
            </transportCheck>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Protect"));
    }

    @Test
    void format_showsLegend() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <transportCheck>
                <REQUESTS>
                    <CTS_REQUEST>
                        <REQ_HEADER>
                            <TRKORR>NPLK900001</TRKORR>
                        </REQ_HEADER>
                    </CTS_REQUEST>
                </REQUESTS>
            </transportCheck>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Legend:"));
        assertTrue(result.contains("K=Workbench"));
        assertTrue(result.contains("W=Customizing"));
        assertTrue(result.contains("D=Modifiable"));
        assertTrue(result.contains("R=Released"));
        assertTrue(result.contains("L=Protected"));
    }

    @Test
    void format_longDescription_truncates() throws FormattingException {
        String longText = "A".repeat(100);
        String xml = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <transportCheck>
                <REQUESTS>
                    <CTS_REQUEST>
                        <REQ_HEADER>
                            <TRKORR>NPLK900001</TRKORR>
                            <AS4TEXT>%s</AS4TEXT>
                        </REQ_HEADER>
                    </CTS_REQUEST>
                </REQUESTS>
            </transportCheck>
            """, longText);

        String result = formatter.format(xml);

        // Should handle gracefully without showing full 100-char string
        assertTrue(result.contains("NPLK900001"));
    }

    @Test
    void format_unknownFunction_showsRawValue() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <transportCheck>
                <REQUESTS>
                    <CTS_REQUEST>
                        <REQ_HEADER>
                            <TRKORR>NPLK900001</TRKORR>
                            <TRFUNCTION>X</TRFUNCTION>
                        </REQ_HEADER>
                    </CTS_REQUEST>
                </REQUESTS>
            </transportCheck>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("X"));
    }

    @Test
    void format_unknownStatus_showsRawValue() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <transportCheck>
                <REQUESTS>
                    <CTS_REQUEST>
                        <REQ_HEADER>
                            <TRKORR>NPLK900001</TRKORR>
                            <TRSTATUS>Z</TRSTATUS>
                        </REQ_HEADER>
                    </CTS_REQUEST>
                </REQUESTS>
            </transportCheck>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Z"));
    }
}
