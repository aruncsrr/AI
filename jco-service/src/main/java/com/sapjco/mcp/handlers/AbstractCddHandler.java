package com.sapjco.mcp.handlers;

import com.sapjco.mcp.config.CddCacheConfig;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.service.CddCodeCacheService;
import com.sapjco.mcp.service.FileStorageService;
import com.sapjco.mcp.util.ParameterExtractor;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Abstract base class for CDD/NCDD local knowledge base MCP handlers.
 *
 * <p>CDD handlers are primarily local/offline operations backed by the code cache.
 * No JCo session is required for pure cache reads; SAP fallback is done via
 * the existing read-handler infrastructure in {@code CddLocalGetCodeHandler}.
 */
@Slf4j
public abstract class AbstractCddHandler implements ToolHandler {

    @Autowired
    protected CddCodeCacheService cacheService;

    @Autowired
    protected CddCacheConfig cacheConfig;

    @Autowired
    protected FileStorageService fileStorageService;

    // ── Parameter helpers ─────────────────────────────────────────────────────

    protected CallToolResult validateRequired(Map<String, Object> args, String name) {
        return ParameterExtractor.validateRequiredString(args, name);
    }

    protected String requireString(Map<String, Object> args, String name) {
        return ParameterExtractor.requireString(args, name);
    }

    protected String optionalString(Map<String, Object> args, String name) {
        return ParameterExtractor.optionalString(args, name);
    }

    protected String optionalString(Map<String, Object> args, String name, String defaultValue) {
        return ParameterExtractor.optionalString(args, name, defaultValue);
    }

    protected boolean optionalBoolean(Map<String, Object> args, String name, boolean defaultValue) {
        return ParameterExtractor.optionalBoolean(args, name, defaultValue);
    }

    // ── Response helpers ──────────────────────────────────────────────────────

    protected CallToolResult success(String content) {
        return McpResponseFormatter.success(content);
    }

    protected CallToolResult error(String message) {
        return McpResponseFormatter.error(message);
    }

    protected CallToolResult error(Exception e) {
        return McpResponseFormatter.error(e);
    }

    protected CallToolResult fileResponse(Path filePath, long byteSize, String... headerLines) {
        StringBuilder sb = new StringBuilder();
        for (String line : headerLines) sb.append(line).append("\n");
        sb.append(String.format(Locale.ROOT, "Size: %,d bytes\n", byteSize));
        sb.append(String.format("File: %s\n", filePath));
        return success(sb.toString());
    }
}
