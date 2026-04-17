package com.sapjco.mcp.mcp;

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.*;

/**
 * Builder utility for creating MCP Tool input schemas.
 * Makes it easier to define complex tool schemas with multiple parameters.
 */
public class ToolSchemaBuilder {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();

    /**
     * Create a new schema builder.
     */
    public static ToolSchemaBuilder builder() {
        return new ToolSchemaBuilder();
    }

    /**
     * Add a required string parameter.
     */
    public ToolSchemaBuilder requiredString(String name, String description) {
        properties.put(name, Map.of(
            "type", "string",
            "description", description
        ));
        required.add(name);
        return this;
    }

    /**
     * Add an optional string parameter.
     */
    public ToolSchemaBuilder optionalString(String name, String description) {
        properties.put(name, Map.of(
            "type", "string",
            "description", description
        ));
        return this;
    }

    /**
     * Add an optional string parameter with default value.
     */
    public ToolSchemaBuilder optionalString(String name, String description, String defaultValue) {
        properties.put(name, Map.of(
            "type", "string",
            "description", description,
            "default", defaultValue
        ));
        return this;
    }

    /**
     * Add a required string enum parameter.
     */
    public ToolSchemaBuilder requiredEnum(String name, String description, List<String> values) {
        properties.put(name, Map.of(
            "type", "string",
            "description", description,
            "enum", values
        ));
        required.add(name);
        return this;
    }

    /**
     * Add an optional string enum parameter.
     */
    public ToolSchemaBuilder optionalEnum(String name, String description, List<String> values) {
        properties.put(name, Map.of(
            "type", "string",
            "description", description,
            "enum", values
        ));
        return this;
    }

    /**
     * Add an optional string enum parameter with default.
     */
    public ToolSchemaBuilder optionalEnum(String name, String description, List<String> values, String defaultValue) {
        properties.put(name, Map.of(
            "type", "string",
            "description", description,
            "enum", values,
            "default", defaultValue
        ));
        return this;
    }

    /**
     * Add a required number parameter.
     */
    public ToolSchemaBuilder requiredNumber(String name, String description) {
        properties.put(name, Map.of(
            "type", "number",
            "description", description
        ));
        required.add(name);
        return this;
    }

    /**
     * Add an optional number parameter.
     */
    public ToolSchemaBuilder optionalNumber(String name, String description) {
        properties.put(name, Map.of(
            "type", "number",
            "description", description
        ));
        return this;
    }

    /**
     * Add an optional number parameter with default.
     */
    public ToolSchemaBuilder optionalNumber(String name, String description, Number defaultValue) {
        properties.put(name, Map.of(
            "type", "number",
            "description", description,
            "default", defaultValue
        ));
        return this;
    }

    /**
     * Add an optional integer parameter with constraints.
     */
    public ToolSchemaBuilder optionalInteger(String name, String description, Integer defaultValue,
                                              Integer minimum, Integer maximum) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "integer");
        prop.put("description", description);
        if (defaultValue != null) prop.put("default", defaultValue);
        if (minimum != null) prop.put("minimum", minimum);
        if (maximum != null) prop.put("maximum", maximum);
        properties.put(name, prop);
        return this;
    }

    /**
     * Add a required boolean parameter.
     */
    public ToolSchemaBuilder requiredBoolean(String name, String description) {
        properties.put(name, Map.of(
            "type", "boolean",
            "description", description
        ));
        required.add(name);
        return this;
    }

    /**
     * Add an optional boolean parameter.
     */
    public ToolSchemaBuilder optionalBoolean(String name, String description) {
        properties.put(name, Map.of(
            "type", "boolean",
            "description", description
        ));
        return this;
    }

    /**
     * Add an optional boolean parameter with default.
     */
    public ToolSchemaBuilder optionalBoolean(String name, String description, boolean defaultValue) {
        properties.put(name, Map.of(
            "type", "boolean",
            "description", description,
            "default", defaultValue
        ));
        return this;
    }

    /**
     * Add an optional object parameter.
     */
    public ToolSchemaBuilder optionalObject(String name, String description) {
        properties.put(name, Map.of(
            "type", "object",
            "description", description
        ));
        return this;
    }

    /**
     * Add an optional array of strings parameter.
     */
    public ToolSchemaBuilder optionalStringArray(String name, String description) {
        properties.put(name, Map.of(
            "type", "array",
            "description", description,
            "items", Map.of("type", "string")
        ));
        return this;
    }

    /**
     * Add an optional array of strings parameter.
     * Alias for optionalStringArray for consistency.
     */
    public ToolSchemaBuilder optionalArray(String name, String description) {
        return optionalStringArray(name, description);
    }

    /**
     * Add a required array of strings parameter.
     */
    public ToolSchemaBuilder requiredArray(String name, String description) {
        properties.put(name, Map.of(
            "type", "array",
            "description", description,
            "items", Map.of("type", "string")
        ));
        required.add(name);
        return this;
    }

    /**
     * Build the JsonSchema.
     */
    public JsonSchema build() {
        return new JsonSchema(
            "object",
            properties,
            required.isEmpty() ? null : required,
            null,  // additionalProperties
            null,  // defs
            null   // definitions
        );
    }

    /**
     * Build a complete Tool with name and description.
     */
    public Tool buildTool(String name, String description) {
        return new Tool(name, description, build());
    }
}
