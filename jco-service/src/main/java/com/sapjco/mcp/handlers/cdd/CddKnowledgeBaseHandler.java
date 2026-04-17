package com.sapjco.mcp.handlers.cdd;

import com.sapjco.mcp.handlers.AbstractCddHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MCP handler for cdd_knowledge_base — returns the embedded CDD/NCDD architecture knowledge base.
 *
 * <p>Always served from local cache — no SAP connection required.
 * Covers: package hierarchy, key classes, data model, notifications, feature toggles,
 * customization tables, automation framework, RAP/BOPF architecture.
 */
@Slf4j
@Component
public class CddKnowledgeBaseHandler extends AbstractCddHandler {

    private static final String KNOWLEDGE_BASE_RESOURCE = "/cdd-knowledge-base.md";

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .buildTool("cdd_knowledge_base",
                        "Returns the comprehensive architecture knowledge base for the CDD (Configuration Delivery Document) " +
                        "packages /HEC1/CP_CDD (CDD v1, BOPF) and /HEC1/CP_NCDD (CDD 2.0, RAP). " +
                        "Covers: package hierarchy, class roles, data models (CDD_HEAD, NCDD_HEAD), " +
                        "notification system (CL_CDD_EMAIL + events), feature toggles (CL_PROV_UTILITY.is_feature_enabled), " +
                        "customization tables (CDD_CUSTOM, CDDBOPFCNF, CDD_CR, CDD_ACTION), " +
                        "automation framework (factory + polymorphic calculators), RAP BDEFs, IBP integration. " +
                        "Always check this first before querying SAP for CDD/NCDD questions. " +
                        "No SAP connection required — served from local cache.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        log.info("cdd_knowledge_base: serving from local cache");

        // 1. Check persistent cache file first
        Path cachedKb = cacheConfig.getKnowledgeBaseFile();
        if (Files.exists(cachedKb)) {
            try {
                String content = Files.readString(cachedKb, StandardCharsets.UTF_8);
                return success(content);
            } catch (IOException e) {
                log.warn("cdd_knowledge_base: failed to read cached file, trying bundled resource", e);
            }
        }

        // 2. Fall back to bundled resource in JAR
        try (InputStream is = getClass().getResourceAsStream(KNOWLEDGE_BASE_RESOURCE)) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                // Save to cache for next time
                try {
                    Files.writeString(cachedKb, content, StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
                return success(content);
            }
        } catch (IOException e) {
            log.warn("cdd_knowledge_base: failed to read bundled resource", e);
        }

        // 3. Return embedded fallback summary
        return success(EMBEDDED_KB);
    }

    // Minimal embedded fallback in case both file and resource are unavailable
    private static final String EMBEDDED_KB =
        "# CDD/NCDD Knowledge Base\n\n" +
        "## Packages\n" +
        "- `/HEC1/CP_CDD` — CDD v1 (BOPF-based): sub-packages APP, DDIC, IBP, UI, REPORTING, ODATA, CONFIG\n" +
        "- `/HEC1/CP_NCDD` — CDD 2.0 (RAP-based): sub-packages APP, AUTO, CDS, DDIC, TEMP, BUSINESS_SERVICE, NOTIFICATION\n\n" +
        "## CDD v1 Key Classes\n" +
        "- `CL_CDD_MANAGER` — singleton facade; entry: `get_instance(config_key)`\n" +
        "- `CL_CDD_CONFIG_BUFFER` — caches all 30+ entity types\n" +
        "- `CL_CDD_EMAIL` — email notifications; 7 events; feature gating via `CL_PROV_UTILITY.is_feature_enabled`\n" +
        "- `CL_CDD_CR_CUSTOMIZING` — CR customization logic\n\n" +
        "## CDD 2.0 Key Objects\n" +
        "- `I_NCDD_WORKLIST` (BDEF) — RAP root; draft-enabled; 13 events\n" +
        "- `BPCL_I_NCDD_WORKLIST` — root behavior implementation\n" +
        "- `CL_NCDD_AUTO_FACTORY` — creates automation calculators by level_id\n\n" +
        "## Key Tables\n" +
        "- `/HEC1/CDD_HEAD` — CDD v1 header\n" +
        "- `/HEC1/NCDD_HEAD` — CDD 2.0 header\n" +
        "- `/HEC1/CDD_CUSTOM` — customization params + email config\n" +
        "- `/HEC1/CDD_ACTION` — audit log\n\n" +
        "## Feature Toggles\n" +
        "Gated via `/HEC1/CL_PROV_UTILITY=>is_feature_enabled(iv_feature_id)`.\n" +
        "Known IDs: CDD_NOTIF_26285, CDD_NOTIF_33189, CDD_NOTIF_26257\n\n" +
        "Use `cdd_local_get_code` to retrieve full source code for any class or object.";
}
