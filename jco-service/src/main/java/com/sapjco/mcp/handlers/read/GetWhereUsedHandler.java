package com.sapjco.mcp.handlers.read;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for GetWhereUsed tool.
 * Finds where an ABAP object is used via RFC proxy.
 * Writes results to file and returns metadata summary.
 */
@Slf4j
@Component
public class GetWhereUsedHandler extends AbstractReadHandler {

    private static final int DEFAULT_MAX_RESULTS = 100;
    private static final int MAX_RESULTS_LIMIT = 1000;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name",
                        "Name of the ABAP object to find usages for (e.g., \"ZCL_MY_CLASS\", \"ZDATA_ELEMENT\")")
                .requiredEnum("object_type",
                        "Type of object: class, interface, program, function_group, function_module, " +
                        "data_element, table, structure, domain, search_help, message_class, package, " +
                        "cds_view, badi_definition, badi_implementation",
                        List.of("class", "interface", "program", "function_group", "function_module",
                                "data_element", "table", "structure", "domain", "search_help",
                                "message_class", "package", "cds_view", "badi_definition", "badi_implementation"))
                .optionalNumber("line",
                        "Optional line number for position-based where-used. Must be provided together with column. " +
                        "When specified, finds usages of the specific element at this position.")
                .optionalNumber("column",
                        "Optional column number for position-based where-used. Must be provided together with line.")
                .optionalString("enclosing_object_name",
                        "Optional enclosing object name (e.g., function group name when searching for a function module)")
                .optionalNumber("max_results",
                        "Maximum number of results to return (default: 100, max: 1000). " +
                        "Use position-based query to narrow search if more results exist.")
                .optionalBoolean("with_sources",
                        "Include source code references in results (default: true)")
                .optionalBoolean("scope_only",
                        "If true, only execute Phase 1 (scope) and return the objectIdentifier metadata " +
                        "without running the full where-used query. Useful for validating position-based queries " +
                        "before running potentially slow full queries.")
                .optionalString("session_id",
                        "Optional session ID from CreateSession. If not provided, a temporary session " +
                        "will be created automatically.")
                .optionalString("system_id",
                        "Optional SAP system ID to connect to (e.g., \"dev\", \"prod\"). " +
                        "If not specified, uses the default system.")
                .buildTool(
                        "GetWhereUsed",
                        "Find where an ABAP object is used (lazy loading by default). Supports two modes: " +
                        "(1) Whole-object: find all usages of a class, interface, etc. " +
                        "(2) Position-based: find usages of a specific element at line/column. " +
                        "Returns lightweight object references by default. For widely-used structures, " +
                        "ALWAYS use position-based queries (line/column) to narrow scope and prevent timeouts. " +
                        "TIP: The XML response includes package information (packageRef elements) - " +
                        "use this tool to discover which package an object belongs to. " +
                        "Response includes a truncated excerpt (first ~2000 chars). Read the full file if more context is needed, " +
                        "or use FormatADTResponse to convert XML to human-readable format."
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
        Integer line = optionalInteger(args, "line");
        Integer column = optionalInteger(args, "column");
        String enclosingObjectName = optionalString(args, "enclosing_object_name");
        int maxResults = optionalInt(args, "max_results", DEFAULT_MAX_RESULTS, MAX_RESULTS_LIMIT);
        boolean withSources = optionalBoolean(args, "with_sources", true);
        boolean scopeOnly = optionalBoolean(args, "scope_only", false);

        // Validate line/column pairing
        if ((line != null && column == null) || (line == null && column != null)) {
            return error("Both line and column must be provided together for position-based where-used");
        }

        log.info("GetWhereUsed called: {} (type: {}, line: {}, col: {}, max: {}, session: {})",
                objectName, objectType, line, column, maxResults,
                optionalString(args, "session_id") != null ? optionalString(args, "session_id") : "(temp)");

        return executeWithErrorHandling(args, objectName, (sessionId, resolved) -> {
            // Build object URI for the query param
            String objectUri = buildObjectUri(objectType, objectName, enclosingObjectName);

            // Phase 1: Get scope information first
            String scopeRequestBody = buildScopeRequest(objectName, objectType, enclosingObjectName, line, column);

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("uri", objectUri);

            // Execute Phase 1 - scope request
            String scopePath = "/sap/bc/adt/repository/informationsystem/usageReferences/scope";
            String scopeResponse = executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessPostViaRfc(dest, session, scopePath, queryParams, scopeRequestBody,
                            "application/vnd.sap.adt.repository.usagereferences.scope.request.v1+xml",
                            "application/vnd.sap.adt.repository.usagereferences.scope.response.v1+xml").getBody());

            // If scope_only, return here
            if (scopeOnly) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Where-Used Scope: %s (%s)\n", objectName.toUpperCase(), objectType));
                if (line != null && column != null) {
                    sb.append(String.format("Position: line %d, column %d\n", line, column));
                }
                sb.append("(Scope-only mode - Phase 1 metadata only)\n");
                sb.append("-".repeat(60)).append("\n\n");
                sb.append(scopeResponse);

                return success(resolved, sb.toString());
            }

            // Phase 2: Full where-used query
            String whereUsedRequestBody = buildWhereUsedRequest(objectName, objectType, enclosingObjectName,
                    line, column, maxResults, withSources);

            String path = "/sap/bc/adt/repository/informationsystem/usageReferences";
            String response = executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessPostViaRfc(dest, session, path, queryParams, whereUsedRequestBody,
                            "application/vnd.sap.adt.repository.usagereferences.request.v1+xml",
                            "application/vnd.sap.adt.repository.usagereferences.result.v1+xml").getBody());

            // Write results to file
            String systemFileId = getSystemFileId(resolved);
            String sanitizedName = fileStorageService.sanitizeFilename(objectName.toUpperCase());
            Path filePath = fileStorageService.writeXml(systemFileId, FileStorageService.CAT_WHERE_USED, sanitizedName, response);

            log.info("Wrote where-used results to file: {} ({} bytes)", filePath, fileStorageService.getByteSize(filePath));

            // Extract metadata for summary
            Map<String, Object> metadata = metadataExtractorService.extractWhereUsedMetadata(response);

            // Build header lines
            String headerLine1 = String.format("Where-Used: %s (%s)", objectName.toUpperCase(), objectType);
            if (line != null && column != null) {
                return formatXmlResponse(resolved, filePath, response, metadata,
                        headerLine1, String.format("Position: line %d, column %d", line, column));
            }

            return formatXmlResponse(resolved, filePath, response, metadata, headerLine1);
        });
    }

    private String buildScopeRequest(String objectName, String objectType, String enclosingObjectName,
                                       Integer line, Integer column) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<usagereferences:usageScopeRequest xmlns:usagereferences=\"http://www.sap.com/adt/ris/usageReferences\"");
        xml.append(" xmlns:adtcore=\"http://www.sap.com/adt/core\">\n");

        // Add object reference
        String uri = buildObjectUri(objectType, objectName, enclosingObjectName);
        String typeCode = getAdtTypeCode(objectType);

        xml.append("  <usagereferences:objectReferenceRequest");
        xml.append(" adtcore:uri=\"").append(uri).append("\"");
        xml.append(" adtcore:type=\"").append(typeCode).append("\"");
        xml.append(" adtcore:name=\"").append(objectName.toUpperCase()).append("\"");

        if (line != null && column != null) {
            xml.append(">\n");
            xml.append("    <usagereferences:position");
            xml.append(" line=\"").append(line).append("\"");
            xml.append(" column=\"").append(column).append("\"");
            xml.append("/>\n");
            xml.append("  </usagereferences:objectReferenceRequest>\n");
        } else {
            xml.append("/>\n");
        }

        xml.append("</usagereferences:usageScopeRequest>");
        return xml.toString();
    }

    private String buildWhereUsedRequest(String objectName, String objectType, String enclosingObjectName,
                                          Integer line, Integer column, int maxResults, boolean withSources) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<usagereferences:usageReferenceRequest xmlns:usagereferences=\"http://www.sap.com/adt/ris/usageReferences\"");
        xml.append(" xmlns:adtcore=\"http://www.sap.com/adt/core\"");
        xml.append(" maximumNumberOfResults=\"").append(maxResults).append("\">\n");

        // Add object reference
        String uri = buildObjectUri(objectType, objectName, enclosingObjectName);
        String typeCode = getAdtTypeCode(objectType);

        xml.append("  <usagereferences:objectReferenceRequest");
        xml.append(" adtcore:uri=\"").append(uri).append("\"");
        xml.append(" adtcore:type=\"").append(typeCode).append("\"");
        xml.append(" adtcore:name=\"").append(objectName.toUpperCase()).append("\"");

        if (line != null && column != null) {
            xml.append(">\n");
            xml.append("    <usagereferences:position");
            xml.append(" line=\"").append(line).append("\"");
            xml.append(" column=\"").append(column).append("\"");
            xml.append("/>\n");
            xml.append("  </usagereferences:objectReferenceRequest>\n");
        } else {
            xml.append("/>\n");
        }

        xml.append("</usagereferences:usageReferenceRequest>");
        return xml.toString();
    }

    private String buildObjectUri(String objectType, String objectName, String enclosingObjectName) {
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
            case "function_module":
                if (enclosingObjectName != null) {
                    return "/sap/bc/adt/functions/groups/" + encodeObjectName(enclosingObjectName) +
                            "/fmodules/" + encodedName;
                }
                return "/sap/bc/adt/functions/groups/UNKNOWN/fmodules/" + encodedName;
            case "data_element":
                return "/sap/bc/adt/ddic/dataelements/" + encodedName;
            case "table":
            case "structure":
                return "/sap/bc/adt/ddic/tables/" + encodedName;
            case "domain":
                return "/sap/bc/adt/ddic/domains/" + encodedName;
            case "cds_view":
                return "/sap/bc/adt/ddic/ddl/sources/" + encodedName;
            case "package":
                return "/sap/bc/adt/packages/" + encodedName;
            default:
                return "/sap/bc/adt/repository/" + encodedName;
        }
    }

    private String getAdtTypeCode(String objectType) {
        switch (objectType.toLowerCase()) {
            case "class": return "CLAS/OC";
            case "interface": return "INTF/OI";
            case "program": return "PROG/P";
            case "function_group": return "FUGR/F";
            case "function_module": return "FUGR/FF";
            case "data_element": return "DTEL/DE";
            case "table": return "TABL/DT";
            case "structure": return "TABL/DS";
            case "domain": return "DOMA/DO";
            case "cds_view": return "DDLS/DF";
            case "package": return "DEVC/K";
            default: return objectType.toUpperCase();
        }
    }
}
