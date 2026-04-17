package com.sapjco.mcp.service;

import com.sap.conn.jco.JCoContext;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sapjco.mcp.config.JcoConfiguration;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages JCo stateful sessions with thread affinity.
 *
 * This is the core component that maintains SAP's JCoContext for each session.
 * The JCoContext.begin() call creates a stateful connection to SAP, and all
 * subsequent operations within that context are executed in the same SAP session.
 *
 * CRITICAL: Each session has a dedicated single-threaded executor to ensure
 * all operations execute on the same thread. This maintains the JCoContext
 * binding to the same JCo connection and SAP work process, which is essential
 * for lock preservation across operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JcoSessionManager {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private final JcoConfiguration jcoConfiguration;

    /**
     * Session keep-alive interval in minutes.
     * ADT Eclipse uses a similar ping mechanism to prevent session timeout.
     * Default SAP HTTP session timeout is typically 5-15 minutes.
     */
    private static final int KEEP_ALIVE_INTERVAL_MINUTES = 3;

    /**
     * Minimum time since last activity before pinging (avoid unnecessary requests).
     */
    private static final Duration KEEP_ALIVE_IDLE_THRESHOLD = Duration.ofMinutes(2);

    @Value("${SAP_ADT_URL:}")
    private String adtBaseUrl;

    @Value("${SAP_USERNAME:}")
    private String sapUsername;

    @Value("${SAP_PASSWORD:}")
    private String sapPassword;

    @Value("${SAP_CLIENT:}")
    private String sapClient;

    /**
     * Scheduled executor for keep-alive pings across all sessions.
     */
    private final ScheduledExecutorService keepAliveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("session-keep-alive");
        t.setDaemon(true);
        return t;
    });

    /**
     * Session context that binds a JcoSession to a dedicated thread executor.
     * All operations for this session will execute on the same thread, ensuring
     * the JCoContext remains bound to the same JCo connection.
     */
    private static class SessionContext {
        final JcoSession session;
        final ExecutorService executor;

        SessionContext(JcoSession session) {
            this.session = session;
            // CRITICAL: Single thread ensures same connection throughout session
            this.executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r);
                t.setName("jco-session-" + THREAD_COUNTER.incrementAndGet() + "-" +
                         session.getSessionId().substring(0, 8));
                t.setDaemon(false); // Ensure thread completes work before shutdown
                return t;
            });
        }

        void shutdown() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Executor did not terminate in time, forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for executor shutdown", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    /**
     * Initialize the session keep-alive scheduler.
     * Runs periodically to ping SAP for all idle sessions to prevent HTTP session timeout.
     *
     * This matches the ADT Eclipse plugin behavior (HttpSessionKeepAliveJob) which prevents
     * the SAP server from closing stateful sessions due to inactivity.
     */
    @PostConstruct
    public void initKeepAlive() {
        log.info("Starting session keep-alive scheduler (interval: {} minutes, idle threshold: {})",
                 KEEP_ALIVE_INTERVAL_MINUTES, KEEP_ALIVE_IDLE_THRESHOLD);

        keepAliveScheduler.scheduleAtFixedRate(
            this::pingIdleSessions,
            KEEP_ALIVE_INTERVAL_MINUTES,
            KEEP_ALIVE_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
    }

    /**
     * Shutdown the keep-alive scheduler when the service is destroyed.
     */
    @PreDestroy
    public void shutdownKeepAlive() {
        log.info("Shutting down session keep-alive scheduler");
        keepAliveScheduler.shutdown();
        try {
            if (!keepAliveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                keepAliveScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            keepAliveScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Ping all idle sessions to keep their HTTP sessions alive.
     * Only pings sessions that have been idle for at least KEEP_ALIVE_IDLE_THRESHOLD.
     */
    private void pingIdleSessions() {
        if (sessions.isEmpty()) {
            log.debug("No active sessions to keep alive");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, SessionContext> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            SessionContext ctx = entry.getValue();
            JcoSession session = ctx.session;

            Duration timeSinceLastAccess = Duration.between(session.getLastAccessedAt(), now);

            if (timeSinceLastAccess.compareTo(KEEP_ALIVE_IDLE_THRESHOLD) >= 0) {
                log.debug("Pinging idle session {} (idle for {})", sessionId, timeSinceLastAccess);
                try {
                    pingSession(session);
                    log.debug("✓ Keep-alive ping successful for session {}", sessionId);
                } catch (Exception e) {
                    log.warn("Keep-alive ping failed for session {}: {}", sessionId, e.getMessage());
                    // Don't destroy session - let the next actual operation handle the failure
                }
            } else {
                log.debug("Session {} is active (last accessed {}), skipping ping", sessionId, timeSinceLastAccess);
            }
        }
    }

    /**
     * Ping the SAP server to keep the HTTP session alive.
     * Uses GET /sap/bc/adt/core/discovery which is a lightweight endpoint.
     */
    private void pingSession(JcoSession session) throws Exception {
        if (adtBaseUrl == null || adtBaseUrl.isEmpty()) {
            log.warn("ADT base URL not configured, skipping keep-alive ping");
            return;
        }

        OkHttpClient client = session.getHttpClient();
        String pingUrl = adtBaseUrl + "/core/discovery";

        Request request = new Request.Builder()
                .url(pingUrl)
                .get()
                .addHeader("Authorization", getBasicAuth())
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Keep-alive ping returned {}: {}", response.code(), response.message());
            }
            // Update last accessed time to reset the idle timer
            session.updateLastAccessed();
        }
    }

    /**
     * Create Basic Auth header value for keep-alive requests.
     */
    private String getBasicAuth() {
        String credentials = sapUsername + ":" + sapPassword;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(
            credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Create a new JCo stateful session with dedicated thread affinity.
     *
     * This begins a JCoContext on a dedicated thread which must remain active
     * for the entire lifecycle of operations (lock, save, unlock, activate).
     *
     * @return Session ID
     * @throws JCoException if JCo context cannot be created
     */
    public String createSession() throws JCoException {
        return createSession(null);
    }

    /**
     * Create a new JCo stateful session with dedicated thread affinity.
     *
     * This begins a JCoContext on a dedicated thread which must remain active
     * for the entire lifecycle of operations (lock, save, unlock, activate).
     *
     * @param request Optional request containing system configuration
     * @return Session ID
     * @throws JCoException if JCo context cannot be created
     */
    public String createSession(CreateSessionRequest request) throws JCoException {
        String sessionId = UUID.randomUUID().toString();

        // Determine which destination to use
        String destinationName;
        String systemId;
        boolean enableHttpSso = false;

        if (request != null && request.hasSystemConfig()) {
            // Create or get destination for the specified system
            systemId = request.getSystemId();

            // Check if HTTP SSO should be enabled
            enableHttpSso = request.isEnableHttpSso();
            if (enableHttpSso) {
                log.info("HTTP SSO enabled for session {} - will use OS keystore certificates", sessionId);
            }

            // Check if SNC authentication is configured
            if (request.hasSncConfig()) {
                // Use SNC authentication
                CreateSessionRequest.SncConfig snc = request.getSnc();
                destinationName = jcoConfiguration.getOrCreateDestination(
                    request.getSystemId(),
                    request.getHost(),
                    request.getSysnr(),
                    request.getClient(),
                    request.getUsername(),  // May be null for SNC
                    request.getPassword(),  // May be null for SNC
                    snc.getPartnername(),
                    snc.getQop(),
                    snc.getLib(),
                    request.getSaprouter()  // SAP Router for firewall traversal
                );
                log.info("Creating JCo session for system {} with SNC: {} (destination: {})",
                         systemId, sessionId, destinationName);
            } else {
                // Use basic authentication
                destinationName = jcoConfiguration.getOrCreateDestination(
                    request.getSystemId(),
                    request.getHost(),
                    request.getSysnr(),
                    request.getClient(),
                    request.getUsername(),
                    request.getPassword()
                );
                log.info("Creating JCo session for system {}: {} (destination: {})",
                         systemId, sessionId, destinationName);
            }
        } else {
            // Use default destination
            systemId = "default";
            destinationName = JcoConfiguration.DEFAULT_DESTINATION_NAME;
            log.info("Creating JCo session (default system): {}", sessionId);
        }

        // Create session metadata with HTTP SSO flag
        JcoSession session = new JcoSession(
            sessionId,
            destinationName,
            LocalDateTime.now(),
            systemId,
            enableHttpSso
        );

        // Set host and client for RFC proxy headers
        if (request != null) {
            session.setHost(request.getHost());
            session.setClient(request.getClient());
        }

        // For SNC authentication, route HTTP through RFC (same pattern as Eclipse ADT)
        if (request != null && request.hasSncConfig()) {
            session.setUseRfcProxy(true);
            log.info("✓ RFC proxy enabled for SNC session - HTTP requests will route through SADT_REST_RFC_ENDPOINT");
        }

        // Create session context with dedicated thread
        SessionContext ctx = new SessionContext(session);
        sessions.put(sessionId, ctx);

        final String destName = destinationName;

        // CRITICAL: Execute JCoContext.begin() on the dedicated thread
        // This ensures all subsequent operations use the same thread and connection
        try {
            ctx.executor.submit(() -> {
                try {
                    JCoDestination dest = JCoDestinationManager.getDestination(destName);
                    JCoContext.begin(dest);
                    log.info("✓ JCoContext.begin() on thread: {} for session: {} (system: {})",
                        Thread.currentThread().getName(), sessionId, systemId);
                } catch (JCoException e) {
                    log.error("Failed to begin JCoContext on dedicated thread", e);
                    throw new RuntimeException(e);
                }
            }).get(10, TimeUnit.SECONDS);  // Wait for completion with timeout
        } catch (TimeoutException e) {
            sessions.remove(sessionId);
            ctx.shutdown();
            throw new JCoException(0, "Timeout while creating session: " + e.getMessage());
        } catch (ExecutionException e) {
            sessions.remove(sessionId);
            ctx.shutdown();
            Throwable cause = e.getCause();
            if (cause instanceof JCoException) {
                throw (JCoException) cause;
            }
            throw new JCoException(0, "Failed to create session: " + cause.getMessage());
        } catch (InterruptedException e) {
            sessions.remove(sessionId);
            ctx.shutdown();
            Thread.currentThread().interrupt();
            throw new JCoException(0, "Interrupted while creating session");
        }

        log.info("✓ Session created: {} for system {} (Total active sessions: {})",
                 sessionId, systemId, sessions.size());

        return sessionId;
    }

    /**
     * Destroy a JCo session and end its context on the dedicated thread.
     *
     * This releases the stateful connection to SAP and shuts down the thread executor.
     *
     * @param sessionId Session ID to destroy
     * @throws JCoException if context cannot be ended
     */
    public void destroySession(String sessionId) throws JCoException {
        log.info("Destroying JCo session: {}", sessionId);

        SessionContext ctx = sessions.remove(sessionId);

        if (ctx == null) {
            log.warn("Session not found: {}", sessionId);
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        // CRITICAL: End context on the same dedicated thread where it was created
        try {
            ctx.executor.submit(() -> {
                try {
                    JCoDestination dest = JCoDestinationManager.getDestination(
                        ctx.session.getDestinationName());
                    JCoContext.end(dest);
                    log.info("✓ JCoContext.end() on thread: {} for session: {}",
                        Thread.currentThread().getName(), sessionId);
                } catch (JCoException e) {
                    log.error("Failed to end JCoContext on dedicated thread", e);
                    // Don't throw - still need to cleanup executor
                }
            }).get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Timeout while ending JCoContext for session: {}", sessionId, e);
        } catch (ExecutionException e) {
            log.error("Failed to cleanly end JCoContext for session: {}", sessionId, e.getCause());
        } catch (InterruptedException e) {
            log.error("Interrupted while ending JCoContext for session: {}", sessionId, e);
            Thread.currentThread().interrupt();
        } finally {
            // Always shutdown the executor
            ctx.shutdown();
        }

        log.info("✓ Session destroyed: {} (Remaining sessions: {})",
                 sessionId, sessions.size());
    }

    /**
     * Get an active session.
     *
     * @param sessionId Session ID
     * @return JcoSession
     * @throws IllegalArgumentException if session not found
     */
    public JcoSession getSession(String sessionId) {
        SessionContext ctx = sessions.get(sessionId);

        if (ctx == null) {
            log.error("Session not found: {}", sessionId);
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        ctx.session.updateLastAccessed();
        return ctx.session;
    }

    /**
     * Get destination for a session.
     *
     * @param sessionId Session ID
     * @return JCoDestination
     * @throws JCoException if destination cannot be retrieved
     */
    public JCoDestination getDestination(String sessionId) throws JCoException {
        JcoSession session = getSession(sessionId);
        return JCoDestinationManager.getDestination(session.getDestinationName());
    }

    /**
     * Execute an operation within a JCo session context on the dedicated thread.
     *
     * This ensures the operation runs within the stateful session on the same
     * thread as JCoContext.begin(), maintaining connection affinity to the
     * same SAP work process.
     *
     * @param sessionId Session ID
     * @param operation Operation to execute
     * @return Operation result
     * @throws Exception if operation fails
     */
    public <T> T executeInContext(String sessionId, JcoOperation<T> operation) throws Exception {
        SessionContext ctx = sessions.get(sessionId);
        if (ctx == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        log.debug("Submitting operation for session {} to dedicated thread", sessionId);

        // CRITICAL: Execute on the SAME thread as JCoContext.begin()
        // This maintains the JCoContext binding to the same JCo connection
        Future<T> future = ctx.executor.submit(() -> {
            try {
                log.debug("Executing operation on thread: {} for session: {}",
                    Thread.currentThread().getName(), sessionId);
                JCoDestination dest = JCoDestinationManager.getDestination(
                    ctx.session.getDestinationName());
                return operation.execute(dest, ctx.session);
            } catch (Exception e) {
                log.error("Operation failed in session context on thread: {}",
                    Thread.currentThread().getName(), e);
                throw new RuntimeException(e);
            }
        });

        try {
            return future.get(60, TimeUnit.SECONDS);  // Wait with timeout
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Operation timed out after 60 seconds", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                Throwable actualCause = cause.getCause();
                if (actualCause instanceof Exception) {
                    throw (Exception) actualCause;
                }
            }
            throw new RuntimeException("Operation failed", cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation interrupted", e);
        }
    }

    /**
     * Execute an operation within a session context with a custom timeout.
     * <p>
     * Use this overload for long-running operations like unit tests.
     *
     * @param sessionId Session ID
     * @param operation Operation to execute
     * @param timeoutSeconds Custom timeout in seconds
     * @return Operation result
     * @throws Exception if operation fails
     */
    public <T> T executeInContext(String sessionId, JcoOperation<T> operation, int timeoutSeconds) throws Exception {
        SessionContext ctx = sessions.get(sessionId);
        if (ctx == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        log.debug("Submitting operation for session {} to dedicated thread (timeout: {}s)", sessionId, timeoutSeconds);

        // CRITICAL: Execute on the SAME thread as JCoContext.begin()
        // This maintains the JCoContext binding to the same JCo connection
        Future<T> future = ctx.executor.submit(() -> {
            try {
                log.debug("Executing operation on thread: {} for session: {}",
                    Thread.currentThread().getName(), sessionId);
                JCoDestination dest = JCoDestinationManager.getDestination(
                    ctx.session.getDestinationName());
                return operation.execute(dest, ctx.session);
            } catch (Exception e) {
                log.error("Operation failed in session context on thread: {}",
                    Thread.currentThread().getName(), e);
                throw new RuntimeException(e);
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Operation timed out after " + timeoutSeconds + " seconds", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                Throwable actualCause = cause.getCause();
                if (actualCause instanceof Exception) {
                    throw (Exception) actualCause;
                }
            }
            throw new RuntimeException("Operation failed", cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation interrupted", e);
        }
    }

    /**
     * Get count of active sessions.
     *
     * @return Number of active sessions
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * List all active sessions.
     *
     * @return List of active JcoSession instances
     */
    public java.util.List<JcoSession> listSessions() {
        return sessions.values().stream()
                .map(ctx -> ctx.session)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Functional interface for operations within JCo context.
     */
    @FunctionalInterface
    public interface JcoOperation<T> {
        T execute(JCoDestination destination, JcoSession session) throws Exception;
    }
}
