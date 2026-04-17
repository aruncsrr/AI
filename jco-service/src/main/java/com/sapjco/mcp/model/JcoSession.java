package com.sapjco.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Represents a JCo stateful session with dedicated HTTP client.
 */
@Slf4j
@Data
@AllArgsConstructor
public class JcoSession {
    private String sessionId;
    private String destinationName;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
    private OkHttpClient httpClient;
    private Map<String, String> csrfTokenCache;
    private String contextId;       // for sap-contextid header (ADT session context)
    private String saplbToken;      // for saplb header (load balancer affinity)
    private String systemId;        // SAP system ID this session is bound to
    private String connectionId;    // for sap-adt-connection-id header (session affinity)
    private boolean httpSsoEnabled; // Whether HTTP SSO via OS keystore is enabled
    private boolean useRfcProxy;    // Route HTTP through SADT_REST_RFC_ENDPOINT for SNC SSO

    // Lock tracking
    private String type = "jco";    // Session type (always "jco" for Java service)
    private String host;            // SAP host for display
    private String client;          // SAP client for display

    // Debug session tracking - for auto-cleanup on session destroy
    private String debugTerminalId;  // Terminal ID from debug session
    private String debugIdeId;       // IDE ID from debug session
    private String debugRequestUser; // User associated with debug session

    /**
     * Check if this session has an active debug session that needs cleanup.
     */
    public boolean hasActiveDebugSession() {
        return debugTerminalId != null && debugIdeId != null;
    }

    /**
     * Build parameters map for debug cleanup REST call.
     * Returns null if no active debug session.
     */
    public Map<String, String> buildDebugCleanupParams() {
        if (!hasActiveDebugSession()) {
            return null;
        }
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("debuggingMode", "user");
        params.put("terminalId", debugTerminalId);
        params.put("ideId", debugIdeId);
        if (debugRequestUser != null) {
            params.put("requestUser", debugRequestUser);
        }
        return params;
    }

    /**
     * Clear debug state after cleanup.
     */
    public void clearDebugState() {
        this.debugTerminalId = null;
        this.debugIdeId = null;
        this.debugRequestUser = null;
    }

    public JcoSession(String sessionId, String destinationName, LocalDateTime createdAt) {
        this(sessionId, destinationName, createdAt, "default", false);
    }

    public JcoSession(String sessionId, String destinationName, LocalDateTime createdAt, String systemId) {
        this(sessionId, destinationName, createdAt, systemId, false);
    }

    public JcoSession(String sessionId, String destinationName, LocalDateTime createdAt, String systemId, boolean enableHttpSso) {
        this.sessionId = sessionId;
        this.destinationName = destinationName;
        this.createdAt = createdAt;
        this.lastAccessedAt = createdAt;
        this.contextId = generateContextId();
        this.connectionId = generateContextId(); // Separate connection ID for sap-adt-connection-id
        this.httpSsoEnabled = enableHttpSso;
        this.httpClient = enableHttpSso ? createSsoHttpClient() : createHttpClient();
        this.csrfTokenCache = new ConcurrentHashMap<>();
        this.systemId = systemId != null ? systemId : "default";
    }

    /**
     * Generate ADT session context ID (UUID without hyphens).
     * This is required for SAP ADT session affinity.
     */
    private String generateContextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public void updateLastAccessed() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * Create a session-specific HTTP client with cookie jar.
     * CRITICAL: The same client instance must be used for all requests in the session!
     */
    private static OkHttpClient createHttpClient() {
        try {
            // Create session-specific cookie jar
            CookieJar cookieJar = new CookieJar() {
                private final Map<String, Cookie> cookieStore = new ConcurrentHashMap<>();

                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    System.err.println("[CookieJar] Saving " + cookies.size() + " cookies from: " + url);
                    for (Cookie cookie : cookies) {
                        cookieStore.put(cookie.name(), cookie);
                        System.err.println("  [CookieJar] - " + cookie.name() + " = "
                            + cookie.value().substring(0, Math.min(30, cookie.value().length())) + "... "
                            + "(domain: " + cookie.domain() + ", path: " + cookie.path()
                            + ", expires: " + new java.util.Date(cookie.expiresAt()) + ")");
                    }
                }

                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> validCookies = new ArrayList<>();
                    for (Cookie cookie : cookieStore.values()) {
                        // Check if cookie matches the URL
                        if (cookie.matches(url)) {
                            validCookies.add(cookie);
                        } else {
                            System.err.println("  [CookieJar] Cookie " + cookie.name()
                                + " does NOT match URL " + url
                                + " (domain: " + cookie.domain() + ", path: " + cookie.path() + ")");
                        }
                    }
                    System.err.println("[CookieJar] Loading " + validCookies.size() + " cookies for: " + url);
                    for (Cookie c : validCookies) {
                        System.err.println("  [CookieJar] + " + c.name());
                    }
                    return validCookies;
                }
            };

            // Trust all certificates (for development with self-signed certs)
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Add logging interceptor to see ACTUAL request headers sent over the wire
            Interceptor loggingInterceptor = new Interceptor() {
                @NotNull
                @Override
                public Response intercept(@NotNull Chain chain) throws java.io.IOException {
                    Request request = chain.request();

                    // Log the actual request that will be sent (after all interceptors)
                    log.info("🌐 [HTTP] Actual request to: {} {}", request.method(), request.url());

                    // Check for Cookie header in the ACTUAL request
                    String cookieHeader = request.header("Cookie");
                    if (cookieHeader != null && !cookieHeader.isEmpty()) {
                        log.info("✅ [HTTP] Cookie header PRESENT in wire request: {}",
                            cookieHeader.substring(0, Math.min(100, cookieHeader.length())) + "...");
                    } else {
                        log.warn("❌ [HTTP] Cookie header MISSING in wire request!");
                    }

                    // Log all headers
                    log.info("🌐 [HTTP] All request headers:");
                    for (String name : request.headers().names()) {
                        String value = request.headers().get(name);
                        if (name.equalsIgnoreCase("authorization")) {
                            value = value != null ? value.substring(0, Math.min(30, value.length())) + "..." : "null";
                        }
                        log.info("    {}: {}", name, value);
                    }

                    return chain.proceed(request);
                }
            };

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .cookieJar(cookieJar)
                    .addNetworkInterceptor(loggingInterceptor)  // Use network interceptor to see FINAL request
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create HTTP client for session", e);
        }
    }

    /**
     * Create a session-specific HTTP client for SSO sessions.
     * For SNC SSO, HTTP requests are routed through SADT_REST_RFC_ENDPOINT,
     * so this creates a basic HTTP client (RFC handles the authentication).
     */
    private static OkHttpClient createSsoHttpClient() {
        log.info("Creating session HTTP client for SSO (RFC proxy handles auth)");

        // For SNC SSO, we route HTTP through RFC, so create a basic client
        // The RFC call handles authentication - no client certificates needed
        return createHttpClient();
    }

    // Lock tracking - for simplicity, tracked as simple list
    private final List<String> locks = new ArrayList<>();

    /**
     * Get the number of active locks.
     */
    public int getLockCount() {
        return locks.size();
    }

    /**
     * Get list of locked object names.
     */
    public List<String> getLocks() {
        return new ArrayList<>(locks);
    }

    /**
     * Add a lock.
     */
    public void addLock(String objectName) {
        locks.add(objectName);
    }

    /**
     * Remove a lock.
     */
    public void removeLock(String objectName) {
        locks.remove(objectName);
    }

    /**
     * Get last used timestamp (alias for lastAccessedAt).
     */
    public LocalDateTime getLastUsedAt() {
        return lastAccessedAt;
    }
}
