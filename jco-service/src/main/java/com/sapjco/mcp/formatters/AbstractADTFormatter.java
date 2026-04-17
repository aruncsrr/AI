package com.sapjco.mcp.formatters;

import com.sapjco.mcp.util.FormattingUtils;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for ADT formatters providing common XML parsing utilities.
 * Formatters can extend this class to leverage shared functionality.
 */
@Slf4j
public abstract class AbstractADTFormatter implements ADTFormatter {

    /**
     * Parse raw XML string into a DOM Document.
     *
     * @param rawResponse The raw XML response
     * @return Parsed Document
     * @throws FormattingException if parsing fails
     */
    protected Document parseXml(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        try {
            return XmlExtractor.parseXml(rawResponse);
        } catch (Exception e) {
            throw FormattingException.malformedXml(getToolName(), e);
        }
    }

    /**
     * Get elements by tag name (namespace-agnostic).
     *
     * @param doc The DOM Document
     * @param tagName The tag name to find
     * @return List of matching elements
     */
    protected List<Element> getElementsByTagName(Document doc, String tagName) {
        List<Element> elements = new ArrayList<>();
        NodeList nodeList = doc.getElementsByTagNameNS("*", tagName);
        for (int i = 0; i < nodeList.getLength(); i++) {
            elements.add((Element) nodeList.item(i));
        }
        return elements;
    }

    /**
     * Get elements by tag name from a parent element.
     *
     * @param parent The parent element
     * @param tagName The tag name to find
     * @return List of matching child elements
     */
    protected List<Element> getChildElements(Element parent, String tagName) {
        List<Element> elements = new ArrayList<>();
        NodeList nodeList = parent.getElementsByTagNameNS("*", tagName);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element elem = (Element) nodeList.item(i);
            // Only include direct children or descendants of this parent
            if (isDescendantOf(elem, parent)) {
                elements.add(elem);
            }
        }
        return elements;
    }

    /**
     * Check if an element is a descendant of a parent.
     */
    private boolean isDescendantOf(Element elem, Element parent) {
        org.w3c.dom.Node current = elem.getParentNode();
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }

    /**
     * Get attribute value from an element (namespace-agnostic).
     *
     * @param element The element
     * @param attrName The attribute name
     * @return Attribute value or empty string if not found
     */
    protected String getAttribute(Element element, String attrName) {
        String value = element.getAttribute(attrName);
        if (value.isEmpty()) {
            // Try with common ADT namespace
            value = element.getAttributeNS("http://www.sap.com/adt/core", attrName);
        }
        return value;
    }

    /**
     * Get namespaced attribute value from an element.
     *
     * @param element The element
     * @param namespace The namespace URI
     * @param attrName The attribute local name
     * @return Attribute value or empty string if not found
     */
    protected String getNamespacedAttribute(Element element, String namespace, String attrName) {
        return element.getAttributeNS(namespace, attrName);
    }

    /**
     * Get text content of a child element.
     *
     * @param parent The parent element
     * @param childTagName The child tag name
     * @return Text content or empty string if not found
     */
    protected String getChildText(Element parent, String childTagName) {
        NodeList children = parent.getElementsByTagNameNS("*", childTagName);
        if (children.getLength() > 0) {
            return children.item(0).getTextContent().trim();
        }
        return "";
    }

    /**
     * Truncate text with ellipsis if too long.
     * Delegates to FormattingUtils.
     */
    protected String truncate(String text, int maxLength) {
        return FormattingUtils.truncate(text, maxLength);
    }

    /**
     * Format a duration in milliseconds.
     * Delegates to FormattingUtils.
     */
    protected String formatDuration(long timeMs, String unit) {
        return FormattingUtils.formatDuration(timeMs, unit);
    }

    /**
     * Create a horizontal divider line.
     *
     * @param length Number of characters
     * @return String of repeated dash characters
     */
    protected String divider(int length) {
        return "-".repeat(length);
    }

    /**
     * Create a horizontal divider with box-drawing character.
     *
     * @param length Number of characters
     * @return String of repeated line characters
     */
    protected String boxDivider(int length) {
        return "\u2500".repeat(length);  // Unicode box-drawing horizontal line
    }
}
