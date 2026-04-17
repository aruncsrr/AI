package com.sapjco.mcp.service;

import com.sapjco.mcp.config.WikiConfigLoader;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the SAP Wiki (Confluence) REST API.
 * Uses OkHttp3 with Bearer token authentication.
 *
 * <p>All requests include:
 * <ul>
 *   <li>{@code Authorization: Bearer {token}}</li>
 *   <li>{@code Accept: application/json}</li>
 *   <li>{@code Content-Type: application/json} (for POST/PUT)</li>
 * </ul>
 */
@Slf4j
@Service
public class WikiApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final WikiConfigLoader config;
    private OkHttpClient httpClient;

    public WikiApiClient(WikiConfigLoader config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * HTTP GET with optional query parameters.
     */
    public String get(String path, Map<String, String> queryParams) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(config.getApiUrl() + path).newBuilder();
        if (queryParams != null) {
            queryParams.forEach(urlBuilder::addQueryParameter);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .addHeader("Authorization", "Bearer " + config.getApiToken())
                .addHeader("Accept", "application/json")
                .build();

        return execute(request);
    }

    /**
     * HTTP POST with JSON body.
     */
    public String post(String path, String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(config.getApiUrl() + path)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + config.getApiToken())
                .addHeader("Accept", "application/json")
                .build();

        return execute(request);
    }

    /**
     * HTTP PUT with JSON body.
     */
    public String put(String path, String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(config.getApiUrl() + path)
                .put(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + config.getApiToken())
                .addHeader("Accept", "application/json")
                .build();

        return execute(request);
    }

    /**
     * HTTP DELETE.
     */
    public void delete(String path) throws IOException {
        Request request = new Request.Builder()
                .url(config.getApiUrl() + path)
                .delete()
                .addHeader("Authorization", "Bearer " + config.getApiToken())
                .addHeader("Accept", "application/json")
                .build();

        execute(request);
    }

    private String execute(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(String.format("Wiki API error %d: %s", response.code(), body));
            }
            return body;
        }
    }
}
