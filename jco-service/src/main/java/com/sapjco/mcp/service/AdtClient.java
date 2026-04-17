package com.sapjco.mcp.service;

import com.sap.conn.jco.JCoDestination;
import com.sapjco.mcp.model.LockResponse;
import com.sapjco.mcp.util.AdtResponseHandler;
import com.sapjco.mcp.util.AdtUrlBuilder;
import com.sapjco.mcp.util.HttpHeaderBuilder;
import com.sapjco.mcp.util.ObjectNameEncoder;
import com.sapjco.mcp.util.RfcProxyExecutor;
import com.sapjco.mcp.util.XmlCreationBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ADT REST client that executes operations within JCo context.
 * Supports both Basic Auth and X.509 client certificate authentication.
 *
 * The key insight: HTTP calls made within a JCoContext.begin()/end() block
 * execute in the same stateful SAP session as RFC calls would.
 */
@Slf4j
@Service
public class AdtClient {

    /**
     * Default timeout for lock operations (matching ADT Eclipse's TIMEOUT_FOR_LOCK_REQUEST).
     * If lock times out, the object may still be locked on server - user must manually unlock in SM12.
     */
    public static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 15;

    @Value("${SAP_ADT_URL:}")
    private String adtBaseUrl;

    @Value("${SAP_CLIENT:}")
    private String sapClient;

    @Value("${SAP_USERNAME:}")
    private String sapUsername;

    @Value("${SAP_PASSWORD:}")
    private String sapPassword;

    // X.509 Certificate Authentication properties
    @Value("${SAP_AUTH_TYPE:basic}")
    private String authType;

    @Value("${SAP_CERTIFICATE:}")
    private String certificatePath;

    @Value("${SAP_PRIVATE_KEY:}")
    private String privateKeyPath;

    @Value("${SAP_CA_CERTIFICATE:}")
    private String caCertificatePath;

    @Value("${SAP_KEY_PASSPHRASE:}")
    private String keyPassphrase;

    @Value("${SAP_LOCK_TIMEOUT_SECONDS:" + DEFAULT_LOCK_TIMEOUT_SECONDS + "}")
    private int lockTimeoutSeconds;

    private OkHttpClient httpClient;
    private final ConcurrentHashMap<String, String> csrfTokenCache = new ConcurrentHashMap<>();

    public AdtClient() {
        // HTTP client will be created in init() after properties are injected
    }

    @PostConstruct
    public void init() {
        // Create HTTP client with appropriate authentication
        this.httpClient = createHttpClient();

        if ("x509".equalsIgnoreCase(authType)) {
            log.info("AdtClient initialized with X.509 authentication - Base URL: {}, Client: {}, Certificate: {}",
                     adtBaseUrl, sapClient, certificatePath);
        } else {
            log.info("AdtClient initialized with Basic authentication - Base URL: {}, Client: {}, Username: {}",
                     adtBaseUrl, sapClient, sapUsername);
        }
    }

