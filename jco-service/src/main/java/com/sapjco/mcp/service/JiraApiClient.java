package com.sapjco.mcp.service;

import com.sapjco.mcp.config.JiraConfigLoader;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the Jira Server/Data Center REST API v2.
 * Uses Bearer token authentication (Jira personal access tokens).
 */
@Slf4j
@Service
public class JiraApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final JiraConfigLoader config;
    private OkHttpClient httpClient;

    public JiraApiClient(JiraConfigLoader config) {
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

    public String post(String path, String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(config.getApiUrl() + path)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + config.getApiToken())
                .addHeader("Accept", "application/json")
                .build();
        return execute(request);
    }

    public String put(String path, String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(config.getApiUrl() + path)
                .put(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + config.getApiToken())
                .addHeader("Accept", "application/json")
                .build();
        return execute(request);
    }

    private String execute(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(String.format("Jira API error %d: %s", response.code(), body));
            }
            return body;
        }
    }
}
