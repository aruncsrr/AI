package com.sapjco.mcp.service;

import com.sapjco.mcp.config.CddCacheConfig;
import com.sapjco.mcp.config.SystemConfigLoader;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.service.CddCodeCacheService.CacheEntry;
import com.sapjco.mcp.service.CddCodeCacheService.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs at application startup to warm up the local CDD/NCDD code cache.
 *
 * <p>If the cache is already fully populated, skips download. Individual failures
 * are logged and marked in the index — startup is never blocked.
 */
@Slf4j
@Component
public class CddCacheDownloadRunner implements ApplicationRunner {

    @Autowired
    private CddCodeCacheService cacheService;

    @Autowired
    private CddCacheConfig cacheConfig;

    @Autowired
    private JcoSessionManager jcoSessionManager;

    @Autowired
    private AdtClient adtClient;

    @Autowired
    private SystemConfigLoader systemConfigLoader;

    @Override
    public void run(ApplicationArguments args) {
        List<CacheEntry> uncached = cacheService.getUncachedEntries();
        int total = cacheService.getTotalKnownObjects();
        int alreadyCached = total - uncached.size();

        if (uncached.isEmpty()) {
            log.info("CDD cache: all {} objects already cached. Skipping download.", total);
            return;
        }

        log.info("CDD cache: {}/{} objects cached. Downloading {} missing objects...",
                alreadyCached, total, uncached.size());

        String systemId = getDefaultSystemId();
        if (systemId == null) {
            log.warn("CDD cache: no SAP system configured — download deferred. " +
                    "Objects will be fetched on-demand when requested.");
            return;
        }

        CacheStats stats = downloadUncached(uncached, systemId);

        log.info("CDD cache warm-up done: downloaded={}, failed={}, skipped={}",
                stats.getDownloaded(), stats.getFailed(), stats.getSkipped());
    }

    private CacheStats downloadUncached(List<CacheEntry> uncached, String systemId) {
        CacheStats stats = new CacheStats();
        stats.setTotal(uncached.size());
        int done = 0;

        for (CacheEntry entry : uncached) {
            try {
                String adtUrl = cacheService.buildAdtUrl(entry.getType(), entry.getName());
                if (adtUrl == null) {
                    stats.setSkipped(stats.getSkipped() + 1);
                    done++;
                    continue;
                }

                // Create a single-use temp session for this download
                CreateSessionRequest req = new CreateSessionRequest();
                req.setSystemId(systemId);
                String tempSession = jcoSessionManager.createSession(req);
                try {
                    String source = jcoSessionManager.executeInContext(tempSession,
                            (dest, session) -> adtClient.getSourceCodeViaRfc(
                                    dest, session, adtUrl, null, "text/plain"));
                    cacheService.saveToCache(entry.getName(), entry.getType(), source);
                    stats.setDownloaded(stats.getDownloaded() + 1);
                } finally {
                    try { jcoSessionManager.destroySession(tempSession); } catch (Exception ignored) {}
                }

            } catch (Exception e) {
                log.warn("CDD cache: failed to download '{}' — {}", entry.getName(), e.getMessage());
                cacheService.markFailed(entry.getName());
                stats.setFailed(stats.getFailed() + 1);
            }

            done++;
            if (done % 10 == 0) {
                log.info("CDD cache: {}/{} processed (ok={}, fail={})",
                        done, uncached.size(), stats.getDownloaded(), stats.getFailed());
            }
        }
        return stats;
    }

    private String getDefaultSystemId() {
        try {
            return systemConfigLoader.getDefaultSystemId();
        } catch (Exception e) {
            log.debug("CDD cache: could not resolve default system: {}", e.getMessage());
            return null;
        }
    }
}