    /**
     * Lock an ABAP object for editing.
     *
     * @param destination JCo destination (within active context)
     * @param objectName Object name (e.g., "ZTEST_CLASS")
     * @param objectType Object type (e.g., "class")
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return LockResponse with lock handle
     * @throws IOException if lock fails
     */
    public LockResponse lockObject(JCoDestination destination, String objectName, String objectType, OkHttpClient sessionClient, Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session)
            throws IOException {

        // CRITICAL: Lock operations use object root URI, NOT source include URI
        // Example: /sap/bc/adt/oo/classes/{name}?_action=LOCK (NOT /sap/bc/adt/oo/classes/{name}/source/main)
        String lockUri = AdtUrlBuilder.buildLockUrl(objectType, objectName);
        String uri = lockUri + "?_action=LOCK&accessMode=MODIFY";
        log.info("Locking object: {} (type: {}) at {}", objectName, objectType, uri);

        // Route through RFC for SNC SSO sessions (same pattern as Eclipse ADT)
        if (session.isUseRfcProxy()) {
            // URI already includes /sap/bc/adt prefix from AdtUrlBuilder
            return lockObjectViaRfc(destination, objectName, objectType, uri, session);
        }

        String url = uri;
        // Log cookies BEFORE lock request
        List<Cookie> cookiesBeforeLock = sessionClient.cookieJar().loadForRequest(HttpUrl.parse(url));
        log.info("🍪 [LOCK] Cookies available before request: {}", cookiesBeforeLock.size());
        if (!cookiesBeforeLock.isEmpty()) {
            log.info("🍪 [LOCK] Cookie details: {}",
                cookiesBeforeLock.stream()
                    .map(c -> c.name() + "=" + c.value().substring(0, Math.min(20, c.value().length())) + "... (domain: " + c.domain() + ", path: " + c.path() + ")")
                    .collect(Collectors.joining(", ")));
        }

        // Get CSRF token first (also captures saplb token)
        // Use the lock URI (object root) for CSRF token fetch
        String csrfToken = fetchCsrfToken(destination, lockUri, sessionClient, sessionCsrfCache, session);

        // Build lock request with ADT session headers
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.parse("text/plain")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")       // NEW: Stateful session indicator
                .addHeader("sap-contextid", session.getContextId())  // NEW: Session context ID
                .addHeader("Accept", "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9");

        // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
        addAuthHeader(requestBuilder, session);

        // Add saplb if available (NEW)
        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        // Log request headers
        log.info("🔒 [LOCK] Request headers:");
        for (String name : request.headers().names()) {
            String value = request.headers().get(name);
            if (name.equalsIgnoreCase("cookie") || name.equalsIgnoreCase("authorization")) {
                value = value != null ? value.substring(0, Math.min(50, value.length())) + "..." : "null";
            }
            log.info("    {}: {}", name, value);
        }

        // Explicitly check Cookie header
        String cookieHeader = request.header("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            log.warn("⚠️ [LOCK] WARNING: No Cookie header in request! Cookies might not be sent!");
        } else {
            log.info("✓ [LOCK] Cookie header present: {}", cookieHeader.substring(0, Math.min(100, cookieHeader.length())) + "...");
        }

        // Use session-specific HTTP client (passed as parameter)
        try (Response response = sessionClient.newCall(request).execute()) {
            // Log response status and headers
            log.info("🔓 [LOCK] Response status: {}", response.code());
            log.info("🔓 [LOCK] Response headers:");
            for (String name : response.headers().names()) {
                String value = response.headers().get(name);
                if (name.equalsIgnoreCase("set-cookie")) {
                    value = value != null ? value.substring(0, Math.min(50, value.length())) + "..." : "null";
                }
                log.info("    {}: {}", name, value);
            }

            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                log.error("Lock failed: {} - {}", response.code(), body);

                // Provide helpful error messages for common scenarios
                String errorMessage = "Lock failed: " + response.code();
                if (response.code() == 403) {
                    // Object already locked by someone else or permission denied
                    if (body.contains("locked") || body.contains("enqueue")) {
                        errorMessage += " - Object may already be locked by another user. Check SM12 for active locks.";
                    } else {
                        errorMessage += " - Permission denied or object is read-only. Details: " + body;
                    }
                } else if (response.code() == 401) {
                    errorMessage += " - Authentication failed. Check SAP credentials.";
                } else if (response.code() == 404) {
                    errorMessage += " - Object not found: " + objectName;
                } else if (response.code() == 500) {
                    errorMessage += " - SAP internal error. Details: " + body;
                } else {
                    errorMessage += " - " + body;
                }

                throw new IOException(errorMessage);
            }

            // Parse XML response body to extract lock handle
            String responseBody = response.body() != null ? response.body().string() : "";

            // Extract LOCK_HANDLE from XML response
            String lockHandle = extractXmlValue(responseBody, "LOCK_HANDLE");
            String transportNumber = extractXmlValue(responseBody, "CORRNR");

            if (lockHandle == null || lockHandle.isEmpty()) {
                throw new IOException("No lock handle returned in response");
            }

            log.info("✓ Object locked - Handle: {}, Transport: {}", lockHandle, transportNumber);

            // Log cookies AFTER lock
            List<Cookie> cookiesAfterLock = sessionClient.cookieJar().loadForRequest(HttpUrl.parse(url));
            log.info("🍪 [LOCK] Cookies available after lock: {}", cookiesAfterLock.size());
            if (!cookiesAfterLock.isEmpty()) {
                log.info("🍪 [LOCK] Cookie details: {}",
                    cookiesAfterLock.stream()
                        .map(c -> c.name() + "=" + c.value().substring(0, Math.min(20, c.value().length())) + "...")
                        .collect(Collectors.joining(", ")));
            }

            return new LockResponse(lockHandle, transportNumber);
        }
    }

    /**
     * Lock an ABAP object using a pre-built lock URI.
     * Used for objects like function modules that need a specific URI different from
     * the standard buildLockUrl pattern (e.g., locking the function module directly
     * rather than the parent function group).
     *
     * @param destination JCo destination
     * @param objectName Object name (for logging)
     * @param lockUri Pre-built ADT URI path for the object to lock (e.g., from AdtUrlBuilder.buildFunctionModuleUrl)
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return LockResponse with lock handle
     * @throws IOException if lock fails
     */
    public LockResponse lockObjectByUri(JCoDestination destination, String objectName, String lockUri,
                                        OkHttpClient sessionClient, Map<String, String> sessionCsrfCache,
                                        com.sapjco.mcp.model.JcoSession session) throws IOException {

        String uri = lockUri + "?_action=LOCK&accessMode=MODIFY";
        log.info("Locking object by URI: {} at {}", objectName, uri);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return lockObjectViaRfc(destination, objectName, "function_module", uri, session);
        }

        String url = uri;
        String csrfToken = fetchCsrfToken(destination, lockUri, sessionClient, sessionCsrfCache, session);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.parse("text/plain")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Accept", "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9");

        addAuthHeader(requestBuilder, session);

        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        try (Response response = sessionClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                log.error("Lock by URI failed: {} - {}", response.code(), body);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), body, "Lock by URI"));
            }

            String responseBody = AdtResponseHandler.extractBody(response);
            String lockHandle = extractXmlValue(responseBody, "LOCK_HANDLE");
            String transportNumber = extractXmlValue(responseBody, "CORRNR");

            if (lockHandle == null || lockHandle.isEmpty()) {
                throw new IOException("No lock handle returned in response");
            }

            log.info("✓ Object locked by URI - Handle: {}, Transport: {}", lockHandle, transportNumber);
            return new LockResponse(lockHandle, transportNumber);
        }
    }

    /**
     * Unlock an ABAP object using a pre-built lock URI.
     * Used for objects like function modules that need a specific URI.
     *
     * @param destination JCo destination
     * @param objectName Object name (for logging)
     * @param lockUri Pre-built ADT URI path for the object to unlock
     * @param lockHandle Lock handle from lock operation
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return Raw SAP response body
     * @throws IOException if unlock fails
     */
    public String unlockObjectByUri(JCoDestination destination, String objectName, String lockUri,
                                    String lockHandle, OkHttpClient sessionClient,
                                    Map<String, String> sessionCsrfCache,
                                    com.sapjco.mcp.model.JcoSession session) throws IOException {

        String uri = lockUri + "?_action=UNLOCK&lockHandle=" + lockHandle;
        log.info("Unlocking object by URI: {} with handle: {}", objectName, lockHandle);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return unlockObjectViaRfc(destination, objectName, "function_module", uri, session);
        }

        String url = uri;
        String csrfToken = fetchCsrfToken(destination, lockUri, sessionClient, sessionCsrfCache, session);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.parse("text/plain")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Accept", "*/*");

        addAuthHeader(requestBuilder, session);

        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        try (Response response = sessionClient.newCall(request).execute()) {
            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Unlock by URI failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Unlock by URI"));
            }

            log.info("✓ Object unlocked by URI");
            return responseBody;
        }
    }

    /**
     * Save ABAP object source code.
     *
     * @param destination JCo destination
     * @param objectName Object name
     * @param objectType Object type
     * @param sourceCode Source code to save
     * @param lockHandle Lock handle from lock operation
     * @param transportNumber Optional transport number
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return Raw SAP response body (empty string on success for most save operations)
     * @throws IOException if save fails
     */
    public String saveObject(JCoDestination destination, String objectName, String objectType,
                          String sourceCode, String lockHandle, String transportNumber, OkHttpClient sessionClient, Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session)
            throws IOException {

        // Build URL/URI with query parameters
        StringBuilder uriBuilder = new StringBuilder(AdtUrlBuilder.buildSourceUrl(objectType, objectName));
        uriBuilder.append("?lockHandle=").append(lockHandle);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("&corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Saving object: {} (type: {}) with lock handle: {}", objectName, objectType, lockHandle);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            // URI already includes /sap/bc/adt prefix from AdtUrlBuilder
            return saveObjectViaRfc(destination, objectName, objectType, uri, sourceCode, session);
        }

        String url = uri;
        // Log cookies BEFORE save request
        List<Cookie> cookiesBeforeSave = sessionClient.cookieJar().loadForRequest(HttpUrl.parse(url));
        log.info("🍪 [SAVE] Cookies available before request: {}", cookiesBeforeSave.size());
        if (!cookiesBeforeSave.isEmpty()) {
            log.info("🍪 [SAVE] Cookie details: {}",
                cookiesBeforeSave.stream()
                    .map(c -> c.name() + "=" + c.value().substring(0, Math.min(20, c.value().length())) + "... (domain: " + c.domain() + ", path: " + c.path() + ")")
                    .collect(Collectors.joining(", ")));
        }
        log.info("🍪 [SAVE] Cookie count: {} (should match lock count)", cookiesBeforeSave.size());

        // Get CSRF token - will reuse cached token from lock operation
        String csrfToken = fetchCsrfToken(destination, AdtUrlBuilder.buildSourceUrl(objectType, objectName), sessionClient, sessionCsrfCache, session);

        // Build save request with SAME session headers as lock
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .put(RequestBody.create(sourceCode, MediaType.parse("text/plain; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")       // NEW: MUST MATCH LOCK
                .addHeader("sap-contextid", session.getContextId())  // NEW: MUST MATCH LOCK
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Accept", "*/*");

        // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
        addAuthHeader(requestBuilder, session);

        // Add saplb if available (NEW - MUST MATCH LOCK)
        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        // Log request headers
        log.info("💾 [SAVE] Request headers:");
        for (String name : request.headers().names()) {
            String value = request.headers().get(name);
            if (name.equalsIgnoreCase("cookie") || name.equalsIgnoreCase("authorization")) {
                value = value != null ? value.substring(0, Math.min(50, value.length())) + "..." : "null";
            }
            log.info("    {}: {}", name, value);
        }

        // Explicitly check Cookie header
        String cookieHeader = request.header("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            log.warn("⚠️ [SAVE] WARNING: No Cookie header in request! Cookies might not be sent!");
        } else {
            log.info("✓ [SAVE] Cookie header present: {}", cookieHeader.substring(0, Math.min(100, cookieHeader.length())) + "...");
        }

        // Use session-specific HTTP client (passed as parameter)
        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("💾 [SAVE] Response status: {}", response.code());

            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Save failed: {} - {}", response.code(), responseBody);
                log.error("💥 [SAVE] Lock handle was: {}", lockHandle);
                log.error("💥 [SAVE] URL was: {}", url);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Save"));
            }

            log.info("✓ Object saved successfully");
            return responseBody;
        }
    }

    /**
     * Save ABAP class test include source code.
     *
     * @param destination JCo destination
     * @param className Class name
     * @param sourceCode Source code to save
     * @param lockHandle Lock handle from lock operation
     * @param transportNumber Optional transport number
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return Raw SAP response body (empty string on success)
     * @throws IOException if save fails
     */
    public String saveClassTestInclude(JCoDestination destination, String className,
                                     String sourceCode, String lockHandle, String transportNumber,
                                     OkHttpClient sessionClient, Map<String, String> sessionCsrfCache,
                                     com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build URL for test class include: /sap/bc/adt/oo/classes/{CLASS}/includes/testClasses
        String basePath = AdtUrlBuilder.buildClassIncludeUrl(className, "testClasses");
        StringBuilder uriBuilder = new StringBuilder(basePath);
        uriBuilder.append("?lockHandle=").append(lockHandle);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("&corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Saving class test include: {} with lock handle: {}", className, lockHandle);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return saveObjectViaRfc(destination, className, "class-test-include", uri, sourceCode, session);
        }

        String url = adtBaseUrl + basePath;
        StringBuilder urlBuilder = new StringBuilder(url);
        urlBuilder.append("?lockHandle=").append(lockHandle);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            urlBuilder.append("&corrNr=").append(transportNumber);
        }
        String fullUrl = urlBuilder.toString();

        // Log cookies BEFORE save request
        List<Cookie> cookiesBeforeSave = sessionClient.cookieJar().loadForRequest(HttpUrl.parse(fullUrl));
        log.info("[SAVE-TEST] Cookies available before request: {}", cookiesBeforeSave.size());

        // Get CSRF token - will reuse cached token from lock operation
        String csrfToken = fetchCsrfToken(destination, AdtUrlBuilder.buildClassIncludeSourceUrl(className, "testClasses"),
                                          sessionClient, sessionCsrfCache, session);

        // Build save request with SAME session headers as lock
        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .put(RequestBody.create(sourceCode, MediaType.parse("text/plain; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Accept", "*/*");

        // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
        addAuthHeader(requestBuilder, session);

        // Add saplb if available
        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        // Log request headers
        log.info("[SAVE-TEST] Request headers:");
        for (String name : request.headers().names()) {
            String value = request.headers().get(name);
            if (name.equalsIgnoreCase("cookie") || name.equalsIgnoreCase("authorization")) {
                value = value != null ? value.substring(0, Math.min(50, value.length())) + "..." : "null";
            }
            log.info("    {}: {}", name, value);
        }

        // Use session-specific HTTP client
        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("[SAVE-TEST] Response status: {}", response.code());

            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Save test include failed: {} - {}", response.code(), responseBody);
                log.error("[SAVE-TEST] Lock handle was: {}", lockHandle);
                log.error("[SAVE-TEST] URL was: {}", url);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Save test include"));
            }

            log.info("Class test include saved successfully");
            return responseBody;
        }
    }

    /**
     * Save an ABAP function module source code.
     * Function modules are sub-resources of function groups, so we lock the function group
     * but save to the function module source path.
     *
     * @param destination JCo destination
     * @param groupName Function group name
     * @param moduleName Function module name
     * @param sourceCode Source code to save
     * @param lockHandle Lock handle from lock operation (on the function group)
     * @param transportNumber Optional transport number
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return Raw SAP response body (empty string on success)
     * @throws IOException if save fails
     */
    public String saveFunctionModule(JCoDestination destination, String groupName, String moduleName,
                                     String sourceCode, String lockHandle, String transportNumber,
                                     OkHttpClient sessionClient, Map<String, String> sessionCsrfCache,
                                     com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build URL for function module source: /sap/bc/adt/functions/groups/{group}/fmodules/{module}/source/main
        String basePath = AdtUrlBuilder.buildFunctionModuleSourceUrl(groupName, moduleName);
        StringBuilder uriBuilder = new StringBuilder(basePath);
        uriBuilder.append("?lockHandle=").append(lockHandle);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("&corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Saving function module: {}.{} with lock handle: {}", groupName, moduleName, lockHandle);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return saveObjectViaRfc(destination, groupName + "." + moduleName, "function-module", uri, sourceCode, session);
        }

        String url = adtBaseUrl + basePath;
        StringBuilder urlBuilder = new StringBuilder(url);
        urlBuilder.append("?lockHandle=").append(lockHandle);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            urlBuilder.append("&corrNr=").append(transportNumber);
        }
        String fullUrl = urlBuilder.toString();

        // Log cookies BEFORE save request
        List<Cookie> cookiesBeforeSave = sessionClient.cookieJar().loadForRequest(HttpUrl.parse(fullUrl));
        log.info("[SAVE-FMOD] Cookies available before request: {}", cookiesBeforeSave.size());

        // Get CSRF token - will reuse cached token from lock operation
        String csrfToken = fetchCsrfToken(destination, basePath, sessionClient, sessionCsrfCache, session);

        // Build save request with SAME session headers as lock
        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .put(RequestBody.create(sourceCode, MediaType.parse("text/plain; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Accept", "*/*");

        // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
        addAuthHeader(requestBuilder, session);

        // Add saplb if available
        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        // Log request headers
        log.info("[SAVE-FMOD] Request headers:");
        for (String name : request.headers().names()) {
            String value = request.headers().get(name);
            if (name.equalsIgnoreCase("cookie") || name.equalsIgnoreCase("authorization")) {
                value = value != null ? value.substring(0, Math.min(50, value.length())) + "..." : "null";
            }
            log.info("    {}: {}", name, value);
        }

        // Use session-specific HTTP client
        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("[SAVE-FMOD] Response status: {}", response.code());

            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Save function module failed: {} - {}", response.code(), responseBody);
                log.error("[SAVE-FMOD] Lock handle was: {}", lockHandle);
                log.error("[SAVE-FMOD] URL was: {}", url);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Save function module"));
            }

            log.info("Function module saved successfully: {}.{}", groupName, moduleName);
            return responseBody;
        }
    }

    /**
     * Save an ABAP function group include source code.
     * Function group includes are sub-resources of function groups, accessed via
     * /sap/bc/adt/functions/groups/{group}/includes/{include}/source/main.
     *
     * @param destination JCo destination
     * @param groupName Function group name
     * @param includeName Include name (e.g., "LZFG_TESTF01")
     * @param sourceCode Source code to save
     * @param lockHandle Lock handle from lock operation
     * @param transportNumber Optional transport number
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return Raw SAP response body (empty string on success)
     * @throws IOException if save fails
     */
    public String saveFunctionGroupInclude(JCoDestination destination, String groupName, String includeName,
                                           String sourceCode, String lockHandle, String transportNumber,
                                           OkHttpClient sessionClient, Map<String, String> sessionCsrfCache,
                                           com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build URL for FG include source: /sap/bc/adt/functions/groups/{group}/includes/{include}/source/main
        String basePath = AdtUrlBuilder.buildFunctionGroupIncludeSourceUrl(groupName, includeName);
        StringBuilder uriBuilder = new StringBuilder(basePath);
        uriBuilder.append("?lockHandle=").append(lockHandle);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("&corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Saving function group include: {}.{} with lock handle: {}", groupName, includeName, lockHandle);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return saveObjectViaRfc(destination, groupName + "." + includeName, "fg-include", uri, sourceCode, session);
        }

        String url = adtBaseUrl + basePath;
        StringBuilder urlBuilder = new StringBuilder(url);
        urlBuilder.append("?lockHandle=").append(lockHandle);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            urlBuilder.append("&corrNr=").append(transportNumber);
        }
        String fullUrl = urlBuilder.toString();

        // Log cookies BEFORE save request
        List<Cookie> cookiesBeforeSave = sessionClient.cookieJar().loadForRequest(HttpUrl.parse(fullUrl));
        log.info("[SAVE-FG-INC] Cookies available before request: {}", cookiesBeforeSave.size());

        // Get CSRF token - will reuse cached token from lock operation
        String csrfToken = fetchCsrfToken(destination, basePath, sessionClient, sessionCsrfCache, session);

        // Build save request with SAME session headers as lock
        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .put(RequestBody.create(sourceCode, MediaType.parse("text/plain; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Accept", "*/*");

        // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
        addAuthHeader(requestBuilder, session);

        // Add saplb if available
        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        // Log request headers
        log.info("[SAVE-FG-INC] Request headers:");
        for (String name : request.headers().names()) {
            String value = request.headers().get(name);
            if (name.equalsIgnoreCase("cookie") || name.equalsIgnoreCase("authorization")) {
                value = value != null ? value.substring(0, Math.min(50, value.length())) + "..." : "null";
            }
            log.info("    {}: {}", name, value);
        }

        // Use session-specific HTTP client
        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("[SAVE-FG-INC] Response status: {}", response.code());

            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Save function group include failed: {} - {}", response.code(), responseBody);
                log.error("[SAVE-FG-INC] Lock handle was: {}", lockHandle);
                log.error("[SAVE-FG-INC] URL was: {}", url);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Save function group include"));
            }

            log.info("Function group include saved successfully: {}.{}", groupName, includeName);
            return responseBody;
        }
    }

    /**
     * Save an ABAP class include (generic for all include types).
     * This is the generic implementation that supports all include types:
     * definitions, implementations, macros, testClasses.
     *
     * @param destination JCo destination
     * @param className ABAP class name
     * @param includeType Type of include (definitions, implementations, macros, testClasses)
     * @param sourceCode Source code to save
     * @param lockHandle Lock handle from lock operation
     * @param transportNumber Optional transport number
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return Raw SAP response body (empty string on success)
     * @throws IOException if save fails
     */
    public String saveClassInclude(JCoDestination destination, String className, String includeType,
                                 String sourceCode, String lockHandle, String transportNumber,
                                 OkHttpClient sessionClient, Map<String, String> sessionCsrfCache,
                                 com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build URL for class include: /sap/bc/adt/oo/classes/{CLASS}/includes/{includeType}
        String basePath = AdtUrlBuilder.buildClassIncludeUrl(className, includeType);
        StringBuilder uriBuilder = new StringBuilder(basePath);
        uriBuilder.append("?lockHandle=").append(lockHandle);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("&corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Saving class include: {} (type: {}) with lock handle: {}", className, includeType, lockHandle);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return saveObjectViaRfc(destination, className, "class-include-" + includeType, uri, sourceCode, session);
        }

        String url = adtBaseUrl + basePath;
        StringBuilder urlBuilder = new StringBuilder(url);
        urlBuilder.append("?lockHandle=").append(lockHandle);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            urlBuilder.append("&corrNr=").append(transportNumber);
        }
        String fullUrl = urlBuilder.toString();

        // Log cookies BEFORE save request
        List<Cookie> cookiesBeforeSave = sessionClient.cookieJar().loadForRequest(HttpUrl.parse(fullUrl));
        log.info("[SAVE-INCLUDE] Cookies available before request: {}", cookiesBeforeSave.size());

        // Get CSRF token - will reuse cached token from lock operation
        String csrfToken = fetchCsrfToken(destination, AdtUrlBuilder.buildClassIncludeSourceUrl(className, includeType),
                                          sessionClient, sessionCsrfCache, session);

        // Build save request with SAME session headers as lock
        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .put(RequestBody.create(sourceCode, MediaType.parse("text/plain; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Accept", "*/*");

        // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
        addAuthHeader(requestBuilder, session);

        // Add saplb if available
        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        // Log request headers
        log.info("[SAVE-INCLUDE] Request headers:");
        for (String name : request.headers().names()) {
            String value = request.headers().get(name);
            if (name.equalsIgnoreCase("cookie") || name.equalsIgnoreCase("authorization")) {
                value = value != null ? value.substring(0, Math.min(50, value.length())) + "..." : "null";
            }
            log.info("    {}: {}", name, value);
        }

        // Use session-specific HTTP client
        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("[SAVE-INCLUDE] Response status: {}", response.code());

            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Save class include failed: {} - {}", response.code(), responseBody);
                log.error("[SAVE-INCLUDE] Lock handle was: {}", lockHandle);
                log.error("[SAVE-INCLUDE] URL was: {}", url);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Save class include (type: " + includeType + ")"));
            }

            log.info("Class include saved successfully (type: {})", includeType);
            return responseBody;
        }
    }

    /**
     * Unlock an ABAP object.
     *
     * @param destination JCo destination
     * @param objectName Object name
     * @param objectType Object type
     * @param lockHandle Lock handle to release
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @throws IOException if unlock fails
     */
    /**
     * Unlock an ABAP object after editing.
     *
     * @param destination JCo destination
     * @param objectName Object name
     * @param objectType Object type
     * @param lockHandle Lock handle from lock operation
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return Raw SAP response body (empty string on success for unlock operations)
     * @throws IOException if unlock fails
     */
    public String unlockObject(JCoDestination destination, String objectName, String objectType,
                            String lockHandle, OkHttpClient sessionClient, Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session) throws IOException {

        // CRITICAL: Unlock operations use object root URI, same as lock
        String lockUri = AdtUrlBuilder.buildLockUrl(objectType, objectName);
        String uri = lockUri + "?_action=UNLOCK&lockHandle=" + lockHandle;
        log.info("Unlocking object: {} (type: {}) with handle: {}", objectName, objectType, lockHandle);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            // URI already includes /sap/bc/adt prefix from AdtUrlBuilder
            return unlockObjectViaRfc(destination, objectName, objectType, uri, session);
        }

        String url = uri;
        // Get CSRF token - will reuse cached token from lock operation
        String csrfToken = fetchCsrfToken(destination, lockUri, sessionClient, sessionCsrfCache, session);

        // Build unlock request with session headers
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.parse("text/plain")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")       // NEW: Match lock/save
                .addHeader("sap-contextid", session.getContextId())  // NEW: Match lock/save
                .addHeader("Accept", "*/*");

        // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
        addAuthHeader(requestBuilder, session);

        // Add saplb if available (NEW - Match lock/save)
        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        // Use session-specific HTTP client (passed as parameter)
        try (Response response = sessionClient.newCall(request).execute()) {
            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Unlock failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Unlock"));
            }

            log.info("✓ Object unlocked successfully");
            return responseBody;
        }
    }

    /**
     * Create a new ABAP class.
     * POST /sap/bc/adt/oo/classes/?corrNr={transport}
     *
     * @param destination JCo destination
     * @param className ABAP class name (e.g., "ZCL_MY_CLASS")
     * @param description Short description
     * @param packageName Target package ($TMP for local development)
     * @param transportNumber Optional transport request (not required for $TMP)
     * @param visibility Class visibility: "public", "protected", or "private" (default: public)
     * @param isFinal Whether the class is final (default: true)
     * @param superClass Optional superclass name
     * @param interfaces Optional list of interfaces to implement
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return CreateResponse with object URI, name, type, and HTTP status
     * @throws IOException if creation fails
     */
    public com.sapjco.mcp.model.CreateResponse createClass(
            JCoDestination destination,
            String className,
            String description,
            String packageName,
            String transportNumber,
            String visibility,
            boolean isFinal,
            String superClass,
            List<String> interfaces,
            OkHttpClient sessionClient,
            Map<String, String> sessionCsrfCache,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build creation endpoint - POST to collection URI (no trailing slash)
        String baseUri = "/sap/bc/adt/oo/classes";
        StringBuilder uriBuilder = new StringBuilder(baseUri);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("?corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Creating class: {} (package: {}, transport: {})", className, packageName, transportNumber);

        // Build XML body for class creation
        String creationXml = XmlCreationBuilder.buildClassCreationXml(className, description, packageName, visibility, isFinal, superClass, interfaces);
        log.debug("Creation XML:\n{}", creationXml);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return createClassViaRfc(destination, className, uri, creationXml, session);
        }

        // Get CSRF token
        String csrfToken = fetchCsrfToken(destination, "/sap/bc/adt/oo/classes", sessionClient, sessionCsrfCache, session);

        // Build request
        Request.Builder requestBuilder = new Request.Builder()
                .url(uri)
                .post(RequestBody.create(creationXml, MediaType.parse("application/vnd.sap.adt.oo.classes.v4+xml; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "application/vnd.sap.adt.oo.classes.v4+xml; charset=utf-8")
                .addHeader("Accept", "application/vnd.sap.adt.oo.classes.v4+xml, application/xml, */*");

        addAuthHeader(requestBuilder, session);

        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }
        if (session.getConnectionId() != null) {
            requestBuilder.addHeader("sap-adt-connection-id", session.getConnectionId());
        }

        Request request = requestBuilder.build();

        log.info("[CREATE-CLASS] Request headers:");
        for (String name : request.headers().names()) {
            String value = request.headers().get(name);
            if (name.equalsIgnoreCase("cookie") || name.equalsIgnoreCase("authorization")) {
                value = value != null ? value.substring(0, Math.min(50, value.length())) + "..." : "null";
            }
            log.info("    {}: {}", name, value);
        }

        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("[CREATE-CLASS] Response status: {}", response.code());
            String responseBody = AdtResponseHandler.extractBody(response);

            // Log response headers (including Location)
            log.info("[CREATE-CLASS] Response headers:");
            for (String name : response.headers().names()) {
                log.info("    {}: {}", name, response.headers().get(name));
            }

            if (!response.isSuccessful()) {
                log.error("Create class failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Create class"));
            }

            // Extract Location header (contains the URI of the created object)
            String locationHeader = response.header("Location");
            log.info("✓ Class created - Location: {}", locationHeader);

            return com.sapjco.mcp.model.CreateResponse.builder()
                    .objectUri(locationHeader)
                    .objectName(className.toUpperCase())
                    .objectType("class")
                    .httpStatus(response.code())
                    .rawResponse(responseBody)
                    .build();
        }
    }

    /**
     * Create a new ABAP class via RFC proxy (for SNC SSO sessions).
     */
    private com.sapjco.mcp.model.CreateResponse createClassViaRfc(
            JCoDestination destination,
            String className,
            String uri,
            String creationXml,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        log.info("⚡ Creating class via RFC: {}", className);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildCreationHeaders(
                    session, sapClient, RfcProxyExecutor.CONTENT_TYPE_CLASS, RfcProxyExecutor.ACCEPT_CLASS);

            // Execute via RFC - no CSRF token needed
            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "POST", uri, null, headers, creationXml, session);

            return RfcProxyExecutor.parseCreateResponse(response, className, "class");

        } catch (Exception e) {
            throw new IOException("Create class via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new ABAP interface.
     * POST /sap/bc/adt/oo/interfaces/?corrNr={transport}
     *
     * @param destination JCo destination
     * @param interfaceName ABAP interface name (e.g., "ZIF_MY_INTERFACE")
     * @param description Short description
     * @param packageName Target package ($TMP for local development)
     * @param transportNumber Optional transport request (not required for $TMP)
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return CreateResponse with object URI, name, type, and HTTP status
     * @throws IOException if creation fails
     */
    public com.sapjco.mcp.model.CreateResponse createInterface(
            JCoDestination destination,
            String interfaceName,
            String description,
            String packageName,
            String transportNumber,
            OkHttpClient sessionClient,
            Map<String, String> sessionCsrfCache,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build creation endpoint - POST to collection URI (no trailing slash)
        String baseUri = "/sap/bc/adt/oo/interfaces";
        StringBuilder uriBuilder = new StringBuilder(baseUri);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("?corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Creating interface: {} (package: {}, transport: {})", interfaceName, packageName, transportNumber);

        // Build XML body for interface creation
        String creationXml = XmlCreationBuilder.buildInterfaceCreationXml(interfaceName, description, packageName);
        log.debug("Creation XML:\n{}", creationXml);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return createInterfaceViaRfc(destination, interfaceName, uri, creationXml, session);
        }

        // Get CSRF token
        String csrfToken = fetchCsrfToken(destination, "/sap/bc/adt/oo/interfaces", sessionClient, sessionCsrfCache, session);

        // Build request
        Request.Builder requestBuilder = new Request.Builder()
                .url(uri)
                .post(RequestBody.create(creationXml, MediaType.parse("application/vnd.sap.adt.oo.interfaces.v2+xml; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "application/vnd.sap.adt.oo.interfaces.v2+xml; charset=utf-8")
                .addHeader("Accept", "application/vnd.sap.adt.oo.interfaces.v2+xml, application/xml, */*");

        addAuthHeader(requestBuilder, session);

        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }
        if (session.getConnectionId() != null) {
            requestBuilder.addHeader("sap-adt-connection-id", session.getConnectionId());
        }

        Request request = requestBuilder.build();

        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("[CREATE-INTERFACE] Response status: {}", response.code());
            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Create interface failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Create interface"));
            }

            String locationHeader = response.header("Location");
            log.info("✓ Interface created - Location: {}", locationHeader);

            return com.sapjco.mcp.model.CreateResponse.builder()
                    .objectUri(locationHeader)
                    .objectName(interfaceName.toUpperCase())
                    .objectType("interface")
                    .httpStatus(response.code())
                    .rawResponse(responseBody)
                    .build();
        }
    }

    /**
     * Create a new ABAP interface via RFC proxy (for SNC SSO sessions).
     */
    private com.sapjco.mcp.model.CreateResponse createInterfaceViaRfc(
            JCoDestination destination,
            String interfaceName,
            String uri,
            String creationXml,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        log.info("⚡ Creating interface via RFC: {}", interfaceName);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildCreationHeaders(
                    session, sapClient, RfcProxyExecutor.CONTENT_TYPE_INTERFACE, RfcProxyExecutor.ACCEPT_INTERFACE);

            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "POST", uri, null, headers, creationXml, session);

            return RfcProxyExecutor.parseCreateResponse(response, interfaceName, "interface");

        } catch (Exception e) {
            throw new IOException("Create interface via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new ABAP program (report).
     * POST /sap/bc/adt/programs/programs/?corrNr={transport}
     *
     * @param destination JCo destination
     * @param programName ABAP program name (e.g., "ZTEST_PROGRAM")
     * @param description Short description
     * @param packageName Target package ($TMP for local development)
     * @param transportNumber Optional transport request (not required for $TMP)
     * @param programType Program type: "executableProgram" (default), "include", "modulePool", etc.
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return CreateResponse with object URI, name, type, and HTTP status
     * @throws IOException if creation fails
     */
    public com.sapjco.mcp.model.CreateResponse createProgram(
            JCoDestination destination,
            String programName,
            String description,
            String packageName,
            String transportNumber,
            String programType,
            OkHttpClient sessionClient,
            Map<String, String> sessionCsrfCache,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build creation endpoint - POST to collection URI (no trailing slash)
        String baseUri = "/sap/bc/adt/programs/programs";
        StringBuilder uriBuilder = new StringBuilder(baseUri);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("?corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Creating program: {} (package: {}, transport: {})", programName, packageName, transportNumber);

        // Build XML body for program creation
        String creationXml = XmlCreationBuilder.buildProgramCreationXml(programName, description, packageName, programType);
        log.debug("Creation XML:\n{}", creationXml);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return createProgramViaRfc(destination, programName, uri, creationXml, session);
        }

        // Get CSRF token
        String csrfToken = fetchCsrfToken(destination, "/sap/bc/adt/programs/programs", sessionClient, sessionCsrfCache, session);

        // Build request
        Request.Builder requestBuilder = new Request.Builder()
                .url(uri)
                .post(RequestBody.create(creationXml, MediaType.parse("application/vnd.sap.adt.programs.programs.v2+xml; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "application/vnd.sap.adt.programs.programs.v2+xml; charset=utf-8")
                .addHeader("Accept", "application/vnd.sap.adt.programs.programs.v2+xml, application/xml, */*");

        addAuthHeader(requestBuilder, session);

        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }
        if (session.getConnectionId() != null) {
            requestBuilder.addHeader("sap-adt-connection-id", session.getConnectionId());
        }

        Request request = requestBuilder.build();

        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("[CREATE-PROGRAM] Response status: {}", response.code());
            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Create program failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Create program"));
            }

            String locationHeader = response.header("Location");
            log.info("✓ Program created - Location: {}", locationHeader);

            return com.sapjco.mcp.model.CreateResponse.builder()
                    .objectUri(locationHeader)
                    .objectName(programName.toUpperCase())
                    .objectType("program")
                    .httpStatus(response.code())
                    .rawResponse(responseBody)
                    .build();
        }
    }

    /**
     * Create a new ABAP program via RFC proxy (for SNC SSO sessions).
     */
    private com.sapjco.mcp.model.CreateResponse createProgramViaRfc(
            JCoDestination destination,
            String programName,
            String uri,
            String creationXml,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        log.info("⚡ Creating program via RFC: {}", programName);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildCreationHeaders(
                    session, sapClient, RfcProxyExecutor.CONTENT_TYPE_PROGRAM, RfcProxyExecutor.ACCEPT_PROGRAM);

            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "POST", uri, null, headers, creationXml, session);

            return RfcProxyExecutor.parseCreateResponse(response, programName, "program");

        } catch (Exception e) {
            throw new IOException("Create program via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new ABAP function group.
     * POST /sap/bc/adt/functions/groups/?corrNr={transport}
     *
     * @param destination JCo destination
     * @param groupName Function group name (e.g., "ZTEST_FG")
     * @param description Short description
     * @param packageName Target package ($TMP for local development)
     * @param transportNumber Optional transport request (not required for $TMP)
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return CreateResponse with object URI, name, type, and HTTP status
     * @throws IOException if creation fails
     */
    public com.sapjco.mcp.model.CreateResponse createFunctionGroup(
            JCoDestination destination,
            String groupName,
            String description,
            String packageName,
            String transportNumber,
            OkHttpClient sessionClient,
            Map<String, String> sessionCsrfCache,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build creation endpoint - POST to collection URI (no trailing slash)
        String baseUri = "/sap/bc/adt/functions/groups";
        StringBuilder uriBuilder = new StringBuilder(baseUri);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("?corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Creating function group: {} (package: {}, transport: {})", groupName, packageName, transportNumber);

        // Build XML body for function group creation
        String creationXml = XmlCreationBuilder.buildFunctionGroupCreationXml(groupName, description, packageName);
        log.debug("Creation XML:\n{}", creationXml);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return createFunctionGroupViaRfc(destination, groupName, uri, creationXml, session);
        }

        // Get CSRF token
        String csrfToken = fetchCsrfToken(destination, "/sap/bc/adt/functions/groups", sessionClient, sessionCsrfCache, session);

        // Build request
        Request.Builder requestBuilder = new Request.Builder()
                .url(uri)
                .post(RequestBody.create(creationXml, MediaType.parse("application/vnd.sap.adt.functions.groups.v4+xml; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "application/vnd.sap.adt.functions.groups.v4+xml; charset=utf-8")
                .addHeader("Accept", "application/vnd.sap.adt.functions.groups.v4+xml, application/xml, */*");

        addAuthHeader(requestBuilder, session);

        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }
        if (session.getConnectionId() != null) {
            requestBuilder.addHeader("sap-adt-connection-id", session.getConnectionId());
        }

        Request request = requestBuilder.build();

        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("[CREATE-FUNCTION-GROUP] Response status: {}", response.code());
            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Create function group failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Create function group"));
            }

            String locationHeader = response.header("Location");
            log.info("✓ Function group created - Location: {}", locationHeader);

            return com.sapjco.mcp.model.CreateResponse.builder()
                    .objectUri(locationHeader)
                    .objectName(groupName.toUpperCase())
                    .objectType("function_group")
                    .httpStatus(response.code())
                    .rawResponse(responseBody)
                    .build();
        }
    }

    /**
     * Create a new ABAP function group via RFC proxy (for SNC SSO sessions).
     */
    private com.sapjco.mcp.model.CreateResponse createFunctionGroupViaRfc(
            JCoDestination destination,
            String groupName,
            String uri,
            String creationXml,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        log.info("⚡ Creating function group via RFC: {}", groupName);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildCreationHeaders(
                    session, sapClient, RfcProxyExecutor.CONTENT_TYPE_FUNCTION_GROUP, RfcProxyExecutor.ACCEPT_FUNCTION_GROUP);

            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "POST", uri, null, headers, creationXml, session);

            return RfcProxyExecutor.parseCreateResponse(response, groupName, "function_group");

        } catch (Exception e) {
            throw new IOException("Create function group via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new ABAP include program.
     * POST /sap/bc/adt/programs/includes/?corrNr={transport}
     *
     * @param destination JCo destination
     * @param includeName Include program name (e.g., "ZTEST_INCLUDE")
     * @param description Short description
     * @param packageName Target package ($TMP for local development)
     * @param transportNumber Optional transport request (not required for $TMP)
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return CreateResponse with object URI, name, type, and HTTP status
     * @throws IOException if creation fails
     */
    public com.sapjco.mcp.model.CreateResponse createInclude(
            JCoDestination destination,
            String includeName,
            String description,
            String packageName,
            String transportNumber,
            OkHttpClient sessionClient,
            Map<String, String> sessionCsrfCache,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build creation endpoint - POST to collection URI (no trailing slash)
        String baseUri = "/sap/bc/adt/programs/includes";
        StringBuilder uriBuilder = new StringBuilder(baseUri);
        if (transportNumber != null && !transportNumber.isEmpty()) {
            uriBuilder.append("?corrNr=").append(transportNumber);
        }
        String uri = uriBuilder.toString();

        log.info("Creating include: {} (package: {}, transport: {})", includeName, packageName, transportNumber);

        // Build XML body for include creation
        String creationXml = XmlCreationBuilder.buildIncludeCreationXml(includeName, description, packageName);
        log.debug("Creation XML:\n{}", creationXml);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return createIncludeViaRfc(destination, includeName, uri, creationXml, session);
        }

        // Get CSRF token
        String csrfToken = fetchCsrfToken(destination, "/sap/bc/adt/programs/includes", sessionClient, sessionCsrfCache, session);

        // Build request
        Request.Builder requestBuilder = new Request.Builder()
                .url(uri)
                .post(RequestBody.create(creationXml, MediaType.parse("application/vnd.sap.adt.programs.includes.v2+xml; charset=utf-8")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "application/vnd.sap.adt.programs.includes.v2+xml; charset=utf-8")
                .addHeader("Accept", "application/vnd.sap.adt.programs.includes.v2+xml, application/xml, */*");

        addAuthHeader(requestBuilder, session);

        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }
        if (session.getConnectionId() != null) {
            requestBuilder.addHeader("sap-adt-connection-id", session.getConnectionId());
        }

        Request request = requestBuilder.build();

        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("[CREATE-INCLUDE] Response status: {}", response.code());
            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Create include failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Create include"));
            }

            String locationHeader = response.header("Location");
            log.info("✓ Include created - Location: {}", locationHeader);

            return com.sapjco.mcp.model.CreateResponse.builder()
                    .objectUri(locationHeader)
                    .objectName(includeName.toUpperCase())
                    .objectType("include")
                    .httpStatus(response.code())
                    .rawResponse(responseBody)
                    .build();
        }
    }

    /**
     * Create a new ABAP include via RFC proxy (for SNC SSO sessions).
     */
    private com.sapjco.mcp.model.CreateResponse createIncludeViaRfc(
            JCoDestination destination,
            String includeName,
            String uri,
            String creationXml,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        log.info("⚡ Creating include via RFC: {}", includeName);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildCreationHeaders(
                    session, sapClient, RfcProxyExecutor.CONTENT_TYPE_INCLUDE, RfcProxyExecutor.ACCEPT_INCLUDE);

            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "POST", uri, null, headers, creationXml, session);

            return RfcProxyExecutor.parseCreateResponse(response, includeName, "include");

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Create include via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Delete an ABAP object using the ADT deletion API.
     *
     * Deletion follows the ADT pattern (as per Eclipse trace):
     * 1. POST /sap/bc/adt/deletion/delete with XML payload containing object URI and transport
     *
     * @param destination JCo destination
     * @param objectName Object name (e.g., "ZCL_TEST")
     * @param objectType Object type (class, interface, program, function_group, include)
     * @param transportNumber Optional transport request (required for non-$TMP objects)
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return DeleteResponse with object info and HTTP status
     * @throws IOException if deletion fails
     */
    public com.sapjco.mcp.model.DeleteResponse deleteObject(
            JCoDestination destination,
            String objectName,
            String objectType,
            String transportNumber,
            OkHttpClient sessionClient,
            Map<String, String> sessionCsrfCache,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Build object URI for deletion API
        String objectUri = AdtUrlBuilder.buildBaseUrl(objectType, objectName);
        log.info("Deleting object: {} (type: {}) at {}", objectName, objectType, objectUri);

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return deleteObjectViaRfc(destination, objectName, objectType, objectUri, transportNumber, session);
        }

        // Build deletion XML payload (as per Eclipse ADT trace)
        String deletionXml = buildDeletionXml(objectUri, transportNumber);

        // Deletion API endpoint
        String deleteUrl = adtBaseUrl + "/deletion/delete";

        // Get CSRF token
        String csrfToken = fetchCsrfToken(destination, deleteUrl, sessionClient, sessionCsrfCache, session);

        // Build POST request for deletion
        Request.Builder requestBuilder = new Request.Builder()
                .url(deleteUrl)
                .post(RequestBody.create(deletionXml, MediaType.parse("application/vnd.sap.adt.deletion.request.v1+xml")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("Content-Type", "application/vnd.sap.adt.deletion.request.v1+xml")
                .addHeader("Accept", "application/vnd.sap.adt.deletion.response.v1+xml");

        addAuthHeader(requestBuilder, session);

        if (session.getConnectionId() != null) {
            requestBuilder.addHeader("sap-adt-connection-id", session.getConnectionId());
        }

        Request request = requestBuilder.build();

        log.info("🗑️ [POST /deletion/delete] Deleting object");

        try (Response response = sessionClient.newCall(request).execute()) {
            log.info("🗑️ [DELETE] Response status: {}", response.code());

            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Delete failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Delete"));
            }

            log.info("✓ Object deleted successfully: {}", objectName);

            return com.sapjco.mcp.model.DeleteResponse.builder()
                    .objectName(objectName.toUpperCase())
                    .objectType(objectType)
                    .httpStatus(response.code())
                    .rawResponse(responseBody)
                    .build();
        }
    }

    /**
     * Build XML payload for deletion request (as per ADT deletion API).
     */
    private String buildDeletionXml(String objectUri, String transportNumber) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<del:deletionRequest xmlns:adtcore=\"http://www.sap.com/adt/core\" xmlns:del=\"http://www.sap.com/adt/deletion\">\n");
        xml.append("  <del:object adtcore:uri=\"").append(objectUri).append("\">\n");
        xml.append("    <del:transportNumber>");
        if (transportNumber != null && !transportNumber.isEmpty()) {
            xml.append(XmlCreationBuilder.escapeXml(transportNumber));
        }
        xml.append("</del:transportNumber>\n");
        xml.append("  </del:object>\n");
        xml.append("</del:deletionRequest>");
        return xml.toString();
    }

    /**
     * Delete an ABAP object via RFC proxy (for SNC SSO sessions).
     */
    private com.sapjco.mcp.model.DeleteResponse deleteObjectViaRfc(
            JCoDestination destination,
            String objectName,
            String objectType,
            String objectUri,
            String transportNumber,
            com.sapjco.mcp.model.JcoSession session) throws IOException {

        log.info("⚡ Deleting object via RFC: {} (type: {})", objectName, objectType);

        try {
            // Build deletion XML payload
            String deletionXml = buildDeletionXml(objectUri, transportNumber);

            // Deletion API endpoint
            String deleteUri = "/sap/bc/adt/deletion/delete";

            Map<String, String> headers = new LinkedHashMap<>();
            if (session.getClient() != null && !session.getClient().isEmpty()) {
                headers.put("SAP-Client", session.getClient());
            } else if (sapClient != null && !sapClient.isEmpty()) {
                headers.put("SAP-Client", sapClient);
            }
            if (session.getConnectionId() != null) {
                headers.put("sap-adt-connection-id", session.getConnectionId());
            }
            headers.put("Content-Type", "application/vnd.sap.adt.deletion.request.v1+xml");
            headers.put("Accept", "application/vnd.sap.adt.deletion.response.v1+xml");

            // Execute POST via RFC - requires CSRF token
            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "POST", deleteUri, null, headers, deletionXml, session);

            if (response.getStatusCode() >= 400) {
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.getStatusCode(), response.getBody(), "Delete via RFC"));
            }

            log.info("✓ Object deleted via RFC: {}", objectName);

            return com.sapjco.mcp.model.DeleteResponse.builder()
                    .objectName(objectName.toUpperCase())
                    .objectType(objectType)
                    .httpStatus(response.getStatusCode())
                    .rawResponse(response.getBody())
                    .build();

        } catch (Exception e) {
            throw new IOException("Delete via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Activate an ABAP object.
     *
     * @param destination JCo destination
     * @param objectName Object name
     * @param objectType Object type
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object with contextId and saplbToken
     * @return Raw SAP response body (XML with activation result)
     * @throws IOException if activation fails
     */
    public String activateObject(JCoDestination destination, String objectName, String objectType, OkHttpClient sessionClient, Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session)
            throws IOException {

        // Activation endpoint with required query params
        String uri = "/sap/bc/adt/activation";
        log.info("Activating object: {} (type: {})", objectName, objectType);

        // Build activation XML payload
        String adtUri = AdtUrlBuilder.buildBaseUrl(objectType, objectName);
        String adtTypeCode = AdtUrlBuilder.getAdtTypeCode(objectType);
        String activationXml = String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">" +
            "<adtcore:objectReference adtcore:uri=\"%s\" adtcore:type=\"%s\" adtcore:name=\"%s\"/>" +
            "</adtcore:objectReferences>",
            adtUri, adtTypeCode, objectName
        );

        // Required query params for activation (as per ADT protocol)
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("method", "activate");
        queryParams.put("preauditRequested", "true");

        // Route through RFC for SNC SSO sessions
        if (session.isUseRfcProxy()) {
            return activateObjectViaRfc(destination, objectName, objectType, uri, queryParams, activationXml, session);
        }

        String url = adtBaseUrl + "/activation";
        // Get CSRF token - will reuse cached token
        String csrfToken = fetchCsrfToken(destination, url, sessionClient, sessionCsrfCache, session);

        // Build activation request with session headers
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(activationXml, MediaType.parse("application/xml")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")       // NEW
                .addHeader("sap-contextid", session.getContextId())  // NEW
                .addHeader("Content-Type", "application/xml")
                .addHeader("Accept", "*/*");

        // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
        addAuthHeader(requestBuilder, session);

        // Add saplb if available (NEW)
        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        // Use session-specific HTTP client (passed as parameter)
        try (Response response = sessionClient.newCall(request).execute()) {
            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Activation failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Activation"));
            }

            log.info("✓ Object activated successfully");
            return responseBody;
        }
    }

    /**
     * Check syntax of an ABAP object without activating it.
     * Uses the same activation endpoint with method=check instead of method=activate.
     *
     * @param destination JCo destination
     * @param objectName Object name
     * @param objectType Object type
     * @param session Full JcoSession object with contextId and saplbToken
     * @return Raw SAP response body (XML with syntax check result)
     * @throws IOException if syntax check fails
     */
    public String checkSyntax(JCoDestination destination, String objectName, String objectType,
                              com.sapjco.mcp.model.JcoSession session) throws IOException {

        // Activation endpoint with method=check for syntax checking
        String uri = "/sap/bc/adt/activation";
        log.info("Checking syntax: {} (type: {})", objectName, objectType);

        // Build syntax check XML payload (same format as activation)
        String adtUri = AdtUrlBuilder.buildBaseUrl(objectType, objectName);
        String adtTypeCode = AdtUrlBuilder.getAdtTypeCode(objectType);
        String checkXml = String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">" +
            "<adtcore:objectReference adtcore:uri=\"%s\" adtcore:type=\"%s\" adtcore:name=\"%s\"/>" +
            "</adtcore:objectReferences>",
            adtUri, adtTypeCode, objectName
        );

        // Required query params for syntax check (method=check instead of activate)
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("method", "check");
        queryParams.put("preauditRequested", "true");

        // Route through RFC for SNC SSO sessions (same pattern as activateObject)
        return checkSyntaxViaRfc(destination, objectName, objectType, uri, queryParams, checkXml, session);
    }

    /**
     * Check syntax of a function module without activating it.
     * Function modules require a different URI pattern (group + module name).
     *
     * @param destination JCo destination
     * @param groupName Function group name
     * @param moduleName Function module name
     * @param session Full JcoSession object
     * @return Raw SAP response body (XML with syntax check result)
     * @throws IOException if syntax check fails
     */
    public String checkSyntaxFunctionModule(JCoDestination destination, String groupName, String moduleName,
                                            com.sapjco.mcp.model.JcoSession session) throws IOException {

        String uri = "/sap/bc/adt/activation";
        log.info("Checking syntax for function module: {}.{}", groupName, moduleName);

        String adtUri = AdtUrlBuilder.buildFunctionModuleUrl(groupName, moduleName);
        String adtTypeCode = AdtUrlBuilder.getAdtTypeCode("function_module");
        String checkXml = String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">" +
            "<adtcore:objectReference adtcore:uri=\"%s\" adtcore:type=\"%s\" adtcore:name=\"%s\"/>" +
            "</adtcore:objectReferences>",
            adtUri, adtTypeCode, moduleName
        );

        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("method", "check");
        queryParams.put("preauditRequested", "true");

        return checkSyntaxViaRfc(destination, moduleName, "function_module", uri, queryParams, checkXml, session);
    }

    /**
     * Activate a function module.
     * Function modules require a different URI pattern (group + module name).
     *
     * @param destination JCo destination
     * @param groupName Function group name
     * @param moduleName Function module name
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Raw SAP response body (XML with activation result)
     * @throws IOException if activation fails
     */
    public String activateFunctionModule(JCoDestination destination, String groupName, String moduleName,
                                         OkHttpClient sessionClient, Map<String, String> sessionCsrfCache,
                                         com.sapjco.mcp.model.JcoSession session) throws IOException {

        String uri = "/sap/bc/adt/activation";
        log.info("Activating function module: {}.{}", groupName, moduleName);

        String adtUri = AdtUrlBuilder.buildFunctionModuleUrl(groupName, moduleName);
        String adtTypeCode = AdtUrlBuilder.getAdtTypeCode("function_module");
        String activationXml = String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">" +
            "<adtcore:objectReference adtcore:uri=\"%s\" adtcore:type=\"%s\" adtcore:name=\"%s\"/>" +
            "</adtcore:objectReferences>",
            adtUri, adtTypeCode, moduleName
        );

        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("method", "activate");
        queryParams.put("preauditRequested", "true");

        if (session.isUseRfcProxy()) {
            return activateObjectViaRfc(destination, moduleName, "function_module", uri, queryParams, activationXml, session);
        }

        String url = adtBaseUrl + "/activation";
        String csrfToken = fetchCsrfToken(destination, url, sessionClient, sessionCsrfCache, session);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(activationXml, MediaType.parse("application/xml")))
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", csrfToken)
                .addHeader("x-sap-adt-sessiontype", "stateful")
                .addHeader("sap-contextid", session.getContextId())
                .addHeader("Content-Type", "application/xml")
                .addHeader("Accept", "*/*");

        addAuthHeader(requestBuilder, session);

        if (session.getSaplbToken() != null) {
            requestBuilder.addHeader("saplb", session.getSaplbToken());
        }

        Request request = requestBuilder.build();

        try (Response response = sessionClient.newCall(request).execute()) {
            String responseBody = AdtResponseHandler.extractBody(response);

            if (!response.isSuccessful()) {
                log.error("Activation failed: {} - {}", response.code(), responseBody);
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), responseBody, "Activation"));
            }

            log.info("✓ Function module activated successfully");
            return responseBody;
        }
    }

    /**
     * Check syntax via RFC proxy (no CSRF token needed).
     */
    private String checkSyntaxViaRfc(JCoDestination destination, String objectName, String objectType,
                                     String uri, Map<String, String> queryParams, String checkXml,
                                     com.sapjco.mcp.model.JcoSession session) throws IOException {
        log.info("⚡ Checking syntax via RFC: {} {}", objectType, objectName);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildStandardHeaders(session, sapClient);
            headers.put(HttpHeaderBuilder.HEADER_CONTENT_TYPE, "application/xml");
            headers.put(HttpHeaderBuilder.HEADER_ACCEPT, "*/*");

            // Execute via RFC - no CSRF token needed, pass query params
            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "POST", uri, queryParams, headers, checkXml, session);

            if (response.getStatusCode() >= 400) {
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.getStatusCode(), response.getBody(), "Syntax check via RFC"));
            }

            log.info("✓ Syntax check completed via RFC");
            return response.getBody();

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Syntax check via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a generic HTTP request through the session's SSO-enabled HTTP client.
     * This enables routing any ADT HTTP request through JCo service for SSO support.
     *
     * @param destination JCo destination (within active context)
     * @param method HTTP method (GET, POST, PUT, DELETE)
     * @param path ADT path (e.g., "/sap/bc/adt/oo/classes/ZCL_TEST/source/main")
     * @param queryParams Query parameters (can be null)
     * @param customHeaders Custom headers (can be null)
     * @param body Request body (can be null)
     * @param timeoutMs Request timeout in milliseconds
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return HttpProxyResponse with status code, headers, and body
     */
    public com.sapjco.mcp.model.HttpProxyResponse executeHttpRequest(
            JCoDestination destination,
            String method,
            String path,
            Map<String, String> queryParams,
            Map<String, String> customHeaders,
            String body,
            int timeoutMs,
            OkHttpClient sessionClient,
            Map<String, String> sessionCsrfCache,
            com.sapjco.mcp.model.JcoSession session) {

        // Route through RFC for SNC SSO sessions (same pattern as Eclipse ADT)
        if (session.isUseRfcProxy()) {
            return executeHttpRequestViaRfc(destination, method, path, queryParams, customHeaders, body, session);
        }

        try {
            // Normalize path: remove /sap/bc/adt prefix if present (adtBaseUrl already includes it)
            String normalizedPath = path;
            if (path.startsWith("/sap/bc/adt")) {
                normalizedPath = path.substring("/sap/bc/adt".length());
            }

            // Build URL from adtBaseUrl + normalized path
            StringBuilder urlBuilder = new StringBuilder(adtBaseUrl);
            urlBuilder.append(normalizedPath);

            // Add query parameters
            if (queryParams != null && !queryParams.isEmpty()) {
                urlBuilder.append("?");
                boolean first = true;
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!first) {
                        urlBuilder.append("&");
                    }
                    urlBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                    urlBuilder.append("=");
                    urlBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                    first = false;
                }
            }

            String url = urlBuilder.toString();
            log.info("Executing HTTP proxy request: {} {}", method, url);

            // Build request
            Request.Builder requestBuilder = new Request.Builder().url(url);

            // Add standard headers
            requestBuilder.addHeader("SAP-Client", sapClient);

            // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
            addAuthHeader(requestBuilder, session);

            // Add session context headers if available
            if (session.getContextId() != null) {
                requestBuilder.addHeader("sap-contextid", session.getContextId());
                requestBuilder.addHeader("x-sap-adt-sessiontype", "stateful");
            }
            if (session.getConnectionId() != null) {
                requestBuilder.addHeader("sap-adt-connection-id", session.getConnectionId());
            }
            if (session.getSaplbToken() != null) {
                requestBuilder.addHeader("saplb", session.getSaplbToken());
            }

            // Add custom headers (skip sap-client and authorization which are already set)
            if (customHeaders != null) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    String headerName = entry.getKey().toLowerCase();
                    if (!headerName.equals("sap-client") && !headerName.equals("authorization")) {
                        requestBuilder.addHeader(entry.getKey(), entry.getValue());
                    }
                }
            }

            // For POST/PUT: fetch CSRF token
            String upperMethod = method.toUpperCase();
            if (upperMethod.equals("POST") || upperMethod.equals("PUT")) {
                String csrfToken = fetchCsrfToken(destination, url, sessionClient, sessionCsrfCache, session);
                requestBuilder.addHeader("x-csrf-token", csrfToken);
            }

            // Set HTTP method with appropriate body
            RequestBody requestBody = null;
            if (body != null && !body.isEmpty()) {
                // Try to determine content type from custom headers
                String contentType = "text/plain; charset=utf-8";
                if (customHeaders != null && customHeaders.containsKey("Content-Type")) {
                    contentType = customHeaders.get("Content-Type");
                }
                requestBody = RequestBody.create(body, MediaType.parse(contentType));
            } else if (upperMethod.equals("POST") || upperMethod.equals("PUT")) {
                // Empty body for POST/PUT
                requestBody = RequestBody.create("", null);
            }

            switch (upperMethod) {
                case "GET":
                    requestBuilder.get();
                    break;
                case "POST":
                    requestBuilder.post(requestBody);
                    break;
                case "PUT":
                    requestBuilder.put(requestBody);
                    break;
                case "DELETE":
                    if (requestBody != null) {
                        requestBuilder.delete(requestBody);
                    } else {
                        requestBuilder.delete();
                    }
                    break;
                default:
                    return com.sapjco.mcp.model.HttpProxyResponse.error("Unsupported HTTP method: " + method);
            }

            Request request = requestBuilder.build();

            // Configure timeout if different from default
            OkHttpClient clientWithTimeout = sessionClient;
            if (timeoutMs > 0 && timeoutMs != 30000) {
                clientWithTimeout = sessionClient.newBuilder()
                        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .build();
            }

            // Execute request
            try (Response response = clientWithTimeout.newCall(request).execute()) {
                // Build response headers (use LinkedHashMap to preserve order)
                Map<String, String> responseHeaders = new LinkedHashMap<>();
                for (String name : response.headers().names()) {
                    responseHeaders.put(name, response.headers().get(name));
                }

                // Get response body
                String responseBody = response.body() != null ? response.body().string() : "";

                log.info("HTTP proxy response: {} {} (body length: {})",
                        response.code(), response.message(), responseBody.length());

                return new com.sapjco.mcp.model.HttpProxyResponse(
                        response.code(),
                        responseHeaders,
                        responseBody
                );
            }

        } catch (Exception e) {
            log.error("HTTP proxy request failed: {}", e.getMessage(), e);
            return com.sapjco.mcp.model.HttpProxyResponse.error(
                    "HTTP proxy request failed: " + e.getMessage()
            );
        }
    }

    // ==================== DEBUGGER OPERATIONS ====================

    /**
     * Attach to a debuggee session.
     * This establishes a debug session that subsequent debug operations will use.
     *
     * @param destination JCo destination
     * @param debuggeeId The debuggee ID to attach to
     * @param requestUser The user to debug
     * @param dynproDebugging Whether to enable dynpro debugging
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Attach response XML
     * @throws IOException if attach fails
     */
    public String debugAttach(JCoDestination destination, String debuggeeId, String requestUser,
                              boolean dynproDebugging, OkHttpClient sessionClient,
                              Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session)
            throws Exception {

        log.info("🔗 Attaching to debuggee: {} for user: {}", debuggeeId, requestUser);

        // Get CSRF token - use discovery endpoint since debugger doesn't support GET
        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        // Build URI with query parameters
        String uri = "/sap/bc/adt/debugger" +
                "?method=attach" +
                "&debuggeeId=" + debuggeeId +
                "&dynproDebugging=" + dynproDebugging +
                "&debuggingMode=user" +
                "&requestUser=" + requestUser +
                "&activeStackPosition=-1";

        // Build headers map
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        headers.put("sap-adt-connection-id", session.getConnectionId());
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        // Call via RFC wrapper
        String responseBody = callViaRfc(destination, "POST", uri, headers, "");

        log.info("✓ Attached to debuggee successfully");
        return responseBody;
    }

    /**
     * Set debugger settings (required after attach).
     *
     * @param destination JCo destination
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Settings response XML
     * @throws IOException if operation fails
     */
    public String debugSetSettings(JCoDestination destination, OkHttpClient sessionClient,
                                   Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session)
            throws Exception {

        log.info("⚙️ Setting debugger settings");

        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        String settingsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<dbg:settings xmlns:dbg=\"http://www.sap.com/adt/debugger\" " +
                "systemDebugging=\"false\" createExceptionObject=\"false\" " +
                "backgroundRFC=\"false\" sharedObjectDebugging=\"false\" " +
                "showDataAging=\"false\"></dbg:settings>";

        String uri = "/sap/bc/adt/debugger?method=setDebuggerSettings&activeStackPosition=-1";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        headers.put("sap-adt-connection-id", session.getConnectionId());
        headers.put("Content-Type", "application/xml");
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        String responseBody = callViaRfc(destination, "POST", uri, headers, settingsXml);

        log.info("✓ Debugger settings applied");
        return responseBody;
    }

    /**
     * Get the call stack.
     *
     * @param destination JCo destination
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Stack XML
     * @throws IOException if operation fails
     */
    public String debugGetStack(JCoDestination destination, OkHttpClient sessionClient,
                                Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session)
            throws Exception {

        log.info("📚 Getting call stack");

        String uri = "/sap/bc/adt/debugger/stack?emode=_&semanticURIs=true&activeStackPosition=-1";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        headers.put("sap-adt-connection-id", session.getConnectionId());
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        String responseBody = callViaRfc(destination, "GET", uri, headers, "");

        log.info("✓ Got call stack");
        return responseBody;
    }

    /**
     * Get child variables from a parent hierarchy.
     *
     * @param destination JCo destination
     * @param parentId Parent ID (e.g., "@ROOT")
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Variables XML
     * @throws IOException if operation fails
     */
    public String debugGetChildVariables(JCoDestination destination, String parentId,
                                         OkHttpClient sessionClient, Map<String, String> sessionCsrfCache,
                                         com.sapjco.mcp.model.JcoSession session) throws Exception {

        log.info("📊 Getting child variables for parent: {}", parentId);

        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        String requestXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<asx:abap version=\"1.0\" xmlns:asx=\"http://www.sap.com/abapxml\">" +
                "<asx:values><DATA><HIERARCHIES><STPDA_ADT_VARIABLE_HIERARCHY>" +
                "<PARENT_ID>" + parentId + "</PARENT_ID>" +
                "</STPDA_ADT_VARIABLE_HIERARCHY></HIERARCHIES></DATA></asx:values></asx:abap>";

        String uri = "/sap/bc/adt/debugger?method=getChildVariables&stackPosition=-1&activeStackPosition=-1";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        headers.put("sap-adt-connection-id", session.getConnectionId());
        headers.put("Content-Type", "application/vnd.sap.as+xml; charset=UTF-8; dataname=com.sap.adt.debugger.ChildVariables");
        headers.put("Accept", "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.debugger.ChildVariables");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        String responseBody = callViaRfc(destination, "POST", uri, headers, requestXml);

        log.info("✓ Got child variables");
        return responseBody;
    }

    /**
     * Execute a debug step operation (stepOver, stepInto, stepReturn).
     *
     * @param destination JCo destination
     * @param stepType Step type (stepOver, stepInto, stepReturn)
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Step response XML
     * @throws IOException if operation fails
     */
    public String debugStep(JCoDestination destination, String stepType,
                            OkHttpClient sessionClient, Map<String, String> sessionCsrfCache,
                            com.sapjco.mcp.model.JcoSession session) throws Exception {

        log.info("⏭️ Executing debug step: {}", stepType);

        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        String uri = "/sap/bc/adt/debugger?method=" + stepType + "&activeStackPosition=-1";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        headers.put("sap-adt-connection-id", session.getConnectionId());
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        String responseBody = callViaRfc(destination, "POST", uri, headers, "");

        log.info("✓ Debug step {} executed", stepType);
        return responseBody;
    }

    /**
     * Resume debug execution.
     *
     * @param destination JCo destination
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Resume response XML
     * @throws Exception if operation fails
     */
    public String debugResume(JCoDestination destination, OkHttpClient sessionClient,
                              Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session)
            throws Exception {

        log.info("▶️ Resuming debug execution");

        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        String uri = "/sap/bc/adt/debugger?method=stepContinue";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        headers.put("sap-adt-connection-id", session.getConnectionId());
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        String responseBody = callViaRfc(destination, "POST", uri, headers, "");

        log.info("✓ Debug execution resumed");
        return responseBody;
    }

    /**
     * Detach from the debugger, releasing the debuggee and clearing the SAP-side
     * debug session singleton. This is essential for enabling consecutive debug
     * sessions within the same JCo/RFC connection.
     *
     * @param destination JCo destination
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Detach response XML
     * @throws Exception if operation fails
     */
    public String debugDetach(JCoDestination destination, OkHttpClient sessionClient,
                              Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session)
            throws Exception {

        log.info("🔌 Detaching from debugger");

        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        String uri = "/sap/bc/adt/debugger?method=detachDebugger";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        headers.put("sap-adt-connection-id", session.getConnectionId());
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        String responseBody = callViaRfc(destination, "POST", uri, headers, "");

        log.info("✓ Detached from debugger");
        return responseBody;
    }

    /**
     * Set a variable value during debugging.
     *
     * @param destination JCo destination
     * @param variableName Variable identifier (e.g., "@LOCAL.LV_VALUE")
     * @param newValue The new value to set
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Response XML
     * @throws Exception if operation fails
     */
    public String debugSetVariableValue(JCoDestination destination, String variableName,
                                        String newValue, OkHttpClient sessionClient,
                                        Map<String, String> sessionCsrfCache,
                                        com.sapjco.mcp.model.JcoSession session) throws Exception {

        log.info("📝 Setting variable: {} = '{}'", variableName, newValue);

        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        // URL encode the variable name for the query parameter
        String encodedVariableName = URLEncoder.encode(variableName, StandardCharsets.UTF_8);
        String uri = "/sap/bc/adt/debugger?method=setVariableValue&variableName=" + encodedVariableName + "&activeStackPosition=-1";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        headers.put("sap-adt-connection-id", session.getConnectionId());
        headers.put("Content-Type", "text/plain; charset=utf-8");
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        String responseBody = callViaRfc(destination, "POST", uri, headers, newValue);

        log.info("✓ Variable value set");
        return responseBody;
    }

    /**
     * Step to a specific line (jump or run to line).
     *
     * @param destination JCo destination
     * @param stepType Step type: "stepJumpToLine" (skip code) or "stepRunToLine" (execute until line)
     * @param sourceUri Source URI with line position (e.g., "/sap/bc/adt/oo/classes/zclass/includes/testclasses#start=26")
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Response XML
     * @throws Exception if operation fails
     */
    public String debugStepToLine(JCoDestination destination, String stepType, String sourceUri,
                                  OkHttpClient sessionClient, Map<String, String> sessionCsrfCache,
                                  com.sapjco.mcp.model.JcoSession session) throws Exception {

        log.info("🎯 {} to: {}", stepType, sourceUri);

        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        // URL encode the source URI for the query parameter
        String encodedUri = URLEncoder.encode(sourceUri, StandardCharsets.UTF_8);
        String uri = "/sap/bc/adt/debugger?method=" + stepType + "&uri=" + encodedUri + "&activeStackPosition=-1";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        headers.put("sap-adt-connection-id", session.getConnectionId());
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        String responseBody = callViaRfc(destination, "POST", uri, headers, "");

        log.info("✓ {} completed", stepType);
        return responseBody;
    }

    // ==================== BREAKPOINT/LISTENER ADT REST OPERATIONS ====================
    // These operations route ADT REST calls through the session's HTTP client for SSO support

    /**
     * Set an external breakpoint via ADT REST API.
     * Routes through session's HTTP client for proper SSO/SNC authentication.
     *
     * @param destination JCo destination
     * @param breakpointXml The breakpoint XML payload
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Response XML
     * @throws Exception if operation fails
     */
    public String debugSetBreakpointRest(JCoDestination destination,
                                         String breakpointXml,
                                         OkHttpClient sessionClient,
                                         Map<String, String> sessionCsrfCache,
                                         com.sapjco.mcp.model.JcoSession session) throws Exception {

        log.info("📍 Setting breakpoint via ADT REST");

        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        String uri = "/sap/bc/adt/debugger/breakpoints";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        if (session.getConnectionId() != null) {
            headers.put("sap-adt-connection-id", session.getConnectionId());
        }
        headers.put("Content-Type", "application/xml");
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        String responseBody = callViaRfc(destination, "POST", uri, headers, breakpointXml);

        log.info("✓ Breakpoint set via ADT REST");
        return responseBody;
    }

    /**
     * Start debug listener via ADT REST API (long-polling).
     * Routes through session's HTTP client for proper SSO/SNC authentication.
     *
     * @param destination JCo destination
     * @param queryParams Query parameters (debuggingMode, requestUser, terminalId, ideId, checkConflict, isNotifiedOnConflict)
     * @param timeoutMs Timeout in milliseconds for the long-polling request
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @return Response XML (contains debuggee info if breakpoint was hit)
     * @throws Exception if operation fails
     */
    public String debugStartListenerRest(JCoDestination destination,
                                         Map<String, String> queryParams,
                                         int timeoutMs,
                                         OkHttpClient sessionClient,
                                         Map<String, String> sessionCsrfCache,
                                         com.sapjco.mcp.model.JcoSession session) throws Exception {

        log.info("👂 Starting debug listener via ADT REST (timeout: {}ms)", timeoutMs);

        // Build URI with query parameters
        StringBuilder uriBuilder = new StringBuilder("/sap/bc/adt/debugger/listeners?");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!first) {
                uriBuilder.append("&");
            }
            uriBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            uriBuilder.append("=");
            uriBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        String uri = uriBuilder.toString();

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        if (session.getConnectionId() != null) {
            headers.put("sap-adt-connection-id", session.getConnectionId());
        }
        headers.put("Accept", "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.debugger.DebuggeesList");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        // Note: RFC timeout is handled by SAP server-side, not client-side
        // The RFC call will return when a debuggee is detected or SAP times out
        String responseBody = callViaRfc(destination, "POST", uri, headers, "");

        log.info("✓ Listener response received");
        return responseBody;
    }

    /**
     * Delete debug listener and breakpoints via ADT REST API.
     * Routes through session's HTTP client for proper SSO/SNC authentication.
     *
     * @param destination JCo destination
     * @param queryParams Query parameters (debuggingMode, requestUser, terminalId, ideId)
     * @param sessionClient Session-specific HTTP client
     * @param sessionCsrfCache Session-specific CSRF cache
     * @param session Full JcoSession object
     * @throws Exception if operation fails
     */
    public void debugDeleteBreakpointRest(JCoDestination destination,
                                          Map<String, String> queryParams,
                                          OkHttpClient sessionClient,
                                          Map<String, String> sessionCsrfCache,
                                          com.sapjco.mcp.model.JcoSession session) throws Exception {

        log.info("🗑️ Deleting breakpoint/listener via ADT REST");

        String csrfToken = fetchCsrfToken(destination, adtBaseUrl + "/discovery", sessionClient, sessionCsrfCache, session);

        // Build URI with query parameters
        StringBuilder uriBuilder = new StringBuilder("/sap/bc/adt/debugger/listeners?");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!first) {
                uriBuilder.append("&");
            }
            uriBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            uriBuilder.append("=");
            uriBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        String uri = uriBuilder.toString();

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("SAP-Client", sapClient);
        addAuthHeader(headers, session);
        headers.put("x-csrf-token", csrfToken);
        headers.put("x-sap-adt-sessiontype", "stateful");
        headers.put("sap-contextid", session.getContextId());
        if (session.getConnectionId() != null) {
            headers.put("sap-adt-connection-id", session.getConnectionId());
        }
        headers.put("Accept", "application/xml");
        if (session.getSaplbToken() != null) {
            headers.put("saplb", session.getSaplbToken());
        }

        callViaRfc(destination, "DELETE", uri, headers, "");

        log.info("✓ Breakpoint/listener deleted via ADT REST");
    }

    // ==================== END DEBUGGER OPERATIONS ====================

    /**
     * Fetch CSRF token from SAP using session-specific HTTP client and cache.
     * IMPORTANT: Cache is per-session to avoid cross-session token pollution.
     * Also captures saplb token for load balancer affinity.
     *
     * For SNC SSO sessions (useRfcProxy=true), routes through RFC proxy.
     */
    private String fetchCsrfToken(JCoDestination destination, String url, OkHttpClient sessionClient, Map<String, String> sessionCsrfCache, com.sapjco.mcp.model.JcoSession session) throws IOException {
        // Route through RFC for SNC SSO sessions (same pattern as executeHttpRequest)
        if (session.isUseRfcProxy()) {
            return fetchCsrfTokenViaRfc(destination, session);
        }

        // Check session-specific cache first
        String cached = sessionCsrfCache.get(url);
        if (cached != null) {
            log.debug("Using cached CSRF token for: {}", url);
            return cached;
        }

        log.debug("Fetching CSRF token for: {}", url);

        // Use GET instead of HEAD - SAP ADT requires GET for initial session establishment
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get()
                .addHeader("SAP-Client", sapClient)
                .addHeader("x-csrf-token", "fetch")
                .addHeader("x-sap-adt-sessiontype", "stateful")       // NEW: Indicate stateful session
                .addHeader("sap-contextid", session.getContextId())  // NEW: Session context ID
                .addHeader("Accept", "*/*");

        // Add Authorization header for Basic auth (X.509/SSO auth is handled at TLS level)
        addAuthHeader(requestBuilder, session);

        Request request = requestBuilder.build();

        // Use session-specific HTTP client (passed as parameter)
        try (Response response = sessionClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = AdtResponseHandler.extractBody(response);
                log.error("CSRF fetch failed: {} - {}", response.code(), body.substring(0, Math.min(200, body.length())));
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.code(), body, "CSRF fetch"));
            }

            String token = response.header("x-csrf-token");
            if (token == null || token.isEmpty()) {
                throw new IOException("No CSRF token in response");
            }

            // NEW: Extract and store saplb token for load balancer affinity
            String saplb = response.header("saplb");
            if (saplb != null && !saplb.isEmpty()) {
                session.setSaplbToken(saplb);
                log.debug("✓ Captured saplb token: {}", saplb);
            }

            // Cache in session-specific cache
            sessionCsrfCache.put(url, token);
            log.debug("✓ CSRF token fetched and cached in session: {}", token.substring(0, Math.min(20, token.length())) + "...");

            return token;
        }
    }

    /**
     * Fetch CSRF token via RFC proxy (SADT_REST_RFC_ENDPOINT).
     * Used for SNC SSO sessions where direct HTTP calls fail due to authentication.
     *
     * NOTE: SAP ADT via RFC may not require CSRF tokens since RFC provides
     * its own security context. This method returns a placeholder token.
     *
     * @param destination JCo destination
     * @param session JcoSession with context and cache
     * @return CSRF token (may be placeholder for RFC)
     * @throws IOException if token fetch fails
     */
    private String fetchCsrfTokenViaRfc(JCoDestination destination, com.sapjco.mcp.model.JcoSession session) throws IOException {
        // Check session-specific cache first
        String cacheKey = adtBaseUrl + "/discovery";
        Map<String, String> sessionCsrfCache = session.getCsrfTokenCache();
        String cached = sessionCsrfCache.get(cacheKey);
        if (cached != null) {
            log.debug("Using cached CSRF token (via RFC) for: {}", cacheKey);
            return cached;
        }

        log.info("📡 Fetching CSRF token via RFC proxy...");

        try {
            // Build headers map for CSRF fetch
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("SAP-Client", sapClient);
            headers.put("x-csrf-token", "fetch");
            headers.put("x-sap-adt-sessiontype", "stateful");
            if (session.getContextId() != null) {
                headers.put("sap-contextid", session.getContextId());
            }
            if (session.getConnectionId() != null) {
                headers.put("sap-adt-connection-id", session.getConnectionId());
            }
            headers.put("Accept", "*/*");

            // Execute via RFC
            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "GET", "/sap/bc/adt/discovery", null, headers, null, session);

            if (response.getStatusCode() >= 400) {
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.getStatusCode(), response.getBody(), "CSRF fetch via RFC"));
            }

            // Extract CSRF token from response headers
            log.info("📋 RFC response headers: {}", response.getHeaders().keySet());
            String token = response.getHeaders().get("x-csrf-token");
            if (token == null || token.isEmpty()) {
                // Try case-insensitive search
                for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("x-csrf-token")) {
                        token = entry.getValue();
                        break;
                    }
                }
            }

            // If no CSRF token in response, RFC doesn't require it - use placeholder
            if (token == null || token.isEmpty()) {
                log.info("⚠️ No CSRF token in RFC response (Status: {}, Headers: {}). Using RFC security context - no CSRF token needed.",
                         response.getStatusCode(), response.getHeaders().keySet());
                token = "RFC_SECURITY_CONTEXT";
            }

            // Extract and store saplb token for load balancer affinity
            String saplb = response.getHeaders().get("saplb");
            if (saplb == null) {
                for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("saplb")) {
                        saplb = entry.getValue();
                        break;
                    }
                }
            }
            if (saplb != null && !saplb.isEmpty()) {
                session.setSaplbToken(saplb);
                log.debug("✓ Captured saplb token via RFC: {}", saplb);
            }

            // Cache in session-specific cache
            sessionCsrfCache.put(cacheKey, token);
            log.info("✓ CSRF token fetched via RFC and cached: {}...", token.substring(0, Math.min(20, token.length())));

            return token;

        } catch (Exception e) {
            throw new IOException("Failed to fetch CSRF token via RFC: " + e.getMessage(), e);
        }
    }

    /**
     * Lock an ABAP object via RFC proxy for SNC SSO sessions.
     * Routes the lock request through SADT_REST_RFC_ENDPOINT.
     *
     * @param destination JCo destination
     * @param objectName Object name
     * @param objectType Object type
     * @param uri Lock URI with query parameters
     * @param session JCo session
     * @return LockResponse with lock handle
     * @throws IOException if lock fails
     */
    private LockResponse lockObjectViaRfc(JCoDestination destination, String objectName, String objectType,
                                          String uri, com.sapjco.mcp.model.JcoSession session) throws IOException {
        log.info("🔒 Locking via RFC: {} {}", objectType, objectName);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildStandardHeaders(session, sapClient);
            headers.put(HttpHeaderBuilder.HEADER_ACCEPT, "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9");

            // Execute via RFC - no CSRF token needed (RFC provides its own security context)
            String responseBody = callViaRfc(destination, "POST", uri, headers, "");

            // Parse XML response body to extract lock handle
            String lockHandle = extractXmlValue(responseBody, "LOCK_HANDLE");
            String transportNumber = extractXmlValue(responseBody, "CORRNR");

            if (lockHandle == null || lockHandle.isEmpty()) {
                throw new IOException("No lock handle returned in RFC response. Response: " + responseBody);
            }

            log.info("✓ Object locked via RFC - Handle: {}, Transport: {}", lockHandle, transportNumber);
            return new LockResponse(lockHandle, transportNumber);

        } catch (Exception e) {
            throw new IOException("Lock via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Unlock an ABAP object via RFC proxy for SNC SSO sessions.
     * Routes the unlock request through SADT_REST_RFC_ENDPOINT.
     *
     * @param destination JCo destination
     * @param objectName Object name
     * @param objectType Object type
     * @param uri Unlock URI with query parameters
     * @param session JCo session
     * @return Raw SAP response body
     * @throws IOException if unlock fails
     */
    private String unlockObjectViaRfc(JCoDestination destination, String objectName, String objectType,
                                    String uri, com.sapjco.mcp.model.JcoSession session) throws IOException {
        log.info("🔓 Unlocking via RFC: {} {}", objectType, objectName);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildStandardHeaders(session, sapClient);
            headers.put(HttpHeaderBuilder.HEADER_ACCEPT, "*/*");

            // Execute via RFC - no CSRF token needed
            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "POST", uri, null, headers, "", session);

            if (response.getStatusCode() >= 400) {
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.getStatusCode(), response.getBody(), "Unlock via RFC"));
            }

            log.info("✓ Object unlocked via RFC");
            return response.getBody();

        } catch (Exception e) {
            throw new IOException("Unlock via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Save ABAP object source via RFC proxy for SNC SSO sessions.
     * Routes the save request through SADT_REST_RFC_ENDPOINT.
     *
     * @param destination JCo destination
     * @param objectName Object name
     * @param objectType Object type
     * @param uri Save URI with query parameters (lockHandle, corrNr)
     * @param sourceCode Source code to save
     * @param session JCo session
     * @return Raw SAP response body
     * @throws IOException if save fails
     */
    private String saveObjectViaRfc(JCoDestination destination, String objectName, String objectType,
                                  String uri, String sourceCode, com.sapjco.mcp.model.JcoSession session) throws IOException {
        log.info("💾 Saving via RFC: {} {}", objectType, objectName);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildStandardHeaders(session, sapClient);
            headers.put(HttpHeaderBuilder.HEADER_CONTENT_TYPE, "text/plain; charset=utf-8");
            headers.put(HttpHeaderBuilder.HEADER_ACCEPT, "*/*");

            // Execute via RFC - use PUT method, no CSRF token needed
            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "PUT", uri, null, headers, sourceCode, session);

            if (response.getStatusCode() >= 400) {
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.getStatusCode(), response.getBody(), "Save via RFC"));
            }

            log.info("✓ Object saved via RFC");
            return response.getBody();

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Save via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Activate an ABAP object via RFC proxy for SNC SSO sessions.
     * Routes the activation request through SADT_REST_RFC_ENDPOINT.
     *
     * @param destination JCo destination
     * @param objectName Object name
     * @param objectType Object type
     * @param uri Activation URI
     * @param queryParams Query parameters for activation (method, preauditRequested)
     * @param activationXml Activation XML payload
     * @param session JCo session
     * @return Raw SAP response body
     * @throws IOException if activation fails
     */
    private String activateObjectViaRfc(JCoDestination destination, String objectName, String objectType,
                                      String uri, Map<String, String> queryParams, String activationXml, com.sapjco.mcp.model.JcoSession session) throws IOException {
        log.info("⚡ Activating via RFC: {} {}", objectType, objectName);

        try {
            Map<String, String> headers = RfcProxyExecutor.buildStandardHeaders(session, sapClient);
            headers.put(HttpHeaderBuilder.HEADER_CONTENT_TYPE, "application/xml");
            headers.put(HttpHeaderBuilder.HEADER_ACCEPT, "*/*");

            // Execute via RFC - no CSRF token needed, pass query params
            com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                    destination, "POST", uri, queryParams, headers, activationXml, session);

            if (response.getStatusCode() >= 400) {
                throw new IOException(AdtResponseHandler.formatErrorMessage(response.getStatusCode(), response.getBody(), "Activation via RFC"));
            }

            log.info("✓ Object activated via RFC");
            return response.getBody();

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Activation via RFC failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get authentication header value.
     * Returns null for X.509/SNC auth (authentication happens at TLS/RFC level).
     *
     * @param session Optional session to check for SSO mode
     */
    private String getAuthHeader(com.sapjco.mcp.model.JcoSession session) {
        // For SSO sessions, RFC handles authentication - no Authorization header needed
        if (session != null && session.isHttpSsoEnabled()) {
            log.debug("SSO session - RFC proxy handles auth, no Authorization header needed");
            return null;
        }

        if ("x509".equalsIgnoreCase(authType) || "snc".equalsIgnoreCase(authType)) {
            // X.509/SNC authentication - no Authorization header needed
            // Authentication happens at the TLS handshake level or via RFC
            return null;
        }

        // Basic authentication
        String credentials = sapUsername + ":" + sapPassword;
        String encoded = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8)
        );
        return "Basic " + encoded;
    }

    /**
     * Get authentication header value (backward compatible - no session context).
     * Returns null for X.509 auth (authentication happens at TLS level).
     */
    private String getAuthHeader() {
        return getAuthHeader(null);
    }

    /**
     * Create Basic Auth header value.
     * @deprecated Use getAuthHeader() instead for multi-auth support
     */
    @Deprecated
    private String getBasicAuth() {
        return getAuthHeader();
    }

    /**
     * Add authentication header to request builder if needed.
     * For X.509/SSO auth, no Authorization header is added.
     *
     * @param builder Request builder
     * @param session Optional session to check for SSO mode
     */
    private Request.Builder addAuthHeader(Request.Builder builder, com.sapjco.mcp.model.JcoSession session) {
        // For SSO sessions, RFC proxy handles authentication - no auth header needed
        if (session != null && session.isHttpSsoEnabled()) {
            log.debug("SSO session - RFC proxy handles auth, skipping Authorization header");
            return builder;
        }

        // For non-SSO sessions, use basic auth if configured
        String authHeader = getAuthHeader(session);
        if (authHeader != null) {
            builder.addHeader("Authorization", authHeader);
        }
        return builder;
    }

    /**
     * Add authentication header to request builder if needed (backward compatible).
     * For X.509 auth, no Authorization header is added.
     */
    private Request.Builder addAuthHeader(Request.Builder builder) {
        return addAuthHeader(builder, null);
    }

    /**
     * Add authentication header to headers map if needed.
     * For X.509/SSO auth, no Authorization header is added.
     *
     * @param headers Headers map
     * @param session Optional session to check for SSO mode
     */
    private void addAuthHeader(Map<String, String> headers, com.sapjco.mcp.model.JcoSession session) {
        // For SSO sessions, RFC proxy handles authentication - no auth header needed
        if (session != null && session.isHttpSsoEnabled()) {
            log.debug("SSO session - RFC proxy handles auth, skipping Authorization header");
            return;
        }

        // For non-SSO sessions, use basic auth if configured
        String authHeader = getAuthHeader(session);
        if (authHeader != null) {
            headers.put("Authorization", authHeader);
        }
    }

    /**
     * Add authentication header to headers map if needed (backward compatible).
     * For X.509 auth, no Authorization header is added.
     */
    private void addAuthHeader(Map<String, String> headers) {
        addAuthHeader(headers, null);
    }

    /**
     * Create OkHttpClient with SSL configuration and session-specific cookie jar.
     * Supports both basic SSL (trust all certs for dev) and X.509 client certificate auth.
     */
    private OkHttpClient createHttpClient(CookieJar cookieJar) {
        try {
            SSLContext sslContext;
            X509TrustManager trustManager;
            KeyManager[] keyManagers = null;

            // Configure X.509 client certificate if enabled
            if ("x509".equalsIgnoreCase(authType) && certificatePath != null && !certificatePath.isEmpty()) {
                // Load client certificate and private key
                keyManagers = loadClientCertificate();
                log.info("X.509 client certificate loaded from: {}", certificatePath);
            }

            // Trust manager - for dev, trust all certs; for production, use proper CA validation
            if (caCertificatePath != null && !caCertificatePath.isEmpty() && Files.exists(Paths.get(caCertificatePath))) {
                // Load custom CA certificate for server validation
                trustManager = loadCustomTrustManager();
                log.info("Custom CA certificate loaded from: {}", caCertificatePath);
            } else {
                // Trust all certificates (for development with self-signed certs)
                trustManager = createTrustAllManager();
            }

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[]{trustManager}, new java.security.SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                    .hostnameVerifier((hostname, session) -> true)
                    .cookieJar(cookieJar)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create HTTP client: " + e.getMessage(), e);
        }
    }

    /**
     * Load client certificate and private key for X.509 authentication.
     */
    private KeyManager[] loadClientCertificate() throws Exception {
        // For PEM files, we need to convert to PKCS12 keystore
        // This is a simplified implementation - in production, use proper PEM parsing libraries
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        // Check if the certificate path points to a PKCS12 file directly
        if (certificatePath.endsWith(".p12") || certificatePath.endsWith(".pfx")) {
            // Load directly from PKCS12 file
            char[] passChars = keyPassphrase != null ? keyPassphrase.toCharArray() : "".toCharArray();
            try (FileInputStream fis = new FileInputStream(certificatePath)) {
                keyStore.load(fis, passChars);
            }
        } else {
            // For PEM files, we'll need to combine cert and key
            // This requires a more complex implementation using Bouncy Castle or similar
            // For now, log a warning and recommend using PKCS12 format
            log.warn("PEM certificate files detected. For best compatibility, consider using PKCS12 (.p12) format.");
            log.warn("You can convert using: openssl pkcs12 -export -in cert.pem -inkey key.pem -out client.p12");

            // Try to load as PKCS12 anyway (might work if file is actually PKCS12)
            char[] passChars = keyPassphrase != null ? keyPassphrase.toCharArray() : "".toCharArray();
            try (FileInputStream fis = new FileInputStream(certificatePath)) {
                keyStore.load(fis, passChars);
            }
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        char[] passChars = keyPassphrase != null ? keyPassphrase.toCharArray() : "".toCharArray();
        kmf.init(keyStore, passChars);

        return kmf.getKeyManagers();
    }

    /**
     * Load custom trust manager with CA certificate.
     */
    private X509TrustManager loadCustomTrustManager() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        // Load CA certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(caCertificatePath)) {
            X509Certificate caCert = (X509Certificate) cf.generateCertificate(fis);
            trustStore.setCertificateEntry("ca", caCert);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }

        throw new RuntimeException("Could not create X509TrustManager from CA certificate");
    }

    /**
     * Create a trust manager that trusts all certificates (for development).
     */
    private X509TrustManager createTrustAllManager() {
        return new X509TrustManager() {
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
        };
    }

    /**
     * Create default OkHttpClient (for backwards compatibility).
     */
    private OkHttpClient createHttpClient() {
        // Create in-memory cookie jar
        CookieJar cookieJar = new CookieJar() {
            private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                cookieStore.put(url.host(), cookies);
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> cookies = cookieStore.get(url.host());
                return cookies != null ? cookies : new ArrayList<>();
            }
        };
        return createHttpClient(cookieJar);
    }

    /**
     * Extract value from XML response using simple tag matching.
     * For production use, consider using a proper XML parser.
     */
    private String extractXmlValue(String xml, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int startIdx = xml.indexOf(openTag);
        if (startIdx == -1) {
            return null;
        }
        startIdx += openTag.length();
        int endIdx = xml.indexOf(closeTag, startIdx);
        if (endIdx == -1) {
            return null;
        }
        return xml.substring(startIdx, endIdx);
    }

    /**
     * Execute HTTP request via SADT_REST_RFC_ENDPOINT (RFC-wrapped HTTP).
     * Used for SNC SSO authentication where direct HTTP fails.
     * This is the SAME pattern Eclipse ADT uses for all HTTP requests.
     *
     * @param destination JCo destination
     * @param method HTTP method
     * @param path URL path
     * @param queryParams Query parameters
     * @param customHeaders Custom headers
     * @param body Request body
     * @param session JCo session
     * @return HttpProxyResponse with status, headers, and body
     */
    private com.sapjco.mcp.model.HttpProxyResponse executeHttpRequestViaRfc(
            JCoDestination destination,
            String method,
            String path,
            Map<String, String> queryParams,
            Map<String, String> customHeaders,
            String body,
            com.sapjco.mcp.model.JcoSession session) {

        try {
            log.info("📡 Routing HTTP via RFC: {} {}", method, path);

            // Build the full URI
            StringBuilder uriBuilder = new StringBuilder(path);
            if (queryParams != null && !queryParams.isEmpty()) {
                uriBuilder.append("?");
                boolean first = true;
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!first) {
                        uriBuilder.append("&");
                    }
                    uriBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                    uriBuilder.append("=");
                    uriBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                    first = false;
                }
            }
            String uri = uriBuilder.toString();

            // Build headers map
            Map<String, String> headers = new LinkedHashMap<>();
            // Use session's client instead of env var for multi-system support
            if (session.getClient() != null && !session.getClient().isEmpty()) {
                headers.put("SAP-Client", session.getClient());
            } else if (sapClient != null && !sapClient.isEmpty()) {
                headers.put("SAP-Client", sapClient);
            }

            // Add session context headers
            if (session.getContextId() != null) {
                headers.put("sap-contextid", session.getContextId());
                headers.put("x-sap-adt-sessiontype", "stateful");
            }
            if (session.getConnectionId() != null) {
                headers.put("sap-adt-connection-id", session.getConnectionId());
            }

            // Add custom headers
            if (customHeaders != null) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    String headerName = entry.getKey().toLowerCase();
                    // Skip auth header - RFC handles authentication
                    if (!headerName.equals("authorization")) {
                        headers.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            // Get the RFC function with diagnostic logging
            log.debug("Getting RFC function from repository for destination: {}", destination.getDestinationName());
            com.sap.conn.jco.JCoFunction function;
            try {
                function = destination.getRepository().getFunction("SADT_REST_RFC_ENDPOINT");
            } catch (Exception e) {
                log.error("Failed to get RFC function from repository: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                return com.sapjco.mcp.model.HttpProxyResponse.error("Repository access failed: " + e.getMessage());
            }

            if (function == null) {
                log.error("RFC function SADT_REST_RFC_ENDPOINT not found in repository for destination: {}", destination.getDestinationName());
                return com.sapjco.mcp.model.HttpProxyResponse.error("RFC function SADT_REST_RFC_ENDPOINT not found");
            }
            log.debug("RFC function SADT_REST_RFC_ENDPOINT found successfully");

            // Fill REQUEST structure
            com.sap.conn.jco.JCoStructure requestStruct = function.getImportParameterList()
                    .getStructure("REQUEST");

            // REQUEST_LINE
            com.sap.conn.jco.JCoStructure requestLine = requestStruct.getStructure("REQUEST_LINE");
            requestLine.setValue("METHOD", method.toUpperCase());
            requestLine.setValue("URI", uri);
            requestLine.setValue("VERSION", "HTTP/1.1");

            // HEADER_FIELDS
            com.sap.conn.jco.JCoTable headerTable = requestStruct.getTable("HEADER_FIELDS");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                headerTable.appendRow();
                headerTable.setValue("NAME", header.getKey());
                headerTable.setValue("VALUE", header.getValue());
            }

            // MESSAGE_BODY
            if (body != null && !body.isEmpty()) {
                requestStruct.setValue("MESSAGE_BODY", body.getBytes(StandardCharsets.UTF_8));
            }

            // Execute the RFC call
            function.execute(destination);

            // Parse RESPONSE structure
            com.sap.conn.jco.JCoStructure responseStruct = function.getExportParameterList()
                    .getStructure("RESPONSE");

            // STATUS_LINE
            com.sap.conn.jco.JCoStructure statusLine = responseStruct.getStructure("STATUS_LINE");
            String statusCode = statusLine.getString("STATUS_CODE").trim();
            String reasonPhrase = statusLine.getString("REASON_PHRASE");
            int statusCodeInt = Integer.parseInt(statusCode);

            log.info("✓ RFC HTTP response: {} {}", statusCode, reasonPhrase);

            // HEADER_FIELDS
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            com.sap.conn.jco.JCoTable respHeaderTable = responseStruct.getTable("HEADER_FIELDS");
            for (int i = 0; i < respHeaderTable.getNumRows(); i++) {
                respHeaderTable.setRow(i);
                responseHeaders.put(
                    respHeaderTable.getString("NAME"),
                    respHeaderTable.getString("VALUE")
                );
            }

            // MESSAGE_BODY
            byte[] responseBodyBytes = responseStruct.getByteArray("MESSAGE_BODY");
            String responseBody = responseBodyBytes != null && responseBodyBytes.length > 0
                    ? new String(responseBodyBytes, StandardCharsets.UTF_8)
                    : "";

            return new com.sapjco.mcp.model.HttpProxyResponse(
                    statusCodeInt,
                    responseHeaders,
                    responseBody
            );

        } catch (Exception e) {
            log.error("RFC HTTP request failed: {}", e.getMessage());
            return com.sapjco.mcp.model.HttpProxyResponse.error("RFC HTTP request failed: " + e.getMessage());
        }
    }

    /**
     * Call ADT REST API via RFC wrapper (SADT_REST_RFC_ENDPOINT).
     *
     * This is the KEY to making debug operations work. Eclipse ADT wraps all
     * HTTP REST calls in RFC calls to SADT_REST_RFC_ENDPOINT, which maintains
     * the SAP session context required for debug operations.
     *
     * @param destination JCo destination
     * @param method HTTP method (GET, POST, etc.)
     * @param uri Full URI path with query parameters
     * @param headers HTTP headers as map
     * @param body Request body (empty string for GET)
     * @return Response body as string
     * @throws Exception if RFC call fails
     */
    private String callViaRfc(JCoDestination destination, String method, String uri,
                              Map<String, String> headers, String body) throws Exception {

        log.info("📡 Calling via RFC: {} {}", method, uri);

        // Get the RFC function
        com.sap.conn.jco.JCoFunction function = destination.getRepository()
                .getFunction("SADT_REST_RFC_ENDPOINT");

        if (function == null) {
            throw new Exception("RFC function SADT_REST_RFC_ENDPOINT not found");
        }

        // Get REQUEST structure
        com.sap.conn.jco.JCoStructure requestStruct = function.getImportParameterList()
                .getStructure("REQUEST");

        // Fill REQUEST_LINE sub-structure
        com.sap.conn.jco.JCoStructure requestLine = requestStruct.getStructure("REQUEST_LINE");
        requestLine.setValue("METHOD", method);
        requestLine.setValue("URI", uri);
        requestLine.setValue("VERSION", "HTTP/1.1");

        // Fill HEADER_FIELDS table
        com.sap.conn.jco.JCoTable headerTable = requestStruct.getTable("HEADER_FIELDS");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            headerTable.appendRow();
            headerTable.setValue("NAME", header.getKey());
            headerTable.setValue("VALUE", header.getValue());
        }

        // Fill MESSAGE_BODY
        if (body != null && !body.isEmpty()) {
            requestStruct.setValue("MESSAGE_BODY", body.getBytes(StandardCharsets.UTF_8));
        }

        // Execute the RFC call
        function.execute(destination);

        // Get RESPONSE structure
        com.sap.conn.jco.JCoStructure responseStruct = function.getExportParameterList()
                .getStructure("RESPONSE");

        // Extract STATUS_LINE
        com.sap.conn.jco.JCoStructure statusLine = responseStruct.getStructure("STATUS_LINE");
        String statusCode = statusLine.getString("STATUS_CODE").trim();
        String reasonPhrase = statusLine.getString("REASON_PHRASE");

        log.info("✓ RFC response: {} {}", statusCode, reasonPhrase);

        // Extract response body
        byte[] responseBodyBytes = responseStruct.getByteArray("MESSAGE_BODY");
        String responseBody = responseBodyBytes != null && responseBodyBytes.length > 0
                ? new String(responseBodyBytes, StandardCharsets.UTF_8)
                : "";

        // Check for errors
        int statusCodeInt = Integer.parseInt(statusCode);
        if (statusCodeInt >= 400) {
            log.error("RFC call failed with status {}: {}", statusCode,
                    responseBody.substring(0, Math.min(500, responseBody.length())));

            // Log full response body for debugging
            log.error("Full response body: {}", responseBody);

            // Write to file for detailed analysis
            try {
                java.nio.file.Files.write(
                    java.nio.file.Paths.get("investigation/debug-error-response.xml"),
                    responseBody.getBytes(StandardCharsets.UTF_8)
                );
                log.error("Full error response written to: investigation/debug-error-response.xml");
            } catch (Exception writeEx) {
                log.warn("Could not write error response to file: {}", writeEx.getMessage());
            }

            // Special case: "debuggeeEnded" is expected when program completes
            if (responseBody.contains("debuggeeEnded")) {
                log.info("ℹ️ Debuggee session ended (program completed execution)");
                return responseBody; // Return the response, don't throw exception
            }

            // Include response body in exception for debugging
            String errorMsg = "RFC call failed: " + statusCode + " - " + reasonPhrase;
            if (!responseBody.isEmpty()) {
                errorMsg += " | Response: " + responseBody.substring(0, Math.min(3000, responseBody.length()));
            }
            throw new IOException(errorMsg);
        }

        return responseBody;
    }

    /**
     * Extract URI path from full URL for RFC calls.
     * RFC endpoint expects path starting with /sap/bc/adt, not just the relative path.
     *
     * Example input: "https://host:443/sap/bc/adt/oo/classes/ZTEST?_action=LOCK"
     * Example output: "/sap/bc/adt/oo/classes/ZTEST?_action=LOCK"
     */
    private String extractUriPath(String url) {
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            String path = parsedUrl.getPath();
            String query = parsedUrl.getQuery();
            return query != null ? path + "?" + query : path;
        } catch (Exception e) {
            // If URL parsing fails, assume it's already a path
            log.warn("Failed to parse URL, assuming it's already a path: {}", url);
            return url;
        }
    }

    // ========================================================================
    // RFC-Routed Read Operations (all operations go through SADT_REST_RFC_ENDPOINT)
    // ========================================================================

    /**
     * Get source code via RFC proxy.
     * All ADT operations must go through RFC for SNC authentication.
     *
     * @param destination JCo destination (within active context)
     * @param session JCo session
     * @param path ADT path (e.g., "/sap/bc/adt/oo/classes/ZCL_TEST/source/main")
     * @param queryParams Query parameters (can be null)
     * @param acceptHeader Accept header value (can be null for default)
     * @return Source code as string
     * @throws Exception if request fails
     */
    public String getSourceCodeViaRfc(JCoDestination destination, com.sapjco.mcp.model.JcoSession session,
                                      String path, Map<String, String> queryParams, String acceptHeader) throws Exception {
        log.info("📡 getSourceCodeViaRfc: {}", path);

        Map<String, String> headers = RfcProxyExecutor.buildStandardHeaders(session, sapClient);
        headers.put(HttpHeaderBuilder.HEADER_ACCEPT, acceptHeader != null ? acceptHeader : "text/plain");

        com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                destination, "GET", path, queryParams, headers, null, session);

        if (response.getStatusCode() >= 400) {
            // Include error message if available (from HttpProxyResponse.error())
            String errorDetail = response.getError() != null ? response.getError() : response.getBody();
            throw new IOException(AdtResponseHandler.formatErrorMessage(response.getStatusCode(), errorDetail, "getSourceCodeViaRfc"));
        }

        return response.getBody();
    }

    /**
     * Perform a GET request via RFC proxy.
     * All ADT operations must go through RFC for SNC authentication.
     *
     * @param destination JCo destination
     * @param session JCo session
     * @param path ADT path
     * @param queryParams Query parameters (can be null)
     * @param acceptHeader Accept header value (can be null for default)
     * @return Response body as string
     * @throws Exception if request fails
     */
    public String statelessGetViaRfc(JCoDestination destination, com.sapjco.mcp.model.JcoSession session,
                                     String path, Map<String, String> queryParams, String acceptHeader) throws Exception {
        log.info("📡 statelessGetViaRfc: {}", path);

        Map<String, String> headers = RfcProxyExecutor.buildStandardHeaders(session, sapClient);
        if (acceptHeader != null) {
            headers.put(HttpHeaderBuilder.HEADER_ACCEPT, acceptHeader);
        }

        com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                destination, "GET", path, queryParams, headers, null, session);

        if (response.getStatusCode() >= 400) {
            // Include error message if available (from HttpProxyResponse.error())
            String errorDetail = response.getError() != null ? response.getError() : response.getBody();
            throw new IOException(AdtResponseHandler.formatErrorMessage(response.getStatusCode(), errorDetail, "statelessGetViaRfc"));
        }

        return response.getBody();
    }

    /**
     * Perform a POST request via RFC proxy.
     * All ADT operations must go through RFC for SNC authentication.
     *
     * @param destination JCo destination
     * @param session JCo session
     * @param path ADT path
     * @param queryParams Query parameters (can be null)
     * @param body Request body
     * @param contentType Content-Type header
     * @param acceptHeader Accept header value (can be null for default)
     * @return Full HTTP response including headers (for Location header extraction, etc.)
     * @throws Exception if request fails
     */
    public com.sapjco.mcp.model.HttpProxyResponse statelessPostViaRfc(JCoDestination destination, com.sapjco.mcp.model.JcoSession session,
                                      String path, Map<String, String> queryParams, String body,
                                      String contentType, String acceptHeader) throws Exception {
        log.info("📡 statelessPostViaRfc: {}", path);

        Map<String, String> headers = RfcProxyExecutor.buildStandardHeaders(session, sapClient);
        if (contentType != null) {
            headers.put(HttpHeaderBuilder.HEADER_CONTENT_TYPE, contentType);
        }
        if (acceptHeader != null) {
            headers.put(HttpHeaderBuilder.HEADER_ACCEPT, acceptHeader);
        }

        com.sapjco.mcp.model.HttpProxyResponse response = executeHttpRequestViaRfc(
                destination, "POST", path, queryParams, headers, body, session);

        if (response.getStatusCode() >= 400) {
            // Include error message if available (from HttpProxyResponse.error())
            String errorDetail = response.getError() != null ? response.getError() : response.getBody();
            throw new IOException(AdtResponseHandler.formatErrorMessage(response.getStatusCode(), errorDetail, "statelessPostViaRfc"));
        }

        return response;
    }

    /**
     * Get XML content via RFC proxy.
     *
     * @param destination JCo destination
     * @param session JCo session
     * @param path ADT path
     * @param queryParams Query parameters (can be null)
     * @return XML response body as string
     * @throws Exception if request fails
     */
    public String getXmlViaRfc(JCoDestination destination, com.sapjco.mcp.model.JcoSession session,
                               String path, Map<String, String> queryParams) throws Exception {
        return statelessGetViaRfc(destination, session, path, queryParams,
                "application/xml, application/atom+xml, text/xml");
    }

    /**
     * POST text content via RFC proxy with custom content/accept types.
     *
     * @param destination JCo destination
     * @param session     JCo session
     * @param path        ADT endpoint path
     * @param queryParams Query parameters
     * @param textBody    Text request body
     * @param contentType Content-Type header
     * @param acceptType  Accept header
     * @return Response body as string
     * @throws Exception if request fails
     */
    public String postTextViaRfc(JCoDestination destination, com.sapjco.mcp.model.JcoSession session,
                                 String path, Map<String, String> queryParams, String textBody,
                                 String contentType, String acceptType) throws Exception {
        return statelessPostViaRfc(destination, session, path, queryParams, textBody, contentType, acceptType).getBody();
    }
}
