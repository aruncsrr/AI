package com.sapjco.mcp.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapjco.mcp.mcp.McpToolRegistry;
import com.sapjco.mcp.mcp.ToolHandler;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Calls the Anthropic Claude API with all registered MCP tools,
 * executes any tool_use blocks locally, and returns the final response.
 */
@Slf4j
@Service
public class AnthropicChatService {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_MODEL = "claude-sonnet-latest";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_TOOL_ROUNDS = 10;

    private final McpToolRegistry toolRegistry;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http;
    private final String baseUrl;
    private final String model;
    private final String authToken;

    public AnthropicChatService(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.baseUrl = Optional.ofNullable(System.getenv("ANTHROPIC_BASE_URL"))
                .map(u -> u.endsWith("/") ? u.substring(0, u.length() - 1) : u)
                .orElse(DEFAULT_BASE_URL);
        this.model = Optional.ofNullable(System.getenv("ANTHROPIC_MODEL"))
                .orElse(DEFAULT_MODEL);
        this.authToken = Optional.ofNullable(System.getenv("ANTHROPIC_AUTH_TOKEN"))
                .orElse("");

        log.info("AnthropicChatService initialized: baseUrl={} model={} tools={}",
                baseUrl, model, toolRegistry.getHandlerCount());
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public Map<String, Object> chat(List<Map<String, Object>> messages) throws Exception {
        List<Map<String, Object>> conversation = new ArrayList<>(messages);
        List<Map<String, Object>> toolCallLog = new ArrayList<>();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<String, Object> response = callClaude(conversation);
            String stopReason = (String) response.get("stop_reason");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contentBlocks =
                    (List<Map<String, Object>>) response.get("content");

            if ("tool_use".equals(stopReason)) {
                // Append assistant message (with tool_use blocks) to conversation
                conversation.add(Map.of("role", "assistant", "content", contentBlocks));

                // Execute each tool_use block
                List<Map<String, Object>> toolResults = new ArrayList<>();
                for (Map<String, Object> block : contentBlocks) {
                    if ("tool_use".equals(block.get("type"))) {
                        String toolUseId = (String) block.get("id");
                        String toolName = (String) block.get("name");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> input = (Map<String, Object>) block.get("input");

                        log.info("Executing tool: {} ({})", toolName, toolUseId);
                        String toolOutput = executeTool(toolName, input);

                        toolCallLog.add(Map.of(
                                "tool", toolName,
                                "input", input != null ? input : Map.of(),
                                "output_preview", toolOutput.length() > 200
                                        ? toolOutput.substring(0, 200) + "…"
                                        : toolOutput
                        ));

                        toolResults.add(Map.of(
                                "type", "tool_result",
                                "tool_use_id", toolUseId,
                                "content", toolOutput
                        ));
                    }
                }

                // Append tool results as user message
                conversation.add(Map.of("role", "user", "content", toolResults));

            } else {
                // Final response — extract text
                String text = extractText(contentBlocks);
                return Map.of(
                        "role", "assistant",
                        "content", text,
                        "toolCalls", toolCallLog
                );
            }
        }

        return Map.of(
                "role", "assistant",
                "content", "Reached maximum tool call rounds. Please try a more specific question.",
                "toolCalls", toolCallLog
        );
    }

