package com.sapjco.mcp.web;

import com.sapjco.mcp.mcp.McpToolRegistry;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.service.CddCodeCacheService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REST API for the Node.js knowledge pipeline.
 *
 * <p>Allows the offline crawler to invoke any MCP tool through the Java server,
 * which handles SNC authentication transparently.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/pipeline/tool — invoke a named MCP tool and return its output
 *   <li>POST /api/pipeline/reload-cache — hot-reload the CDD cache index from disk
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private static final Pattern FILE_PATH_PATTERN =
            Pattern.compile("File:\\s*(/[^\\n\\r]+)");

    private final McpToolRegistry toolRegistry;
    private final CddCodeCacheService cddCacheService;

    /**
     * Invoke any registered MCP tool by name with the given input parameters.
     *
     * <p>Request body:
     * <pre>{@code
     * {
     *   "tool": "GetClass",
     *   "input": { "class_name": "/HEC1/CL_CDD_EMAIL", "system_id": "ISD" }
     * }
     * }</pre>
     *
     * <p>Response:
     * <pre>{@code
     * {
     *   "isError": false,
     *   "text": "File: /tmp/sap-mcp/... | Lines: 1407 | ...",
     *   "filePath": "/tmp/sap-mcp/ISD_100/class/%2fHEC1%2fCL_CDD_EMAIL.abap",
     *   "fileContent": "<full ABAP source>"
     * }
     * }</pre>
     */
    @PostMapping("/tool")
    public ResponseEntity<Map<String, Object>> invokeTool(
            @RequestBody Map<String, Object> body) {

        String toolName = (String) body.get("tool");
        @SuppressWarnings("unchecked")
        Map<String, Object> input = body.containsKey("input")
                ? (Map<String, Object>) body.get("input")
                : Map.of();

        if (toolName == null || toolName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "isError", true,
                    "text", "Missing 'tool' field in request body"));
        }

        Optional<ToolHandler> handlerOpt = toolRegistry.getHandler(toolName);
        if (handlerOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "isError", true,
                    "text", "Unknown tool: " + toolName));
        }

        log.info("Pipeline tool call: {} with input keys={}", toolName, input.keySet());

        try {
            CallToolRequest request = new CallToolRequest(toolName, input);
            McpSchema.CallToolResult result = handlerOpt.get().handle(null, request);

            StringBuilder textBuilder = new StringBuilder();
            boolean isError = result.isError() != null && result.isError();

            if (result.content() != null) {
                for (McpSchema.Content content : result.content()) {
                    if (content instanceof McpSchema.TextContent tc) {
                        textBuilder.append(tc.text());
                    }
                }
            }

            String text = textBuilder.toString();
            Map<String, Object> response = new HashMap<>();
            response.put("isError", isError);
            response.put("text", text);

            // Extract file path from response text and read the file
            String filePath = extractFilePath(text);
            response.put("filePath", filePath != null ? filePath : "");

            if (filePath != null && !isError) {
                try {
                    Path path = Paths.get(filePath);
                    if (Files.exists(path) && Files.isReadable(path)) {
                        String fileContent = Files.readString(path, StandardCharsets.UTF_8);
                        response.put("fileContent", fileContent);
                    } else {
                        response.put("fileContent", "");
                        log.warn("Pipeline: file not found or unreadable: {}", filePath);
                    }
                } catch (Exception e) {
                    response.put("fileContent", "");
                    log.warn("Pipeline: could not read file {}: {}", filePath, e.getMessage());
                }
            } else {
                response.put("fileContent", "");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Pipeline tool error for {}: {}", toolName, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "isError", true,
                    "text", "Error executing " + toolName + ": " + e.getMessage(),
                    "filePath", "",
                    "fileContent", ""));
        }
    }

    /**
     * Hot-reload the CDD cache index from disk.
     * Call this after the Node.js sync-java-cache.js writes new objects.
     */
    @PostMapping("/reload-cache")
    public ResponseEntity<Map<String, Object>> reloadCache() {
        try {
            int before = cddCacheService.getIndex().size();
            cddCacheService.reloadIndex();
            int after = cddCacheService.getIndex().size();
            log.info("Pipeline: cache reloaded — {} → {} entries", before, after);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("Cache reloaded: %d → %d entries", before, after),
                    "entries", after));
        } catch (Exception e) {
            log.error("Pipeline: cache reload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Reload failed: " + e.getMessage()));
        }
    }

    /** Extract a file path from a "File: /path/to/file" reference in tool output. */
    private String extractFilePath(String text) {
        if (text == null) return null;
        Matcher m = FILE_PATH_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
}
