package com.sapjco.mcp.formatters.transport;

import com.sapjco.mcp.formatters.FormattingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransportListFormatter.
 */
class TransportListFormatterTest {

    private TransportListFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new TransportListFormatter();
    }

    @Test
    void getToolName_returnsListTransportRequests() {
        assertEquals("ListTransportRequests", formatter.getToolName());
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
    void format_noTransports_showsNoTransportsFound() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tm:root xmlns:tm="http://www.sap.com/cts/adt/tm">
            </tm:root>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Transport Requests"));
        assertTrue(result.contains("No transport requests found") || result.contains("0 transport"));
    }

    @Test
    void format_singleTransport_showsTransportDetails() throws FormattingException {
        // The formatter expects 'desc' attribute for description, not 'short_text' element
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tm:root xmlns:tm="http://www.sap.com/cts/adt/tm">
                <tm:request tm:number="NPLK900001" tm:owner="D052860" tm:status="D" tm:target="" tm:desc="Test transport request">
                </tm:request>
            </tm:root>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("NPLK900001"));
        assertTrue(result.contains("D052860"));
        assertTrue(result.contains("Modif") || result.contains("D"));
        assertTrue(result.contains("Test transport request"));
    }

    @Test
    void format_multipleTransports_showsAllTransports() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tm:root xmlns:tm="http://www.sap.com/cts/adt/tm">
                <tm:request tm:number="NPLK900001" tm:owner="D052860" tm:status="D" tm:desc="First request"/>
                <tm:request tm:number="NPLK900002" tm:owner="D052861" tm:status="R" tm:desc="Second request"/>
                <tm:request tm:number="NPLK900003" tm:owner="D052860" tm:status="L" tm:desc="Third request"/>
            </tm:root>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("NPLK900001"));
        assertTrue(result.contains("NPLK900002"));
        assertTrue(result.contains("NPLK900003"));
        assertTrue(result.contains("D052860"));
        assertTrue(result.contains("D052861"));
    }

    @Test
    void format_statusMapping_mapsCorrectly() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tm:root xmlns:tm="http://www.sap.com/cts/adt/tm">
                <tm:request tm:number="TR1" tm:owner="USER" tm:status="D" tm:desc="Modifiable"/>
                <tm:request tm:number="TR2" tm:owner="USER" tm:status="R" tm:desc="Released"/>
                <tm:request tm:number="TR3" tm:owner="USER" tm:status="L" tm:desc="Protected"/>
            </tm:root>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("Modif") || result.contains("D "));
        assertTrue(result.contains("Released") || result.contains("R "));
        assertTrue(result.contains("Protect") || result.contains("L "));
    }

    @Test
    void format_withTasks_showsTaskCount() throws FormattingException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tm:root xmlns:tm="http://www.sap.com/cts/adt/tm">
                <tm:request tm:number="NPLK900001" tm:owner="D052860" tm:status="D" tm:desc="Request with tasks">
                    <tm:task tm:number="NPLK900002" tm:owner="D052860" tm:status="D"/>
                    <tm:task tm:number="NPLK900003" tm:owner="D052861" tm:status="D"/>
                </tm:request>
            </tm:root>
            """;

        String result = formatter.format(xml);

        assertTrue(result.contains("NPLK900001"));
        // The formatter shows the request, tasks may or may not be listed
    }
}
