package com.sapjco.mcp.util.xml;

import lombok.Getter;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Map;
import java.util.function.Function;

/**
 * Configuration for extracting a single field from an XML element.
 * Uses builder pattern for fluent configuration.
 *
 * <p>Example usage:
 * <pre>
 * FieldExtractor.builder("executionTimeMs")
 *     .fromAttribute("executionTime")
 *     .asDouble()
 *     .withUnitConversion("unit", Map.of("s", 1000.0, "ms", 1.0))
 *     .toLong()
 *     .defaultValue(0L)
 *     .build()
 * </pre>
 */
@Getter
public class FieldExtractor {

    private final String fieldName;
    private final SourceType sourceType;
    private final String sourceName;
    private final String sourceNamespace;
    private final String childTagName;
    private final String nestedChildTagName;
    private final ValueType valueType;
    private final String unitAttribute;
    private final Map<String, Double> unitMultipliers;
    private final ValueType finalType;
    private final Object defaultValue;

    /**
     * Type of source to extract from.
     */
    public enum SourceType {
        ATTRIBUTE,
        NAMESPACED_ATTRIBUTE,
        CHILD_TEXT,
        CHILD_ATTRIBUTE,
        NESTED_CHILD_ATTRIBUTE,
        NESTED_CHILD_TEXT
    }

    /**
     * Type of value conversion.
     */
    public enum ValueType {
        STRING,
        INTEGER,
        LONG,
        DOUBLE,
        BOOLEAN
    }

    private FieldExtractor(Builder builder) {
        this.fieldName = builder.fieldName;
        this.sourceType = builder.sourceType;
        this.sourceName = builder.sourceName;
        this.sourceNamespace = builder.sourceNamespace;
        this.childTagName = builder.childTagName;
        this.nestedChildTagName = builder.nestedChildTagName;
        this.valueType = builder.valueType;
        this.unitAttribute = builder.unitAttribute;
        this.unitMultipliers = builder.unitMultipliers;
        this.finalType = builder.finalType;
        this.defaultValue = builder.defaultValue;
    }

    /**
     * Extract the configured field value from an XML element.
     *
     * @param element The XML element to extract from
     * @return The extracted and converted value
     */
    public Object extract(Element element) {
        String rawValue = extractRawValue(element);

        if (rawValue == null || rawValue.isEmpty()) {
            return defaultValue;
        }

        // Convert to initial type
        Object value = convertToType(rawValue, valueType);

        // Apply unit conversion if configured
        if (unitAttribute != null && unitMultipliers != null && value instanceof Number) {
            // First try plain attribute
            String unit = element.getAttribute(unitAttribute);
            // If not found, iterate through all attributes and match by local name
            if (unit == null || unit.isEmpty()) {
                org.w3c.dom.NamedNodeMap attrs = element.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    org.w3c.dom.Node attr = attrs.item(i);
                    if (attr.getLocalName() != null && attr.getLocalName().equals(unitAttribute)) {
                        unit = attr.getNodeValue();
                        break;
                    }
                }
            }
            Double multiplier = unitMultipliers.get(unit);
            if (multiplier != null) {
                double numValue = ((Number) value).doubleValue();
                value = numValue * multiplier;
            }
        }

        // Convert to final type if different
        if (finalType != null && finalType != valueType) {
            value = convertNumericToType((Number) value, finalType);
        }

