package com.sapjco.mcp.mcp;

import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry for all MCP tool handlers.
 * Collects all handlers from Spring context and provides lookup.
 */
@Slf4j
@Component
public class McpToolRegistry {

    private final Map<String, ToolHandler> handlers = new LinkedHashMap<>();

    /**
     * Create registry from Spring-injected handlers.
     */
    public McpToolRegistry(List<ToolHandler> toolHandlers) {
        for (ToolHandler handler : toolHandlers) {
            Tool tool = handler.getToolDefinition();
            String name = tool.name();

            if (handlers.containsKey(name)) {
                log.warn("Duplicate tool handler for '{}', overwriting", name);
            }

            handlers.put(name, handler);
            log.debug("Registered tool handler: {}", name);
        }

        log.info("Registered {} tool handlers", handlers.size());
    }

    /**
     * Get all tool definitions.
     */
    public List<Tool> getToolDefinitions() {
        return handlers.values().stream()
                .map(ToolHandler::getToolDefinition)
                .toList();
    }

    /**
     * Get a handler by tool name.
     */
    public Optional<ToolHandler> getHandler(String toolName) {
        return Optional.ofNullable(handlers.get(toolName));
    }

    /**
     * Get all registered tool names.
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * Get count of registered handlers.
     */
    public int getHandlerCount() {
        return handlers.size();
    }
}