    public Map<String, Object> getStatus() {
        return Map.of(
                "status", "ok",
                "model", model,
                "baseUrl", baseUrl,
                "tools", toolRegistry.getHandlerCount(),
                "toolNames", toolRegistry.getToolNames()
        );
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Map<String, Object> callClaude(List<Map<String, Object>> messages) throws Exception {
        // Build tools array from registry
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Tool tool : toolRegistry.getToolDefinitions()) {
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("name", tool.name());
            toolDef.put("description", tool.description() != null ? tool.description() : "");
            // Convert MCP schema to Anthropic input_schema
            toolDef.put("input_schema", mcpSchemaToInputSchema(tool));
            tools.add(toolDef);
        }

        // Build request body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 4096);
        body.put("system",
                "You are an intelligent assistant with access to SAP tools and Jira. " +
                "You can query SAP Wiki pages, SAP ABAP development systems (ISD), and Jira issue tracking. " +
                "When users ask about Wiki pages, use wiki_* tools. " +
                "When users ask about SAP ABAP code or systems, use the ADT tools. " +
                "When users ask about Jira issues, tickets, or projects, use jira_* tools:\n" +
                "  - jira_get_issue: fetch details of a specific issue by key (e.g. APC-1213)\n" +
                "  - jira_search_issues: search with JQL (e.g. 'project = APC AND status = Open')\n" +
                "  - jira_create_issue: create a new issue\n" +
                "  - jira_update_issue: update issue fields\n" +
                "  - jira_add_comment: add a comment\n" +
                "  - jira_get_transitions: list available status transitions\n" +
                "  - jira_transition_issue: change issue status\n" +
                "Always present results clearly. When returning file paths, mention the file was saved locally. " +
                "For tables and structured data, use markdown tables.\n\n" +
                "COMPARE KEYWORD BEHAVIOR: When the user's message contains the word 'compare' and two " +
                "comma-separated triplets (e.g. 'compare 000000000086,000001,006 with 000000000086,000001,005'), " +
                "parse each triplet as: 1st value=HEC_CONFID, 2nd value=HEC_CONF_VERSION, 3rd value=CDD_VERSION. " +
                "Then IMMEDIATELY call the CompareCddHeader tool with:\n" +
                "  confid       = first triplet's 1st value\n" +
                "  conf_version = first triplet's 2nd value\n" +
                "  cdd_version_1= first triplet's 3rd value\n" +
                "  cdd_version_2= second triplet's 3rd value\n" +
                "Keep leading zeros exactly as provided. Do NOT use SelectSQLQuery or PreviewCDSView for this. " +
                "The CompareCddHeader tool fetches both records, compares all fields, and generates a PDF automatically. " +
                "Report the PDF path from the tool result to the user.\n\n" +
                "SIMULATE KEYWORD BEHAVIOR: When the user's message contains the word 'simulate', " +
                "parse the configuration ID and version from the message. " +
                "Examples: 'Simulate 000000012860 Version 1' or 'Simulate 000000012860,1'. " +
                "Then IMMEDIATELY call the SimulateNcdd tool with:\n" +
                "  conf_i = the configuration ID (e.g. '000000012860')\n" +
                "  conf_v = the version (e.g. '1' or '000001')\n" +
                "Keep leading zeros exactly as provided. Do NOT use any other tool for this. " +
                "The SimulateNcdd tool runs the ABAP report as a background job and generates a PDF automatically. " +
                "Report the PDF path from the tool result to the user.\n\n" +
                "DURATION KEYWORD BEHAVIOR: When the user's message contains the word 'duration', " +
                "extract the Solution Alias and Implementation Type from the message. " +
                "Examples: 'Get Duration of solution S/4HANA On Premise and Implementation Type Template Solutions' " +
                "or 'Duration for S/4HANA Cloud and New Implementations'. " +
                "Then IMMEDIATELY call the wiki_duration_lookup tool with:\n" +
                "  solution_alias      = the solution name (e.g. 'S/4HANA On Premise')\n" +
                "  implementation_type = the implementation type (e.g. 'Template Solutions')\n" +
                "The tool reads Wiki page 5888305446 section 5.3.1 and returns the Days Duration value. " +
                "Present the result clearly to the user, stating the Days Duration number.\n\n" +
                "CDD/NCDD CODE KNOWLEDGE:\n" +
                "You have a local offline cache of all CDD and NCDD package code (~379 objects across " +
                "/HEC1/CP_CDD and /HEC1/CP_NCDD). ALWAYS use local tools first for CDD/NCDD questions:\n" +
                "  - cdd_knowledge_base: Get architecture overview — packages, classes, tables, " +
                "notification system, feature toggles, customization tables. Call this FIRST for any " +
                "CDD/NCDD question before searching or fetching code.\n" +
                "  - cdd_local_search: Search by class name, keyword, or description. " +
                "Use when you need to find a specific object (e.g. 'notification', 'automation', 'CL_CDD_EMAIL').\n" +
                "  - cdd_local_get_code: Get full source code. Checks local cache first (instant), " +
                "falls back to SAP ISD only if not cached. Use after cdd_local_search to get source.\n" +
                "Only use GetClass/GetInterface/etc. ADT tools directly for non-CDD/NCDD objects or when " +
                "force_refresh=true is explicitly requested.");
        body.put("messages", messages);
        body.put("tools", tools);

        String jsonBody = mapper.writeValueAsString(body);
        log.debug("Calling Claude API: {} messages, {} tools", messages.size(), tools.size());

        String endpoint = baseUrl + "/v1/messages";

        Request.Builder reqBuilder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(jsonBody, JSON))
                .header("Content-Type", "application/json")
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-beta", "interleaved-thinking-2025-05-14");

        if (!authToken.isBlank()) {
            reqBuilder.header("x-auth-token", authToken);
            // Also set as Authorization bearer (some proxies need this)
            reqBuilder.header("Authorization", "Bearer " + authToken);
        }

        try (Response response = http.newCall(reqBuilder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("Claude API error {}: {}", response.code(), responseBody);
                throw new IOException("Claude API returned " + response.code() + ": " + responseBody);
            }
            return mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mcpSchemaToInputSchema(Tool tool) {
        // MCP Tool inputSchema is a JsonSchema record
        if (tool.inputSchema() == null) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            // Serialize and deserialize to get a plain Map
            String json = mapper.writeValueAsString(tool.inputSchema());
            Map<String, Object> schema = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            // Ensure type=object
            schema.putIfAbsent("type", "object");
            return schema;
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private String executeTool(String toolName, Map<String, Object> input) {
        Optional<ToolHandler> handler = toolRegistry.getHandler(toolName);
        if (handler.isEmpty()) {
            return "Error: Unknown tool '" + toolName + "'";
        }

        try {
            CallToolRequest request = new CallToolRequest(toolName,
                    input != null ? input : Map.of());
            McpSchema.CallToolResult result = handler.get().handle(null, request);

            if (result == null) return "(no result)";

            StringBuilder sb = new StringBuilder();
            if (result.content() != null) {
                for (McpSchema.Content content : result.content()) {
                    if (content instanceof McpSchema.TextContent tc) {
                        sb.append(tc.text());
                    }
                }
            }
            return sb.length() > 0 ? sb.toString() : "(empty result)";

        } catch (Exception e) {
            log.error("Tool '{}' execution error: {}", toolName, e.getMessage(), e);
            return "Error executing " + toolName + ": " + e.getMessage();
        }
    }

    private String extractText(List<Map<String, Object>> contentBlocks) {
        if (contentBlocks == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> block : contentBlocks) {
            String type = (String) block.get("type");
            if ("text".equals(type)) {
                Object text = block.get("text");
                if (text != null) sb.append(text);
            }
        }
        return sb.toString().trim();
    }
}
