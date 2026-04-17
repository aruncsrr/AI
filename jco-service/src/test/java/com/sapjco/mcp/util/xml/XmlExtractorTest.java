package com.sapjco.mcp.util.xml;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlExtractor.
 */
class XmlExtractorTest {

    @Test
    void extract_simpleElements() throws Exception {
        // Arrange
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <items>
                    <item name="Item1" value="100"/>
                    <item name="Item2" value="200"/>
                </items>
                """;

        ElementExtractor extractor = ElementExtractor.builder("item")
                .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
                .field(FieldExtractor.builder("value").fromAttribute("value").asInteger().build())
                .build();

        // Act
        List<Map<String, Object>> results = XmlExtractor.extract(xml, extractor);

        // Assert
        assertEquals(2, results.size());
        assertEquals("Item1", results.get(0).get("name"));
        assertEquals(100, results.get(0).get("value"));
        assertEquals("Item2", results.get(1).get("name"));
        assertEquals(200, results.get(1).get("value"));
    }

    @Test
    void extract_namespacedAttributes() throws Exception {
        // Arrange
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <root xmlns:adtcore="http://www.sap.com/adt/core">
                    <object adtcore:name="ZCL_TEST" adtcore:type="CLAS/OC"/>
                </root>
                """;

        ElementExtractor extractor = ElementExtractor.builder("object")
                .field(FieldExtractor.builder("name")
                        .fromNamespacedAttribute("http://www.sap.com/adt/core", "name")
                        .asString()
                        .build())
                .field(FieldExtractor.builder("type")
                        .fromNamespacedAttribute("http://www.sap.com/adt/core", "type")
                        .asString()
                        .build())
                .build();

        // Act
        List<Map<String, Object>> results = XmlExtractor.extract(xml, extractor);

        // Assert
        assertEquals(1, results.size());
        assertEquals("ZCL_TEST", results.get(0).get("name"));
        assertEquals("CLAS/OC", results.get(0).get("type"));
    }

    @Test
    void extract_emptyResults() throws Exception {
        // Arrange
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <root></root>
                """;

        ElementExtractor extractor = ElementExtractor.builder("nonexistent")
                .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
                .build();

        // Act
        List<Map<String, Object>> results = XmlExtractor.extract(xml, extractor);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void extractSingle_fromRoot() throws Exception {
        // Arrange
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <result status="success" count="42"/>
                """;

        List<FieldExtractor> extractors = List.of(
                FieldExtractor.builder("status").fromAttribute("status").asString().build(),
                FieldExtractor.builder("count").fromAttribute("count").asInteger().build()
        );

        // Act
        Map<String, Object> result = XmlExtractor.extractSingle(xml, extractors);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(42, result.get("count"));
    }

    @Test
    void extractSingle_missingFields_returnsDefaults() throws Exception {
        // Arrange
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <result/>
                """;

        List<FieldExtractor> extractors = List.of(
                FieldExtractor.builder("status").fromAttribute("status").asString().defaultValue("unknown").build(),
                FieldExtractor.builder("count").fromAttribute("count").asInteger().defaultValue(0).build()
        );

        // Act
        Map<String, Object> result = XmlExtractor.extractSingle(xml, extractors);

        // Assert
        assertEquals("unknown", result.get("status"));
        assertEquals(0, result.get("count"));
    }

    @Test
    void extractFromElement_findsFirstMatch() throws Exception {
        // Arrange
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <root>
                    <data value="first"/>
                    <data value="second"/>
                </root>
                """;

        List<FieldExtractor> extractors = List.of(
                FieldExtractor.builder("value").fromAttribute("value").asString().build()
        );

        // Act
        Map<String, Object> result = XmlExtractor.extractFromElement(xml, "data", extractors);

        // Assert
        assertEquals("first", result.get("value"));
    }

    @Test
    void extractWithChildren_nestedStructure() throws Exception {
        // Arrange
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <testClasses>
                    <testClass name="LTCL_TEST">
                        <testMethod name="TEST_ONE"/>
                        <testMethod name="TEST_TWO"/>
                    </testClass>
                </testClasses>
                """;

        ElementExtractor parentExtractor = ElementExtractor.builder("testClass")
                .field(FieldExtractor.builder("name").fromAttribute("name").asString().build())
                .build();

        ElementExtractor childExtractor = ElementExtractor.builder("testMethod")
                .field(FieldExtractor.builder("methodName").fromAttribute("name").asString().build())
                .build();

        // Act
        List<Map<String, Object>> results = XmlExtractor.extractWithChildren(
                xml, parentExtractor, "methods", childExtractor);

        // Assert
        assertEquals(1, results.size());
        assertEquals("LTCL_TEST", results.get(0).get("name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) results.get(0).get("methods");
        assertEquals(2, methods.size());
        assertEquals("TEST_ONE", methods.get(0).get("methodName"));
        assertEquals("TEST_TWO", methods.get(1).get("methodName"));
    }

    @Test
    void parseXml_valid() throws Exception {
        // Act & Assert - should not throw
        var doc = XmlExtractor.parseXml("<root><child/></root>");
        assertNotNull(doc);
        assertEquals("root", doc.getDocumentElement().getTagName());
    }

    @Test
    void getElements_findsAll() throws Exception {
        // Arrange
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <root>
                    <item id="1"/>
                    <item id="2"/>
                    <item id="3"/>
                </root>
                """;

        // Act
        List<Element> elements = XmlExtractor.getElements(xml, "item");

        // Assert
        assertEquals(3, elements.size());
    }

    @Test
    void extractPropertyValue_findsEntryByKey() throws Exception {
        // Arrange
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <element>
                    <properties>
                        <entry key="ddicIsKey">true</entry>
                        <entry key="ddicDataElement">MANDT</entry>
                    </properties>
                </element>
                """;

        var doc = XmlExtractor.parseXml(xml);
        Element element = doc.getDocumentElement();

        // Act
        String isKey = XmlExtractor.extractPropertyValue(element, "properties", "entry", "key", "ddicIsKey");
        String dataElement = XmlExtractor.extractPropertyValue(element, "properties", "entry", "key", "ddicDataElement");
        String missing = XmlExtractor.extractPropertyValue(element, "properties", "entry", "key", "nonexistent");

        // Assert
        assertEquals("true", isKey);
        assertEquals("MANDT", dataElement);
        assertEquals("", missing);
    }
}
