package com.sapjco.mcp.util.xml;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generic XML extraction engine that uses FieldExtractor and ElementExtractor configurations.
 *
 * <p>Example usage:
 * <pre>
 * // Extract list of elements
 * List&lt;Map&lt;String, Object&gt;&gt; results = XmlExtractor.extract(xml, elementConfig);
 *
 * // Extract single element's fields from root
 * Map&lt;String, Object&gt; result = XmlExtractor.extractSingle(xml, fieldList);
 * </pre>
 */
@Slf4j
public class XmlExtractor {

    /**
     * Extract a list of elements matching the extractor configuration.
     *
     * @param xml The XML string to parse
     * @param extractor The element extractor configuration
     * @return List of maps containing extracted field values
     */
    public static List<Map<String, Object>> extract(String xml, ElementExtractor extractor) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            Document doc = parseXml(xml);
            // Use getElementsByTagNameNS with "*" to match any namespace
            NodeList elements = doc.getElementsByTagNameNS("*", extractor.getTagName());

            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);

                // Apply filter if configured
                if (!extractor.shouldInclude(element)) {
                    continue;
                }

                Map<String, Object> data = extractFields(element, extractor.getFieldExtractors());
                results.add(data);
            }
        } catch (Exception e) {
            log.error("Failed to extract elements from XML", e);
        }

        return results;
    }

    /**
     * Extract elements with nested child elements.
     *
     * @param xml The XML string to parse
     * @param parentExtractor Configuration for parent elements
     * @param childKey The key to store child elements under
     * @param childExtractor Configuration for child elements
     * @return List of parent maps with nested child lists
     */
    public static List<Map<String, Object>> extractWithChildren(
            String xml,
            ElementExtractor parentExtractor,
            String childKey,
            ElementExtractor childExtractor) {

        List<Map<String, Object>> results = new ArrayList<>();

        try {
            Document doc = parseXml(xml);
            // Use getElementsByTagNameNS with "*" to match any namespace
            NodeList parentElements = doc.getElementsByTagNameNS("*", parentExtractor.getTagName());

            for (int i = 0; i < parentElements.getLength(); i++) {
                Element parentElement = (Element) parentElements.item(i);

                if (!parentExtractor.shouldInclude(parentElement)) {
                    continue;
                }

                // Extract parent fields
                Map<String, Object> parentData = extractFields(parentElement, parentExtractor.getFieldExtractors());

                // Extract children
                List<Map<String, Object>> children = new ArrayList<>();
                // Use getElementsByTagNameNS with "*" to match any namespace
                NodeList childElements = parentElement.getElementsByTagNameNS("*", childExtractor.getTagName());

                for (int j = 0; j < childElements.getLength(); j++) {
                    Element childElement = (Element) childElements.item(j);

                    // Only process direct children (not nested)
                    if (childElement.getParentNode() != parentElement) {
                        continue;
                    }

                    if (!childExtractor.shouldInclude(childElement)) {
                        continue;
                    }

                    Map<String, Object> childData = extractFields(childElement, childExtractor.getFieldExtractors());
                    children.add(childData);
                }

                parentData.put(childKey, children);
                results.add(parentData);
            }
        } catch (Exception e) {
            log.error("Failed to extract elements with children from XML", e);
        }

        return results;
    }

    /**
     * Extract fields from the root element of the XML.
     *
     * @param xml The XML string to parse
     * @param fieldExtractors List of field extractors to apply
     * @return Map containing extracted field values
     */
    public static Map<String, Object> extractSingle(String xml, List<FieldExtractor> fieldExtractors) {
        try {
            Document doc = parseXml(xml);
            Element root = doc.getDocumentElement();
            return extractFields(root, fieldExtractors);
        } catch (Exception e) {
            log.error("Failed to extract fields from XML root", e);
            // Return map with default values
            Map<String, Object> defaults = new HashMap<>();
            for (FieldExtractor fe : fieldExtractors) {
                defaults.put(fe.getFieldName(), fe.getDefaultValue());
            }
            return defaults;
        }
    }

    /**
     * Extract fields from the first element with the given tag name.
     *
     * @param xml The XML string to parse
     * @param elementTagName The tag name of the element to extract from
     * @param fieldExtractors List of field extractors to apply
     * @return Map containing extracted field values, or empty map if element not found
     */
    public static Map<String, Object> extractFromElement(String xml, String elementTagName, List<FieldExtractor> fieldExtractors) {
        try {
            Document doc = parseXml(xml);
            NodeList elements = doc.getElementsByTagNameNS("*", elementTagName);

            if (elements.getLength() > 0) {
                Element element = (Element) elements.item(0);
                return extractFields(element, fieldExtractors);
            }

            // Return map with default values if element not found
            Map<String, Object> defaults = new HashMap<>();
            for (FieldExtractor fe : fieldExtractors) {
                defaults.put(fe.getFieldName(), fe.getDefaultValue());
            }
            return defaults;
        } catch (Exception e) {
            log.error("Failed to extract fields from element {}", elementTagName, e);
            Map<String, Object> defaults = new HashMap<>();
            for (FieldExtractor fe : fieldExtractors) {
                defaults.put(fe.getFieldName(), fe.getDefaultValue());
            }
            return defaults;
        }
    }

    /**
     * Extract fields from a specific element.
     *
     * @param element The XML element
     * @param fieldExtractors List of field extractors
     * @return Map of field name to extracted value
     */
    public static Map<String, Object> extractFields(Element element, List<FieldExtractor> fieldExtractors) {
        Map<String, Object> data = new LinkedHashMap<>();

        for (FieldExtractor fe : fieldExtractors) {
            Object value = fe.extract(element);
            data.put(fe.getFieldName(), value);
        }

        return data;
    }

    /**
     * Parse XML string into a Document.
     *
     * @param xml The XML string
     * @return Parsed Document
     */
    public static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Get all elements from document with a specific tag name.
     *
     * @param xml The XML string
     * @param tagName The tag name to find
     * @return List of matching elements
     */
    public static List<Element> getElements(String xml, String tagName) {
        List<Element> elements = new ArrayList<>();
        try {
            Document doc = parseXml(xml);
            // Use getElementsByTagNameNS with "*" to match any namespace
            NodeList nodeList = doc.getElementsByTagNameNS("*", tagName);
            for (int i = 0; i < nodeList.getLength(); i++) {
                elements.add((Element) nodeList.item(i));
            }
        } catch (Exception e) {
            log.error("Failed to get elements from XML", e);
        }
        return elements;
    }

    /**
     * Extract a property value from a properties/entry pattern.
     * Common in ABAP element info XML where properties are stored as:
     * <pre>
     * &lt;properties&gt;
     *   &lt;entry key="ddicIsKey"&gt;true&lt;/entry&gt;
     *   &lt;entry key="ddicDataElement"&gt;s_mandt&lt;/entry&gt;
     * &lt;/properties&gt;
     * </pre>
     *
     * @param element The element containing the properties structure
     * @param propertiesTagName The tag name for the properties container (usually "properties")
     * @param entryTagName The tag name for each entry (usually "entry")
     * @param keyAttrName The attribute name containing the key (usually "key")
     * @param keyValue The specific key to find (e.g., "ddicIsKey", "ddicDataElement")
     * @return The text content of the matching entry, or empty string if not found
     */
    public static String extractPropertyValue(Element element, String propertiesTagName, String entryTagName, String keyAttrName, String keyValue) {
        // Use namespace-agnostic search for properties element
        NodeList propertiesNodes = element.getElementsByTagNameNS("*", propertiesTagName);
        if (propertiesNodes.getLength() == 0) {
            // Fallback to non-namespaced search
            propertiesNodes = element.getElementsByTagName(propertiesTagName);
        }

        if (propertiesNodes.getLength() > 0) {
            Element propertiesElem = (Element) propertiesNodes.item(0);
            // Use namespace-agnostic search for entry elements
            NodeList entryNodes = propertiesElem.getElementsByTagNameNS("*", entryTagName);
            if (entryNodes.getLength() == 0) {
                // Fallback to non-namespaced search
                entryNodes = propertiesElem.getElementsByTagName(entryTagName);
            }

            for (int i = 0; i < entryNodes.getLength(); i++) {
                Element entry = (Element) entryNodes.item(i);

                // Try namespace-aware attribute first
                String keyAttr = entry.getAttributeNS("*", keyAttrName);
                if (keyAttr == null || keyAttr.isEmpty()) {
                    // Fallback to plain attribute
                    keyAttr = entry.getAttribute(keyAttrName);
                }

                // Also try iterating attributes by local name
                if (keyAttr == null || keyAttr.isEmpty()) {
                    org.w3c.dom.NamedNodeMap attrs = entry.getAttributes();
                    for (int j = 0; j < attrs.getLength(); j++) {
                        org.w3c.dom.Node attr = attrs.item(j);
                        if (attr.getLocalName() != null && attr.getLocalName().equals(keyAttrName)) {
                            keyAttr = attr.getNodeValue();
                            break;
                        }
                    }
                }

                if (keyValue.equals(keyAttr)) {
                    return entry.getTextContent().trim();
                }
            }
        }
        return "";
    }
}
