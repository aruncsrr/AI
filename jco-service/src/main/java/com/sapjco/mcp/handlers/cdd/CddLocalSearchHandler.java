package com.sapjco.mcp.handlers.cdd;

import com.sapjco.mcp.handlers.AbstractCddHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.CddCodeCacheService.CacheEntry;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP handler for cdd_local_search — searches the local CDD/NCDD code cache index.
 *
 * <p>Searches by object name, description, and package. No SAP connection required.
 * Returns matching objects with their type, package, description, and whether source
 * has been downloaded to local cache.
 */
@Slf4j
@Component
public class CddLocalSearchHandler extends AbstractCddHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("query",
                        "Search term to match against object name, description, or package. " +
                        "Case-insensitive. Examples: 'notification', 'email', 'CL_CDD_EMAIL', " +
                        "'customiz', 'automation', 'BOPF', 'worklist'")
                .optionalEnum("object_type",
                        "Filter by object type. Default: all",
                        List.of("all", "class", "interface", "program", "function_group",
                                "cds_view", "behavior_definition"),
                        "all")
                .buildTool("cdd_local_search",
                        "Search the local CDD/NCDD offline code cache by name, description, or package. " +
                        "Covers all objects in /HEC1/CP_CDD and /HEC1/CP_NCDD (classes, interfaces, programs, " +
                        "CDS views, BDEFs). Returns matching objects with type, package, description, and cache status. " +
                        "No SAP connection required — searches local index instantly. " +
                        "Use cdd_local_get_code to retrieve source of a specific object found here.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        CallToolResult validation = validateRequired(args, "query");
        if (validation != null) return validation;

        String query = requireString(args, "query");
        String objectType = optionalString(args, "object_type", "all");

        log.info("cdd_local_search: query='{}' type='{}'", query, objectType);

        List<CacheEntry> results = cacheService.search(query, objectType);

        if (results.isEmpty()) {
            return success(String.format(
                "No CDD/NCDD objects found matching '%s'" +
                (!"all".equals(objectType) ? " (type: " + objectType + ")" : "") +
                ".\n\nTip: Try broader terms like 'notification', 'email', 'config', 'auto', 'worklist'.",
                query));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d object(s) matching '%s'", results.size(), query));
        if (!"all".equals(objectType)) sb.append(String.format(" [type: %s]", objectType));
        sb.append(":\n\n");

        // Table header
        sb.append(String.format("%-45s %-20s %-35s %-30s %s%n",
                "Object Name", "Type", "Package", "Description", "Cached?"));
        sb.append("-".repeat(145)).append("\n");

        for (CacheEntry e : results) {
            boolean cached = cacheService.isCached(e.getName());
            sb.append(String.format("%-45s %-20s %-35s %-30s %s%n",
                    truncate(e.getName(), 44),
                    e.getType(),
                    truncate(e.getPkg() != null ? e.getPkg() : "", 34),
                    truncate(e.getDescription() != null ? e.getDescription() : "", 29),
                    cached ? "Yes" : "No (use cdd_local_get_code to fetch)"));
        }

        sb.append("\nUse cdd_local_get_code with object_name and object_type to retrieve source code.");
        return success(sb.toString());
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
