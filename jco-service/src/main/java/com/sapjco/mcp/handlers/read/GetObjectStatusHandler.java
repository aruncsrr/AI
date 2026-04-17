package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handler for GetObjectStatus tool.
 * Retrieves ABAP object metadata to determine activation status.
 *
 * <p>The adtcore:version attribute indicates the object's activation state:</p>
 * <ul>
 *   <li>{@code active} - Only active version exists</li>
 *   <li>{@code inactive} - Only inactive version (new object not yet activated)</li>
 *   <li>{@code activeWithInactiveVersion} - Active exists AND pending inactive changes</li>
 *   <li>{@code partlyActive} - Mixed state (some includes active, some inactive)</li>
 * </ul>
 */
@Slf4j
@Component
public class GetObjectStatusHandler extends AbstractReadHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name",
                        "Name of the ABAP object (e.g., \"ZCL_MY_CLASS\", \"ZTEST_PROGRAM\")")
                .requiredEnum("object_type",
                        "Type of object: class, interface, program, function_group, include",
                        List.of("class", "interface", "program", "function_group", "include"))
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetObjectStatus",
                        "Get ABAP object activation status. Returns the version status (active, inactive, " +
                        "activeWithInactiveVersion, partlyActive) indicating whether the object has " +
                        "pending inactive changes. Use this to check if an object needs activation " +
                        "before running tests. Does not require a session."
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate required parameters
        CallToolResult validation = validateRequired(args, "object_name");
        if (validation != null) return validation;
        validation = validateRequired(args, "object_type");
        if (validation != null) return validation;

        String objectName = requireString(args, "object_name");
        String objectType = requireString(args, "object_type");

        log.info("GetObjectStatus called: {} (type: {}, session: {})",
                objectName, objectType,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, objectName, (sessionId, resolved) -> {
            String metadataPath = buildMetadataPath(objectType, objectName);
            String acceptHeader = buildAcceptHeader(objectType);

            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessGetViaRfc(dest, session, metadataPath, null, acceptHeader));

            return success(resolved, response);
        });
    }

    /**
     * Build the metadata path for an object (no /source/main suffix).
     * This returns object metadata including the adtcore:version attribute.
     */
    private String buildMetadataPath(String objectType, String objectName) {
        String encodedName = encodeObjectName(objectName);
        switch (objectType.toLowerCase()) {
            case "class":
                return "/sap/bc/adt/oo/classes/" + encodedName;
            case "interface":
                return "/sap/bc/adt/oo/interfaces/" + encodedName;
            case "program":
                return "/sap/bc/adt/programs/programs/" + encodedName;
            case "function_group":
                return "/sap/bc/adt/functions/groups/" + encodedName;
            case "include":
                return "/sap/bc/adt/programs/includes/" + encodedName;
            default:
                throw new IllegalArgumentException("Unsupported object type: " + objectType);
        }
    }

    /**
     * Build the Accept header for fetching object metadata.
     * SAP ADT requires object-type-specific versioned content types.
     * Pattern follows GetTransportRequestsHandler and other metadata handlers.
     */
    private String buildAcceptHeader(String objectType) {
        switch (objectType.toLowerCase()) {
            case "class":
                return "application/vnd.sap.adt.oo.classes.v4+xml, application/xml";
            case "interface":
                return "application/vnd.sap.adt.oo.interfaces.v4+xml, application/xml";
            case "program":
                return "application/vnd.sap.adt.programs.v4+xml, application/xml";
            case "function_group":
                return "application/vnd.sap.adt.functions.groups.v4+xml, application/xml";
            case "include":
                return "application/vnd.sap.adt.programs.includes.v4+xml, application/xml";
            default:
                return "application/xml";
        }
    }

    /**
     * Check if an object has inactive changes based on its version attribute.
     *
     * @param version The adtcore:version attribute value
     * @return true if the object has inactive (unactivated) changes
     */
    public static boolean hasInactiveChanges(String version) {
        if (version == null) {
            return false;
        }
        return version.equals("activeWithInactiveVersion")
                || version.equals("inactive")
                || version.equals("partlyActive");
    }
}
