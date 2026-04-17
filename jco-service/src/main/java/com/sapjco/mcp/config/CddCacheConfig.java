package com.sapjco.mcp.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for the local CDD/NCDD code cache.
 *
 * Cache directory priority:
 *   1. CDD_CACHE_DIR environment variable
 *   2. Default: {user.home}/sap-ai-assistant/cdd-cache
 */
@Slf4j
@Component
public class CddCacheConfig {

    private static final String ENV_CACHE_DIR = "CDD_CACHE_DIR";
    private static final String DEFAULT_SUBDIR = "sap-ai-assistant/cdd-cache";

    @Getter
    private Path cacheDir;

    @PostConstruct
    public void init() {
        String envDir = System.getenv(ENV_CACHE_DIR);
        if (envDir != null && !envDir.isBlank()) {
            cacheDir = Paths.get(envDir);
        } else {
            cacheDir = Paths.get(System.getProperty("user.home"), DEFAULT_SUBDIR);
        }

        try {
            Files.createDirectories(cacheDir);
            log.info("CDD cache directory: {}", cacheDir);
        } catch (IOException e) {
            log.error("Failed to create CDD cache directory: {}", cacheDir, e);
        }
    }

    public Path getIndexFile() {
        return cacheDir.resolve("index.json");
    }

    public Path getKnowledgeBaseFile() {
        return cacheDir.resolve("knowledge-base.md");
    }

    public Path getObjectDir(String objectType) {
        return cacheDir.resolve(objectType);
    }

    public boolean isCacheWarmedUp() {
        Path indexFile = getIndexFile();
        if (!Files.exists(indexFile)) return false;
        try {
            return Files.size(indexFile) > 100;
        } catch (IOException e) {
            return false;
        }
    }
}
