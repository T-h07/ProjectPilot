package com.projectpilot.lan;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.data.db.auth.AuthException;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.lan.dto.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class LanClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private volatile String token;

    public LanClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("LAN host URL is required");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public UserSession login(String username, String password) {
        try {
            LoginRequest req = new LoginRequest(username, password);
            String json = mapper.writeValueAsString(req);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/login"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String msg = resp.body() == null || resp.body().isBlank() ? "Login failed." : resp.body();
                throw new AuthException(msg);
            }

            LoginResponse login = mapper.readValue(resp.body(), LoginResponse.class);
            this.token = login.token();
            return login.session();
        } catch (AuthException ae) {
            throw ae;
        } catch (Exception e) {
            throw new AuthException("Login failed.");
        }
    }

    public SnapshotDto fetchSnapshot() {
        ensureToken();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/snapshot"))
                    .timeout(Duration.ofSeconds(5))
                    .header("X-PP-Token", token)
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Snapshot failed");
            }
            return mapper.readValue(resp.body(), SnapshotDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot failed", e);
        }
    }

    public void sendAction(SyncAction action) {
        ensureToken();
        if (action == null) return;
        try {
            String json = mapper.writeValueAsString(action);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/sync"))
                    .timeout(Duration.ofSeconds(5))
                    .header("X-PP-Token", token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        System.err.println("[LAN] Failed to sync action: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("[LAN] Failed to sync action: " + e.getMessage());
        }
    }

    private void ensureToken() {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Not logged in");
        }
    }
}
