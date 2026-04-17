package com.sapjco.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sapjco.mcp.config.CddCacheConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the local offline code cache for /HEC1/CP_CDD and /HEC1/CP_NCDD packages.
 *
 * <p>Cache strategy:
 * <ol>
 *   <li>All 379 source objects pre-indexed on startup via {@link #initIndex()}</li>
 *   <li>Source files downloaded lazily from SAP on first access or via bulk warm-up</li>
 *   <li>index.json tracks metadata: type, package, description, path, cached_at, size_bytes</li>
 * </ol>
 *
 * <p>SAP fetching is NOT done here — it lives in {@code CddCacheDownloadRunner} and
 * {@code CddLocalGetCodeHandler}, which have proper JCo session context.
 */
@Slf4j
@Service
public class CddCodeCacheService {

    // ── Cache entry model ─────────────────────────────────────────────────────

    @Data
    public static class CacheEntry {
        private String name;
        private String type;          // class / interface / program / function_group / cds_view / behavior_definition
        private String pkg;           // SAP package
        private String description;
        private String path;          // relative path within cache dir
        private String cachedAt;      // ISO-8601 timestamp, null if not yet downloaded
        private long sizeBytes;
        private boolean failed;       // true if last download attempt failed
    }

    @Data
    public static class CacheStats {
        private int total;
        private int downloaded;
        private int failed;
        private int skipped;
    }

    // ── Static object catalogue ───────────────────────────────────────────────
    // Known source objects across /HEC1/CP_CDD and /HEC1/CP_NCDD.
    // Format: { name, type, package, description }

    public static final List<String[]> OBJECT_CATALOGUE = Arrays.asList(
        // ── /HEC1/CP_CDD_APP ─────────────────────────────────────────────────
        new String[]{"/HEC1/CL_CDD_MANAGER",           "class",    "/HEC1/CP_CDD_APP", "CDD Manager - Main facade"},
        new String[]{"/HEC1/CL_CDD_CONFIG_BUFFER",     "class",    "/HEC1/CP_CDD_APP", "CDD Configuration Buffer"},
        new String[]{"/HEC1/CL_CDD_BOPF_UPDATE",       "class",    "/HEC1/CP_CDD_APP", "CDD BOPF Update"},
        new String[]{"/HEC1/CL_CDD_AUTH_CHECK",        "class",    "/HEC1/CP_CDD_APP", "CDD Authorization Check"},
        new String[]{"/HEC1/CL_CDD_VERSION_COMPARE",   "class",    "/HEC1/CP_CDD_APP", "CDD Version Compare"},
        new String[]{"/HEC1/CL_CDD_STATUS_MACHINE",    "class",    "/HEC1/CP_CDD_APP", "CDD Status Machine"},
        new String[]{"/HEC1/CL_CDD_COPY",              "class",    "/HEC1/CP_CDD_APP", "CDD Copy"},
        new String[]{"/HEC1/CL_CDD_VALIDATOR",         "class",    "/HEC1/CP_CDD_APP", "CDD Validator"},
        new String[]{"/HEC1/CL_CDD_EMAIL",             "class",    "/HEC1/CP_CDD_APP", "CDD Notifications - Email"},
        new String[]{"/HEC1/CL_CDD_EMAIL_TEMPLATE",    "class",    "/HEC1/CP_CDD_APP", "CDD Email Template"},
        new String[]{"/HEC1/CL_CDD_PDF_OUTPUT",        "class",    "/HEC1/CP_CDD_APP", "CDD PDF Output"},
        new String[]{"/HEC1/CL_CDD_CR_CUSTOMIZING",    "class",    "/HEC1/CP_CDD_APP", "CDD CR Customizing"},
        new String[]{"/HEC1/CL_CDD_UPDATE_ADDL_CONF",  "class",    "/HEC1/CP_CDD_APP", "CDD Update Additional Configuration"},
        new String[]{"/HEC1/CDD_CUSTOMIZE",            "class",    "/HEC1/CP_CDD_APP", "CDD Read Customize Data"},
        new String[]{"/HEC1/CL_CDD_READ_CONFIG_DATA",  "class",    "/HEC1/CP_CDD_DDIC","CDD Read Configuration data"},
        new String[]{"/HEC1/IF_CDD_MANAGER",           "interface","/HEC1/CP_CDD_APP", "CDD Manager Interface"},
        new String[]{"/HEC1/IF_CDD_CONSTANTS",         "interface","/HEC1/CP_CDD_APP", "CDD Constants Interface"},
        new String[]{"/HEC1/IF_CDD_STATUS",            "interface","/HEC1/CP_CDD_APP", "CDD Status Constants"},
        new String[]{"/HEC1/IF_CDD_AUTH",              "interface","/HEC1/CP_CDD_APP", "CDD Authorization Interface"},
        new String[]{"/HEC1/IF_CDD_EMAIL",             "interface","/HEC1/CP_CDD_APP", "CDD Email Interface"},
        // ── /HEC1/CP_CDD_REPORTING ───────────────────────────────────────────
        new String[]{"/HEC1/CL_CDD_CTC_NOTIF",         "class",    "/HEC1/CP_CDD_REPORTING","CDD Send Email Notification for CTC"},
        new String[]{"/HEC1/CDD_CTC_ASSIGN_NOTIF",      "program",  "/HEC1/CP_CDD_REPORTING","FD CDD: Notification Reminder CTC SPOC"},
        new String[]{"/HEC1/R_HEADER_CDD",              "cds_view", "/HEC1/CP_CDD_REPORTING","CDD Header CDS View"},
        // ── /HEC1/CP_CDD_IBP ─────────────────────────────────────────────────
        new String[]{"/HEC1/CL_CDD_IBP_PROC_INBOUND",  "class",    "/HEC1/CP_CDD_IBP","CDD IBP Process Inbound"},
        new String[]{"/HEC1/CL_CDD_IBP_PARSER",         "class",    "/HEC1/CP_CDD_IBP","CDD IBP Payload Parser"},
        new String[]{"/HEC1/IF_CDD_IBP_PROCESSOR",      "interface","/HEC1/CP_CDD_IBP","CDD IBP Processor Interface"},
        // ── /HEC1/CP_NCDD_APP ────────────────────────────────────────────────
        new String[]{"/HEC1/CL_NCDD_MANAGER",           "class",    "/HEC1/CP_NCDD_APP","NCDD Manager CDD 2.0"},
        new String[]{"/HEC1/CL_NCDD_READ_CONFIG_DATA",  "class",    "/HEC1/CP_NCDD_APP","NCDD Read Configuration data"},
        new String[]{"/HEC1/CL_NCDD_EV_BOPF_UPD_HNDL", "class",    "/HEC1/CP_NCDD_APP","NCDD RAP Event BOPF Update Handler"},
        new String[]{"/HEC1/CL_NCDD_VALIDATOR",         "class",    "/HEC1/CP_NCDD_APP","NCDD Validator"},
        new String[]{"/HEC1/CL_NCDD_AUTH_CHECK",        "class",    "/HEC1/CP_NCDD_APP","NCDD Authorization Check"},
        new String[]{"/HEC1/CL_NCDD_STATUS_MACHINE",    "class",    "/HEC1/CP_NCDD_APP","NCDD Status Machine"},
        new String[]{"/HEC1/CL_NCDD_COPY",              "class",    "/HEC1/CP_NCDD_APP","NCDD Copy"},
        new String[]{"/HEC1/CL_NCDD_PDF_OUTPUT",        "class",    "/HEC1/CP_NCDD_APP","NCDD PDF Output"},
        new String[]{"/HEC1/CL_NCDD_VERSION_COMPARE",   "class",    "/HEC1/CP_NCDD_APP","NCDD Version Compare"},
        // ── /HEC1/CP_NCDD_CDS ────────────────────────────────────────────────
        new String[]{"BPCL_I_NCDD_WORKLIST",            "class",    "/HEC1/CP_NCDD_CDS","RAP Root Behavior Implementation NCDD"},
        new String[]{"BPCL_I_NCDD_GEN_INFO",            "class",    "/HEC1/CP_NCDD_CDS","RAP General Info Behavior"},
        new String[]{"BPCL_I_NCDD_PROJPLAN",            "class",    "/HEC1/CP_NCDD_CDS","RAP Project Plan Behavior"},
        new String[]{"BPCL_I_NCDD_RISKS",               "class",    "/HEC1/CP_NCDD_CDS","RAP Risks Behavior"},
        new String[]{"BPCL_I_NCDD_ASSUM",               "class",    "/HEC1/CP_NCDD_CDS","RAP Assumptions Behavior"},
        new String[]{"/HEC1/I_NCDD_WORKLIST",           "behavior_definition","/HEC1/CP_NCDD_CDS","NCDD Worklist Interface BDEF - draft-enabled, 13 events"},
        new String[]{"/HEC1/C_NCDD_WORKLIST",           "behavior_definition","/HEC1/CP_NCDD_CDS","NCDD Worklist Consumption BDEF"},
        new String[]{"/HEC1/I_NCDD_WORKLIST_HEAD",      "cds_view", "/HEC1/CP_NCDD_CDS","NCDD Worklist Header CDS Interface"},
        new String[]{"/HEC1/C_NCDD_WORKLIST_HEAD",      "cds_view", "/HEC1/CP_NCDD_CDS","NCDD Worklist Header Consumption CDS"},
        new String[]{"/HEC1/I_NCDD_OVERVIEW",           "cds_view", "/HEC1/CP_NCDD_CDS","NCDD Overview Interface CDS"},
        new String[]{"/HEC1/C_NCDD_OVERVIEW",           "cds_view", "/HEC1/CP_NCDD_CDS","NCDD Overview Consumption CDS"},
        new String[]{"/HEC1/I_NCDD_PROJECT_PLAN",       "cds_view", "/HEC1/CP_NCDD_CDS","NCDD Project Plan Interface CDS"},
        new String[]{"/HEC1/C_NCDD_PROJECT_PLAN",       "cds_view", "/HEC1/CP_NCDD_CDS","NCDD Project Plan Consumption CDS"},
        new String[]{"/HEC1/I_NCDD_ASSUM",              "cds_view", "/HEC1/CP_NCDD_CDS","NCDD Assumptions Interface CDS"},
        new String[]{"/HEC1/I_NCDD_RISK",               "cds_view", "/HEC1/CP_NCDD_CDS","NCDD Risk Interface CDS"},
        // ── /HEC1/CP_NCDD_AUTO ───────────────────────────────────────────────
        new String[]{"/HEC1/CL_NCDD_AUTO_FACTORY",      "class",    "/HEC1/CP_NCDD_AUTO","NCDD Automation Factory - creates calculators by level_id"},
        new String[]{"/HEC1/CL_NCDD_AUTO_LOGGER",       "class",    "/HEC1/CP_NCDD_AUTO","NCDD Automation Logger - JSON audit trail"},
        new String[]{"/HEC1/CL_NCDD_AUTO_CONFIG_HELP",  "class",    "/HEC1/CP_NCDD_AUTO","NCDD Automation Configuration Helper"},
        new String[]{"/HEC1/IF_NCDD_AUTO_CALC",         "interface","/HEC1/CP_NCDD_AUTO","NCDD Automation Calculator Interface"},
        new String[]{"/HEC1/IF_NCDD_AUTO_CONFIG",       "interface","/HEC1/CP_NCDD_AUTO","NCDD Automation Configuration Interface - 19 level IDs"},
        new String[]{"/HEC1/I_NCDD_AUTO_HEAD",          "cds_view", "/HEC1/CP_NCDD_AUTO","NCDD Automation Header Interface CDS"},
        new String[]{"/HEC1/C_NCDD_AUTO_HEAD",          "cds_view", "/HEC1/CP_NCDD_AUTO","NCDD Automation Header Consumption CDS"},
        new String[]{"/HEC1/I_NCDD_AUTO_RESULTS",       "cds_view", "/HEC1/CP_NCDD_AUTO","NCDD Automation Results Interface CDS"},
        // ── /HEC1/CP_NCDD_TEMP (N-suffix next-gen RAP) ───────────────────────
        new String[]{"/HEC1/I_NCDD_WORKLIST_N",         "behavior_definition","/HEC1/CP_NCDD_TEMP","NCDD Worklist N Interface BDEF (next-gen)"},
        new String[]{"/HEC1/C_NCDD_WORKLIST_N",         "behavior_definition","/HEC1/CP_NCDD_TEMP","NCDD Worklist N Consumption BDEF (next-gen)"},
        // ── /HEC1/CP_NCDD_NOTIFICATION ───────────────────────────────────────
        new String[]{"/HEC1/NCDD_NOTIF_CDS",            "cds_view", "/HEC1/CP_NCDD_NOTIFICATION","NCDD Notifications CDS View"}
    );

    // ── Dependencies ─────────────────────────────────────────────────────────

    @Autowired
    private CddCacheConfig cacheConfig;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Map<String, CacheEntry> index = new ConcurrentHashMap<>();

    // ── Initialisation ────────────────────────────────────────────────────────

    @PostConstruct
    public void initIndex() {
        Path indexFile = cacheConfig.getIndexFile();
        if (Files.exists(indexFile)) {
            try {
                Map<String, CacheEntry> loaded = mapper.readValue(
                        indexFile.toFile(), new TypeReference<Map<String, CacheEntry>>() {});
                index.putAll(loaded);
                log.info("CDD cache: loaded {} entries from index", index.size());
                return;
            } catch (IOException e) {
                log.warn("CDD cache: failed to read index, rebuilding. Cause: {}", e.getMessage());
            }
        }
        rebuildIndexFromCatalogue();
    }

    /**
     * Hot-reload the index from disk (called by PipelineController after sync-java-cache.js).
     * Clears the in-memory index and re-reads index.json + static catalogue.
     */
    public void reloadIndex() {
        index.clear();
        initIndex();
    }

    /** Rebuild index entries from the static catalogue (no downloads). */
    public void rebuildIndexFromCatalogue() {
        for (String[] row : OBJECT_CATALOGUE) {
            String key = row[0].toUpperCase();
            if (!index.containsKey(key)) {
                CacheEntry entry = new CacheEntry();
                entry.setName(row[0]);
                entry.setType(row[1]);
                entry.setPkg(row[2]);
                entry.setDescription(row[3]);
                entry.setPath(row[1] + "/" + sanitizeName(row[0]) + extFor(row[1]));
                index.put(key, entry);
            }
        }
        saveIndex();
        log.info("CDD cache: built index with {} entries", index.size());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isCached(String objectName) {
        CacheEntry e = index.get(objectName.toUpperCase());
        if (e == null) return false;
        if (e.getCachedAt() == null || e.isFailed()) return false;
        return Files.exists(cacheConfig.getCacheDir().resolve(e.getPath()));
    }

    public Optional<Path> getCachedPath(String objectName) {
        if (!isCached(objectName)) return Optional.empty();
        CacheEntry e = index.get(objectName.toUpperCase());
        return Optional.of(cacheConfig.getCacheDir().resolve(e.getPath()));
    }

    public Optional<CacheEntry> getEntry(String objectName) {
        return Optional.ofNullable(index.get(objectName.toUpperCase()));
    }

    /** Save downloaded source to cache and update index. */
    public Path saveToCache(String objectName, String objectType, String source) throws IOException {
        String key = objectName.toUpperCase();
        CacheEntry entry = index.computeIfAbsent(key, k -> {
            CacheEntry e = new CacheEntry();
            e.setName(objectName);
            e.setType(objectType);
            e.setPath(objectType + "/" + sanitizeName(objectName) + extFor(objectType));
            return e;
        });

        Path filePath = cacheConfig.getCacheDir().resolve(entry.getPath());
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, source, StandardCharsets.UTF_8);

        entry.setCachedAt(Instant.now().toString());
        entry.setSizeBytes(Files.size(filePath));
        entry.setFailed(false);
        saveIndex();

        log.debug("CDD cache: saved {} ({} bytes)", objectName, entry.getSizeBytes());
        return filePath;
    }

    /** Mark an object as failed (download error). */
    public void markFailed(String objectName) {
        CacheEntry entry = index.get(objectName.toUpperCase());
        if (entry != null) {
            entry.setFailed(true);
            saveIndex();
        }
    }

    /**
     * Search indexed objects by query string.
     * Matches against name, description, and package (case-insensitive).
     * Optionally filter by object type.
     */
    public List<CacheEntry> search(String query, String objectType) {
        String q = query.toLowerCase();
        boolean filterType = objectType != null && !objectType.isBlank() && !"all".equalsIgnoreCase(objectType);

        return index.values().stream()
                .filter(e -> {
                    if (filterType && !objectType.equalsIgnoreCase(e.getType())) return false;
                    String name = e.getName() != null ? e.getName().toLowerCase() : "";
                    String desc = e.getDescription() != null ? e.getDescription().toLowerCase() : "";
                    String pkg  = e.getPkg() != null ? e.getPkg().toLowerCase() : "";
                    return name.contains(q) || desc.contains(q) || pkg.contains(q);
                })
                .sorted(Comparator.comparing(e -> e.getName() != null ? e.getName() : ""))
                .collect(Collectors.toList());
    }

    public Map<String, CacheEntry> getIndex() {
        return Collections.unmodifiableMap(index);
    }

    /** All objects that have not yet been downloaded. */
    public List<CacheEntry> getUncachedEntries() {
        return index.values().stream()
                .filter(e -> !isCached(e.getName()))
                .collect(Collectors.toList());
    }

    public int getTotalKnownObjects() {
        return OBJECT_CATALOGUE.size();
    }

    /** ADT URL for fetching source of a given object type. */
    public String buildAdtUrl(String objectType, String objectName) {
        String encoded = objectName.replace("/", "%2f");
        return switch (objectType) {
            case "class"               -> "/sap/bc/adt/oo/classes/" + encoded + "/source/main";
            case "interface"           -> "/sap/bc/adt/oo/interfaces/" + encoded + "/source/main";
            case "program"             -> "/sap/bc/adt/programs/programs/" + encoded + "/source/main";
            case "function_group"      -> "/sap/bc/adt/functions/groups/" + encoded + "/source/main";
            case "cds_view"            -> "/sap/bc/adt/ddic/ddl/sources/" + encoded + "/source/main";
            case "behavior_definition" -> "/sap/bc/adt/bo/behaviordefinitions/" + encoded + "/source/main";
            default -> null;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extFor(String objectType) {
        return "cds_view".equals(objectType) ? ".ddl" : ".abap";
    }

    private String sanitizeName(String name) {
        return name.replace("/", "_").replace(" ", "_");
    }

    private void saveIndex() {
        try {
            mapper.writeValue(cacheConfig.getIndexFile().toFile(), index);
        } catch (IOException e) {
            log.error("CDD cache: failed to save index", e);
        }
    }
}
