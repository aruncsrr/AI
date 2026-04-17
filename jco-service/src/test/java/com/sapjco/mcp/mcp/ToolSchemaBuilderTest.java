package com.sapjco.mcp.mcp;

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolSchemaBuilder.
 */
class ToolSchemaBuilderTest {

    @Test
    void buildTool_simpleStringParam() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .requiredString("name", "The object name")
                .buildTool("GetObject", "Get an object");

        // Assert
        assertEquals("GetObject", tool.name());
        assertEquals("Get an object", tool.description());

        JsonSchema schema = tool.inputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.type());

        @SuppressWarnings("unchecked")
        Map<String, Object> nameParam = (Map<String, Object>) schema.properties().get("name");
        assertEquals("string", nameParam.get("type"));
        assertEquals("The object name", nameParam.get("description"));

        assertTrue(schema.required().contains("name"));
    }

    @Test
    void buildTool_optionalStringParam() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .optionalString("system_id", "Optional system ID")
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> param = (Map<String, Object>) schema.properties().get("system_id");
        assertEquals("string", param.get("type"));

        // Optional params should NOT be in required list
        assertNull(schema.required());
    }

    @Test
    void buildTool_optionalStringWithDefault() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .optionalString("version", "The version", "active")
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> param = (Map<String, Object>) schema.properties().get("version");
        assertEquals("active", param.get("default"));
    }

    @Test
    void buildTool_requiredEnum() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .requiredEnum("object_type", "Type of object",
                        List.of("class", "interface", "program"))
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> param = (Map<String, Object>) schema.properties().get("object_type");
        assertEquals("string", param.get("type"));

        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) param.get("enum");
        assertEquals(3, enumValues.size());
        assertTrue(enumValues.contains("class"));
        assertTrue(enumValues.contains("interface"));
        assertTrue(enumValues.contains("program"));

        assertTrue(schema.required().contains("object_type"));
    }

    @Test
    void buildTool_optionalEnumWithDefault() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .optionalEnum("version", "Version to retrieve",
                        List.of("active", "inactive"), "active")
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> param = (Map<String, Object>) schema.properties().get("version");
        assertEquals("active", param.get("default"));

        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) param.get("enum");
        assertEquals(2, enumValues.size());
    }

    @Test
    void buildTool_booleanParams() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .requiredBoolean("required_flag", "A required flag")
                .optionalBoolean("optional_flag", "An optional flag")
                .optionalBoolean("flag_with_default", "Flag with default", true)
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> required = (Map<String, Object>) schema.properties().get("required_flag");
        assertEquals("boolean", required.get("type"));
        assertTrue(schema.required().contains("required_flag"));

        @SuppressWarnings("unchecked")
        Map<String, Object> optional = (Map<String, Object>) schema.properties().get("optional_flag");
        assertEquals("boolean", optional.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> withDefault = (Map<String, Object>) schema.properties().get("flag_with_default");
        assertEquals(true, withDefault.get("default"));
    }

    @Test
    void buildTool_numberParams() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .requiredNumber("count", "The count")
                .optionalNumber("limit", "Max results")
                .optionalNumber("timeout", "Timeout in seconds", 30)
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> required = (Map<String, Object>) schema.properties().get("count");
        assertEquals("number", required.get("type"));
        assertTrue(schema.required().contains("count"));

        @SuppressWarnings("unchecked")
        Map<String, Object> withDefault = (Map<String, Object>) schema.properties().get("timeout");
        assertEquals(30, withDefault.get("default"));
    }

    @Test
    void buildTool_integerWithConstraints() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .optionalInteger("max_results", "Maximum number of results", 100, 1, 1000)
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> param = (Map<String, Object>) schema.properties().get("max_results");
        assertEquals("integer", param.get("type"));
        assertEquals(100, param.get("default"));
        assertEquals(1, param.get("minimum"));
        assertEquals(1000, param.get("maximum"));
    }

    @Test
    void buildTool_arrayParams() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .optionalStringArray("tags", "List of tags")
                .requiredArray("identifiers", "Required list of identifiers")
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> optional = (Map<String, Object>) schema.properties().get("tags");
        assertEquals("array", optional.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) optional.get("items");
        assertEquals("string", items.get("type"));

        assertTrue(schema.required().contains("identifiers"));
    }

    @Test
    void buildTool_objectParam() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .optionalObject("parameters", "Optional parameters as key-value pairs")
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> param = (Map<String, Object>) schema.properties().get("parameters");
        assertEquals("object", param.get("type"));
    }

    @Test
    void buildTool_multipleParams_preservesOrder() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .requiredString("class_name", "Class name")
                .optionalString("version", "Version")
                .optionalString("system_id", "System ID")
                .optionalString("session_id", "Session ID")
                .buildTool("GetClass", "Get class source");

        // Assert
        JsonSchema schema = tool.inputSchema();

        // Properties should exist
        assertEquals(4, schema.properties().size());
        assertTrue(schema.properties().containsKey("class_name"));
        assertTrue(schema.properties().containsKey("version"));
        assertTrue(schema.properties().containsKey("system_id"));
        assertTrue(schema.properties().containsKey("session_id"));

        // Only class_name is required
        assertEquals(1, schema.required().size());
        assertEquals("class_name", schema.required().get(0));
    }

    @Test
    void build_returnsJsonSchemaOnly() {
        // Act
        JsonSchema schema = ToolSchemaBuilder.builder()
                .requiredString("name", "Name")
                .build();

        // Assert
        assertNotNull(schema);
        assertEquals("object", schema.type());
        assertTrue(schema.properties().containsKey("name"));
    }

    @Test
    void buildTool_noRequiredParams_requiredIsNull() {
        // Act
        Tool tool = ToolSchemaBuilder.builder()
                .optionalString("optional_param", "Optional")
                .buildTool("MyTool", "A tool");

        // Assert
        JsonSchema schema = tool.inputSchema();
        assertNull(schema.required());
    }
}
