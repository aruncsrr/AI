package com.sapjco.mcp.handlers.format;

import com.sapjco.mcp.formatters.ADTFormatterRegistry;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.mcp.McpResponseFormatter;
import com.sapjco.mcp.mcp.ToolHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Handler for FormatADTResponse tool.
 * Formats raw SAP ADT responses into human-readable text.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormatADTResponseHandler implements ToolHandler {

    private final ADTFormatterRegistry formatterRegistry;
    private final FileStorageService fileStorageService;

    @Override
    public Tool getToolDefinition() {
        // Build list of supported tools for description
        List<String> supportedTools = new ArrayList<>(formatterRegistry.getRegisteredTools());
        Collections.sort(supportedTools);
        String toolList = supportedTools.isEmpty()
                ? "No formatters currently registered"
                : String.join(", ", supportedTools);

        return ToolSchemaBuilder.builder()
                .requiredString("tool_name",
                        "Name of the tool that produced the raw response (e.g., \"GetWhereUsed\", \"RunAbapUnit\", \"SaveClass\")")
                .optionalString("response_file",
                        "Path to file containing raw ADT response (preferred). " +
                        "Use file paths returned by ADT tools (e.g., GetWhereUsed, RunAbapUnit).")
                .optionalString("raw_response",
                        "Raw XML/text response (alternative to response_file for inline data)")
                .buildTool(
                        "FormatADTResponse",
                        "Format a raw SAP ADT response into human-readable text. " +
                        "All ADT tools return raw responses by default. Use this tool to format them " +
                        "for display when needed. Supported tools: " + toolList
                );
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String toolName = (String) args.get("tool_name");
        String responseFile = (String) args.get("response_file");
        String rawResponse = (String) args.get("raw_response");

        if (toolName == null || toolName.trim().isEmpty()) {
            return McpResponseFormatter.error("tool_name is required");
        }

        // Validate input: exactly one of response_file or raw_response required
        boolean hasResponseFile = responseFile != null && !responseFile.isEmpty();
        boolean hasRawResponse = rawResponse != null && !rawResponse.isEmpty();

        if (hasResponseFile && hasRawResponse) {
            return McpResponseFormatter.error("Provide response_file OR raw_response, not both");
        }
        if (!hasResponseFile && !hasRawResponse) {
            return McpResponseFormatter.error("Must provide response_file or raw_response");
        }

        // If response_file provided, read content from file
        String effectiveResponse;
        if (hasResponseFile) {
            try {
                effectiveResponse = fileStorageService.readSource(responseFile);
                log.info("FormatADTResponse called for tool: {} (file: {}, {} bytes)",
                        toolName, responseFile, effectiveResponse.length());
            } catch (IOException e) {
                log.error("Failed to read response file: {}", responseFile, e);
                return McpResponseFormatter.error("Failed to read response file: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                return McpResponseFormatter.error(e.getMessage());
            }
        } else {
            effectiveResponse = rawResponse;
            log.info("FormatADTResponse called for tool: {} (inline, {} bytes)",
                    toolName, effectiveResponse.length());
        }

        try {
            // Check if formatter exists
            if (!formatterRegistry.hasFormatter(toolName)) {
                List<String> supportedTools = new ArrayList<>(formatterRegistry.getRegisteredTools());
                Collections.sort(supportedTools);
                String message = String.format(
                        "No formatter registered for tool '%s'. Supported tools: %s",
                        toolName,
                        supportedTools.isEmpty() ? "(none)" : String.join(", ", supportedTools)
                );
                return McpResponseFormatter.error(message);
            }

            // Format the response
            String formatted = formatterRegistry.format(toolName, effectiveResponse);
            return McpResponseFormatter.success(formatted);

        } catch (FormattingException e) {
            log.error("Formatting failed for {}: {}", toolName, e.getMessage());
            return McpResponseFormatter.error(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error formatting {} response", toolName, e);
            return McpResponseFormatter.error(e);
        }
    }
}
