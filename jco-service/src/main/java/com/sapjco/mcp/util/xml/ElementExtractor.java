package com.sapjco.mcp.util.xml;

import lombok.Getter;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Configuration for extracting multiple elements from XML with field extractors.
 * Uses builder pattern for fluent configuration.
 *
 * <p>Example usage:
 * <pre>
 * ElementExtractor.builder("testMethod")
 *     .field(FieldExtractor.builder("name").fromNamespacedAttribute(ADT_NS, "name").asString().build())
 *     .field(FieldExtractor.builder("executionTimeMs").fromAttribute("executionTime").asDouble()
 *            .withUnitConversion("unit", Map.of("s", 1000.0)).toLong().build())
 *     .filter(elem -> !"DYNP".equals(elem.getAttribute("stackType")))
 *     .build()
 * </pre>
 */
@Getter
public class ElementExtractor {

    private final String tagName;
    private final List<FieldExtractor> fieldExtractors;
    private final Predicate<Element> filter;

    private ElementExtractor(Builder builder) {
        this.tagName = builder.tagName;
        this.fieldExtractors = new ArrayList<>(builder.fieldExtractors);
        this.filter = builder.filter;
    }

    /**
     * Check if an element should be included (passes the filter).
     *
     * @param element The element to check
     * @return true if element should be included
     */
    public boolean shouldInclude(Element element) {
        return filter == null || filter.test(element);
    }

    /**
     * Create a new builder for an element extractor.
     *
     * @param tagName The tag name of elements to extract
     * @return A new builder instance
     */
    public static Builder builder(String tagName) {
        return new Builder(tagName);
    }

    /**
     * Builder for ElementExtractor.
     */
    public static class Builder {
        private final String tagName;
        private final List<FieldExtractor> fieldExtractors = new ArrayList<>();
        private Predicate<Element> filter;

        private Builder(String tagName) {
            this.tagName = tagName;
        }

        /**
         * Add a field extractor for this element type.
         */
        public Builder field(FieldExtractor extractor) {
            this.fieldExtractors.add(extractor);
            return this;
        }

        /**
         * Add multiple field extractors.
         */
        public Builder fields(List<FieldExtractor> extractors) {
            this.fieldExtractors.addAll(extractors);
            return this;
        }

        /**
         * Set a filter predicate for elements.
         * Elements that don't pass the filter are skipped.
         */
        public Builder filter(Predicate<Element> filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Build the ElementExtractor.
         */
        public ElementExtractor build() {
            if (tagName == null || tagName.isEmpty()) {
                throw new IllegalStateException("Tag name must be specified");
            }
            return new ElementExtractor(this);
        }
    }
}
