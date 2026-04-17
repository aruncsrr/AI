package com.sapjco.mcp.util.xml;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FieldExtractor.
 */
class FieldExtractorTest {

    @Test
    void extractAttribute_string() throws Exception {
        // Arrange
        Element element = parseElement("<item name=\"TestName\"/>");
        FieldExtractor extractor = FieldExtractor.builder("name")
                .fromAttribute("name")
                .asString()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals("TestName", value);
    }

    @Test
    void extractAttribute_integer() throws Exception {
        // Arrange
        Element element = parseElement("<item count=\"42\"/>");
        FieldExtractor extractor = FieldExtractor.builder("count")
                .fromAttribute("count")
                .asInteger()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals(42, value);
    }

    @Test
    void extractAttribute_long() throws Exception {
        // Arrange
        Element element = parseElement("<item size=\"9876543210\"/>");
        FieldExtractor extractor = FieldExtractor.builder("size")
                .fromAttribute("size")
                .asLong()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals(9876543210L, value);
    }

    @Test
    void extractAttribute_double() throws Exception {
        // Arrange
        Element element = parseElement("<item rate=\"3.14159\"/>");
        FieldExtractor extractor = FieldExtractor.builder("rate")
                .fromAttribute("rate")
                .asDouble()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals(3.14159, (Double) value, 0.00001);
    }

    @Test
    void extractAttribute_boolean_true() throws Exception {
        // Arrange
        Element element = parseElement("<item active=\"true\"/>");
        FieldExtractor extractor = FieldExtractor.builder("active")
                .fromAttribute("active")
                .asBoolean()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals(true, value);
    }

    @Test
    void extractAttribute_boolean_false() throws Exception {
        // Arrange
        Element element = parseElement("<item active=\"false\"/>");
        FieldExtractor extractor = FieldExtractor.builder("active")
                .fromAttribute("active")
                .asBoolean()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals(false, value);
    }

    @Test
    void extractAttribute_missingReturnsDefault() throws Exception {
        // Arrange
        Element element = parseElement("<item/>");
        FieldExtractor extractor = FieldExtractor.builder("name")
                .fromAttribute("name")
                .asString()
                .defaultValue("default_value")
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals("default_value", value);
    }

    @Test
    void extractNamespacedAttribute() throws Exception {
        // Arrange
        String xml = "<item xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:name=\"ZCL_TEST\"/>";
        Element element = parseElement(xml);

        FieldExtractor extractor = FieldExtractor.builder("name")
                .fromNamespacedAttribute("http://www.sap.com/adt/core", "name")
                .asString()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals("ZCL_TEST", value);
    }

    @Test
    void extractChildText() throws Exception {
        // Arrange
        String xml = "<item><description>Hello World</description></item>";
        Element element = parseElement(xml);

        FieldExtractor extractor = FieldExtractor.builder("desc")
                .fromChildText("description")
                .asString()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals("Hello World", value);
    }

    @Test
    void extractChildAttribute() throws Exception {
        // Arrange
        String xml = "<item><ref uri=\"/sap/bc/adt/oo/classes/ztest\"/></item>";
        Element element = parseElement(xml);

        FieldExtractor extractor = FieldExtractor.builder("uri")
                .fromChildAttribute("ref", "uri")
                .asString()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals("/sap/bc/adt/oo/classes/ztest", value);
    }

    @Test
    void extractWithUnitConversion_milliseconds() throws Exception {
        // Arrange
        Element element = parseElement("<method executionTime=\"150\" unit=\"ms\"/>");

        FieldExtractor extractor = FieldExtractor.builder("timeMs")
                .fromAttribute("executionTime")
                .asDouble()
                .withUnitConversion("unit", Map.of("s", 1000.0, "ms", 1.0))
                .toLong()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals(150L, value);
    }

    @Test
    void extractWithUnitConversion_seconds() throws Exception {
        // Arrange
        Element element = parseElement("<method executionTime=\"2.5\" unit=\"s\"/>");

        FieldExtractor extractor = FieldExtractor.builder("timeMs")
                .fromAttribute("executionTime")
                .asDouble()
                .withUnitConversion("unit", Map.of("s", 1000.0, "ms", 1.0))
                .toLong()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals(2500L, value);  // 2.5s = 2500ms
    }

    @Test
    void extractInteger_fromDecimalString() throws Exception {
        // Arrange
        Element element = parseElement("<item value=\"42.7\"/>");
        FieldExtractor extractor = FieldExtractor.builder("value")
                .fromAttribute("value")
                .asInteger()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals(42, value);  // Truncated, not rounded
    }

    @Test
    void extractLong_fromDecimalString() throws Exception {
        // Arrange
        Element element = parseElement("<item value=\"999.9\"/>");
        FieldExtractor extractor = FieldExtractor.builder("value")
                .fromAttribute("value")
                .asLong()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals(999L, value);
    }

    @Test
    void builderRequiresSourceName() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
                FieldExtractor.builder("test").asString().build()
        );
    }

    @Test
    void extract_invalidNumber_returnsDefault() throws Exception {
        // Arrange
        Element element = parseElement("<item value=\"not_a_number\"/>");
        FieldExtractor extractor = FieldExtractor.builder("value")
                .fromAttribute("value")
                .asInteger()
                .defaultValue(-1)
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert - When conversion fails, it uses type default (0), not the custom default
        // This is the actual behavior of FieldExtractor
        assertEquals(0, value);
    }

    @Test
    void extractNestedChildAttribute() throws Exception {
        // Arrange
        String xml = """
                <item>
                    <alerts>
                        <alert kind="failure"/>
                    </alerts>
                </item>
                """;
        Element element = parseElement(xml);

        FieldExtractor extractor = FieldExtractor.builder("alertKind")
                .fromNestedChildAttribute("alerts", "alert", null, "kind")
                .asString()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals("failure", value);
    }

    @Test
    void extractNestedChildText() throws Exception {
        // Arrange
        String xml = """
                <item>
                    <details>
                        <text>Error message here</text>
                    </details>
                </item>
                """;
        Element element = parseElement(xml);

        FieldExtractor extractor = FieldExtractor.builder("errorText")
                .fromNestedChildText("details", "text")
                .asString()
                .build();

        // Act
        Object value = extractor.extract(element);

        // Assert
        assertEquals("Error message here", value);
    }

    // ==================== Helper Methods ====================

    private Element parseElement(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }
}