        return value;
    }

    private String extractRawValue(Element element) {
        switch (sourceType) {
            case ATTRIBUTE:
                // First try plain attribute (no namespace)
                String attrValue = element.getAttribute(sourceName);
                if (attrValue != null && !attrValue.isEmpty()) {
                    return attrValue;
                }
                // If not found, iterate through all attributes and match by local name
                org.w3c.dom.NamedNodeMap attrs = element.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    org.w3c.dom.Node attr = attrs.item(i);
                    if (attr.getLocalName() != null && attr.getLocalName().equals(sourceName)) {
                        return attr.getNodeValue();
                    }
                }
                return null;

            case NAMESPACED_ATTRIBUTE:
                String value = element.getAttributeNS(sourceNamespace, sourceName);
                if (value == null || value.isEmpty()) {
                    // Try prefixed form (adtcore:name)
                    value = element.getAttribute("adtcore:" + sourceName);
                }
                if (value == null || value.isEmpty()) {
                    // Try plain attribute
                    value = element.getAttribute(sourceName);
                }
                return value;

            case CHILD_TEXT:
                // Use namespace-agnostic search to match elements regardless of prefix
                NodeList children = element.getElementsByTagNameNS("*", sourceName);
                if (children.getLength() > 0) {
                    return children.item(0).getTextContent().trim();
                }
                // Fallback to non-namespaced search
                children = element.getElementsByTagName(sourceName);
                if (children.getLength() > 0) {
                    return children.item(0).getTextContent().trim();
                }
                return null;

            case CHILD_ATTRIBUTE:
                // Use namespace-agnostic search to match elements regardless of prefix
                NodeList childNodes = element.getElementsByTagNameNS("*", childTagName);
                if (childNodes.getLength() == 0) {
                    // Fallback to non-namespaced search
                    childNodes = element.getElementsByTagName(childTagName);
                }
                if (childNodes.getLength() > 0) {
                    Element childElem = (Element) childNodes.item(0);
                    if (sourceNamespace != null) {
                        String nsValue = childElem.getAttributeNS(sourceNamespace, sourceName);
                        if (nsValue == null || nsValue.isEmpty()) {
                            nsValue = childElem.getAttribute("adtcore:" + sourceName);
                        }
                        if (nsValue == null || nsValue.isEmpty()) {
                            nsValue = childElem.getAttribute(sourceName);
                        }
                        return nsValue;
                    }
                    return childElem.getAttribute(sourceName);
                }
                return null;

            case NESTED_CHILD_ATTRIBUTE:
                // Find first child, then find nested child within it
                // Use namespace-agnostic search to match elements regardless of prefix
                NodeList parentNodes = element.getElementsByTagNameNS("*", childTagName);
                if (parentNodes.getLength() == 0) {
                    // Fallback to non-namespaced search
                    parentNodes = element.getElementsByTagName(childTagName);
                }
                if (parentNodes.getLength() > 0) {
                    Element parentElem = (Element) parentNodes.item(0);
                    // Use namespace-agnostic search for nested child
                    NodeList nestedNodes = parentElem.getElementsByTagNameNS("*", nestedChildTagName);
                    if (nestedNodes.getLength() == 0) {
                        // Fallback to non-namespaced search
                        nestedNodes = parentElem.getElementsByTagName(nestedChildTagName);
                    }
                    if (nestedNodes.getLength() > 0) {
                        Element nestedElem = (Element) nestedNodes.item(0);
                        if (sourceNamespace != null) {
                            String nsValue = nestedElem.getAttributeNS(sourceNamespace, sourceName);
                            if (nsValue == null || nsValue.isEmpty()) {
                                nsValue = nestedElem.getAttribute("adtcore:" + sourceName);
                            }
                            if (nsValue == null || nsValue.isEmpty()) {
                                nsValue = nestedElem.getAttribute(sourceName);
                            }
                            return nsValue;
                        }
                        return nestedElem.getAttribute(sourceName);
                    }
                }
                return null;

            case NESTED_CHILD_TEXT:
                // Find first child, then find nested child and get its text content
                // Use namespace-agnostic search to match elements regardless of prefix
                NodeList parentTextNodes = element.getElementsByTagNameNS("*", childTagName);
                if (parentTextNodes.getLength() == 0) {
                    // Fallback to non-namespaced search
                    parentTextNodes = element.getElementsByTagName(childTagName);
                }
                if (parentTextNodes.getLength() > 0) {
                    Element parentTextElem = (Element) parentTextNodes.item(0);
                    // Use namespace-agnostic search for nested child
                    NodeList nestedTextNodes = parentTextElem.getElementsByTagNameNS("*", nestedChildTagName);
                    if (nestedTextNodes.getLength() == 0) {
                        // Fallback to non-namespaced search
                        nestedTextNodes = parentTextElem.getElementsByTagName(nestedChildTagName);
                    }
                    if (nestedTextNodes.getLength() > 0) {
                        return nestedTextNodes.item(0).getTextContent().trim();
                    }
                }
                return null;

            default:
                return null;
        }
    }

    private Object convertToType(String rawValue, ValueType type) {
        if (rawValue == null || rawValue.isEmpty()) {
            return getDefaultForType(type);
        }

        try {
            switch (type) {
                case STRING:
                    return rawValue;
                case INTEGER:
                    // Handle decimal strings by parsing as double first
                    if (rawValue.contains(".")) {
                        return (int) Double.parseDouble(rawValue.trim());
                    }
                    return Integer.parseInt(rawValue.trim());
                case LONG:
                    // Handle decimal strings by parsing as double first
                    if (rawValue.contains(".")) {
                        return (long) Double.parseDouble(rawValue.trim());
                    }
                    return Long.parseLong(rawValue.trim());
                case DOUBLE:
                    return Double.parseDouble(rawValue.trim());
                case BOOLEAN:
                    return "true".equalsIgnoreCase(rawValue.trim());
                default:
                    return rawValue;
            }
        } catch (NumberFormatException e) {
            return getDefaultForType(type);
        }
    }

    private Object convertNumericToType(Number value, ValueType type) {
        switch (type) {
            case INTEGER:
                return value.intValue();
            case LONG:
                return value.longValue();
            case DOUBLE:
                return value.doubleValue();
            default:
                return value;
        }
    }

    private Object getDefaultForType(ValueType type) {
        switch (type) {
            case STRING:
                return "";
            case INTEGER:
                return 0;
            case LONG:
                return 0L;
            case DOUBLE:
                return 0.0;
            case BOOLEAN:
                return false;
            default:
                return null;
        }
    }

    /**
     * Create a new builder for a field extractor.
     *
     * @param fieldName The name of the field in the result map
     * @return A new builder instance
     */
    public static Builder builder(String fieldName) {
        return new Builder(fieldName);
    }

    /**
     * Builder for FieldExtractor.
     */
    public static class Builder {
        private final String fieldName;
        private SourceType sourceType = SourceType.ATTRIBUTE;
        private String sourceName;
        private String sourceNamespace;
        private String childTagName;
        private String nestedChildTagName;
        private ValueType valueType = ValueType.STRING;
        private String unitAttribute;
        private Map<String, Double> unitMultipliers;
        private ValueType finalType;
        private Object defaultValue;

        private Builder(String fieldName) {
            this.fieldName = fieldName;
        }

        /**
         * Extract from a plain attribute.
         */
        public Builder fromAttribute(String attributeName) {
            this.sourceType = SourceType.ATTRIBUTE;
            this.sourceName = attributeName;
            return this;
        }

        /**
         * Extract from a namespaced attribute.
         */
        public Builder fromNamespacedAttribute(String namespace, String localName) {
            this.sourceType = SourceType.NAMESPACED_ATTRIBUTE;
            this.sourceNamespace = namespace;
            this.sourceName = localName;
            return this;
        }

        /**
         * Extract from child element text content.
         */
        public Builder fromChildText(String tagName) {
            this.sourceType = SourceType.CHILD_TEXT;
            this.sourceName = tagName;
            return this;
        }

        /**
         * Extract from a child element's attribute.
         */
        public Builder fromChildAttribute(String childTagName, String attributeName) {
            this.sourceType = SourceType.CHILD_ATTRIBUTE;
            this.childTagName = childTagName;
            this.sourceName = attributeName;
            return this;
        }

        /**
         * Extract from a child element's namespaced attribute.
         */
        public Builder fromChildAttribute(String childTagName, String namespace, String localName) {
            this.sourceType = SourceType.CHILD_ATTRIBUTE;
            this.childTagName = childTagName;
            this.sourceNamespace = namespace;
            this.sourceName = localName;
            return this;
        }

        /**
         * Extract from a nested child element's namespaced attribute.
         * Navigates: element -> childTagName -> nestedChildTagName -> attribute
         */
        public Builder fromNestedChildAttribute(String childTagName, String nestedChildTagName, String namespace, String localName) {
            this.sourceType = SourceType.NESTED_CHILD_ATTRIBUTE;
            this.childTagName = childTagName;
            this.nestedChildTagName = nestedChildTagName;
            this.sourceNamespace = namespace;
            this.sourceName = localName;
            return this;
        }

        /**
         * Extract text content from a nested child element.
         * Navigates: element -> childTagName -> nestedChildTagName -> textContent
         */
        public Builder fromNestedChildText(String childTagName, String nestedChildTagName) {
            this.sourceType = SourceType.NESTED_CHILD_TEXT;
            this.childTagName = childTagName;
            this.nestedChildTagName = nestedChildTagName;
            return this;
        }

        /**
         * Convert value as string (default).
         */
        public Builder asString() {
            this.valueType = ValueType.STRING;
            if (this.defaultValue == null) {
                this.defaultValue = "";
            }
            return this;
        }

        /**
         * Convert value as integer.
         */
        public Builder asInteger() {
            this.valueType = ValueType.INTEGER;
            if (this.defaultValue == null) {
                this.defaultValue = 0;
            }
            return this;
        }

        /**
         * Convert value as long.
         */
        public Builder asLong() {
            this.valueType = ValueType.LONG;
            if (this.defaultValue == null) {
                this.defaultValue = 0L;
            }
            return this;
        }

        /**
         * Convert value as double.
         */
        public Builder asDouble() {
            this.valueType = ValueType.DOUBLE;
            if (this.defaultValue == null) {
                this.defaultValue = 0.0;
            }
            return this;
        }

        /**
         * Convert value as boolean.
         */
        public Builder asBoolean() {
            this.valueType = ValueType.BOOLEAN;
            if (this.defaultValue == null) {
                this.defaultValue = false;
            }
            return this;
        }

        /**
         * Apply unit conversion based on unit attribute.
         *
         * @param unitAttribute The attribute name containing the unit
         * @param multipliers Map of unit values to multipliers
         */
        public Builder withUnitConversion(String unitAttribute, Map<String, Double> multipliers) {
            this.unitAttribute = unitAttribute;
            this.unitMultipliers = multipliers;
            return this;
        }

        /**
         * Convert final result to long (after unit conversion).
         */
        public Builder toLong() {
            this.finalType = ValueType.LONG;
            if (this.defaultValue == null || !(this.defaultValue instanceof Long)) {
                this.defaultValue = 0L;
            }
            return this;
        }

        /**
         * Convert final result to integer (after unit conversion).
         */
        public Builder toInteger() {
            this.finalType = ValueType.INTEGER;
            if (this.defaultValue == null || !(this.defaultValue instanceof Integer)) {
                this.defaultValue = 0;
            }
            return this;
        }

        /**
         * Set default value when source is empty or conversion fails.
         */
        public Builder defaultValue(Object value) {
            this.defaultValue = value;
            return this;
        }

        /**
         * Build the FieldExtractor.
         */
        public FieldExtractor build() {
            // Source name is not required for CHILD_ATTRIBUTE and NESTED_CHILD_TEXT
            if (sourceName == null
                && sourceType != SourceType.CHILD_ATTRIBUTE
                && sourceType != SourceType.NESTED_CHILD_TEXT) {
                throw new IllegalStateException("Source name must be specified");
            }
            return new FieldExtractor(this);
        }
    }
}
