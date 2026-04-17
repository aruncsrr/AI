package com.sapjco.mcp.handlers.cdd;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.CddCodeCacheService;
import com.sapjco.mcp.service.CddCodeCacheService.CacheEntry;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP handler for cdd_local_get_code — retrieves CDD/NCDD object source code.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Check local cache — return immediately if found (no SAP call)</li>
 *   <li>If not cached (or force_refresh=true) — fetch from SAP ISD via ADT</li>
 *   <li>Save fetched source to local cache for future requests</li>
 * </ol>
 *
 * <p>Extends {@link AbstractReadHandler} to reuse JCo session management for SAP fallback.
 */
@Slf4j
@Component
public class CddLocalGetCodeHandler extends AbstractReadHandler {

    @Autowired
    private CddCodeCacheService cacheService;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("object_name",
                        "Name of the CDD/NCDD ABAP object. Examples: '/HEC1/CL_CDD_EMAIL', " +
                        "'CL_CDD_MANAGER', 'BPCL_I_NCDD_WORKLIST', '/HEC1/I_NCDD_WORKLIST'")
                .requiredEnum("object_type",
                        "Type of the ABAP object",
                        List.of("class", "interface", "program", "function_group",
                                "cds_view", "behavior_definition"))
                .optionalBoolean("force_refresh",
                        "Set true to re-download from SAP even if locally cached. Default: false",
                        false)
                .optionalString("system_id",
                        "Optional SAP system ID for fallback fetch (e.g. 'isd_001'). " +
                        "Uses default system if not specified.")
                .buildTool("cdd_local_get_code",
                        "Get source code for a CDD/NCDD ABAP object — checks local offline cache first, " +
                        "falls back to SAP ISD only if not cached. " +
                        "Covers all objects in /HEC1/CP_CDD and /HEC1/CP_NCDD packages. " +
                        "On cache hit: returns instantly with no SAP connection. " +
                        "On cache miss: fetches from SAP, saves to cache, returns source. " +
                        "Use cdd_local_search first if you don't know the exact object name.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        for (String req : new String[]{"object_name", "object_type"}) {
            CallToolResult v = validateRequired(args, req);
            if (v != null) return v;
        }

        String objectName  = requireString(args, "object_name");
        String objectType  = requireString(args, "object_type");
        boolean forceRefresh = optionalBoolean(args, "force_refresh", false);

        log.info("cdd_local_get_code: {} ({}) force_refresh={}", objectName, objectType, forceRefresh);

        // 1. Cache hit
        if (!forceRefresh && cacheService.isCached(objectName)) {
            Optional<Path> cached = cacheService.getCachedPath(objectName);
            if (cached.isPresent()) {
                Path filePath = cached.get();
                try {
                    long size = java.nio.file.Files.size(filePath);
                    String source = java.nio.file.Files.readString(filePath);
                    String excerpt = source.length() > 2000
                            ? source.substring(0, 2000) + "\n... (truncated, read full file)"
                            : source;

                    Optional<CacheEntry> entry = cacheService.getEntry(objectName);
                    String pkg = entry.map(CacheEntry::getPkg).orElse("");
                    String desc = entry.map(CacheEntry::getDescription).orElse("");

                    return McpResponseFormatter.success(String.format(
                            "Description: %s\n" +
                            "Cached: %s\n" +
                            "Size: %,d bytes | File: %s\n\n" +
                            "--- Source (excerpt) ---\n%s",
                            objectName, objectType, pkg, desc,
                            entry.map(CacheEntry::getCachedAt).orElse("?"),
                            size, filePath, excerpt));
                } catch (Exception e) {
                    log.warn("cdd_local_get_code: error reading cache file, will re-fetch", e);
                }
            }
        }

        // 2. Cache miss or force_refresh → fetch from SAP
        log.info("cdd_local_get_code: cache miss for '{}', fetching from SAP...", objectName);

        return executeWithErrorHandling(args, objectName, (sessionId, resolved) -> {
            String adtUrl = cacheService.buildAdtUrl(objectType, objectName);
            if (adtUrl == null) {
                return error("Unsupported object type for SAP fetch: " + objectType);
            }

            String source = executeInContext(sessionId, (dest, session) ->
                    adtClient.getSourceCodeViaRfc(dest, session, adtUrl, null, "text/plain"));

            // Save to persistent cache
            Path cachedPath = cacheService.saveToCache(objectName, objectType, source);

            // Also write to standard FileStorageService location (interop with other tools)
            String systemFileId = getSystemFileId(resolved);
            Path stdPath = fileStorageService.writeSource(systemFileId, objectType, objectName, source);

            long size = java.nio.file.Files.size(cachedPath);
            String excerpt = source.length() > 2000
                    ? source.substring(0, 2000) + "\n... (truncated, read full file)"
                    : source;

            return success(resolved, String.format(
                    "Cache: %s\n" +
                    "Mirror: %s\n" +
                    "Size: %,d bytes\n\n" +
                    "--- Source (excerpt) ---\n%s",
                    objectName, objectType, cachedPath, stdPath, size, excerpt));
        });
    }
}
