package com.projectpilot.lan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.data.db.TeamService;
import com.projectpilot.data.db.auth.AuthException;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.lan.dto.ServerStatusDto;
import com.projectpilot.lan.dto.*;
import com.projectpilot.util.SslUtil;

import java.net.URI;
import com.projectpilot.util.AppLog;
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
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3));
        var ssl = SslUtil.trustAllContextIfEnabled();
        if (ssl != null) builder.sslContext(ssl);
        this.http = builder.build();
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

    public String token() {
        return token;
    }

    public String baseUrl() {
        return baseUrl;
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

    public ServerStatusDto fetchStatus() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/status"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Status failed");
            }
            return mapper.readValue(resp.body(), ServerStatusDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Status failed", e);
        }
    }

    public java.util.List<DirectoryUserDto> fetchDirectoryUsers() {
        ensureToken();
        try {
            HttpRequest request = authed("/api/directory")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Directory failed");
            }
            return mapper.readValue(resp.body(), new TypeReference<java.util.List<DirectoryUserDto>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Directory failed", e);
        }
    }

    public java.util.List<TeamService.TeamRow> fetchTeams() {
        ensureToken();
        try {
            HttpRequest request = authed("/api/teams")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Teams failed");
            }
            return mapper.readValue(resp.body(), new TypeReference<java.util.List<TeamService.TeamRow>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Teams failed", e);
        }
    }

    public java.util.List<TeamService.TeamMemberRow> fetchTeamMembers(String teamId) {
        ensureToken();
        try {
            String q = encodeQuery(teamId);
            HttpRequest request = authed("/api/teams/members?teamId=" + q)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Team members failed");
            }
            return mapper.readValue(resp.body(), new TypeReference<java.util.List<TeamService.TeamMemberRow>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Team members failed", e);
        }
    }

    public void assignTeam(String teamId, String projectId) {
        ensureToken();
        try {
            TeamAssignRequest req = new TeamAssignRequest(teamId, projectId);
            String json = mapper.writeValueAsString(req);
            HttpRequest request = authed("/api/teams/assign")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException(resp.body() == null ? "Assign team failed" : resp.body());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Assign team failed", e);
        }
    }

    public java.util.List<String> fetchTeamNamesForMemberInProject(String memberId, String projectId) {
        ensureToken();
        try {
            String m = encodeQuery(memberId);
            String p = encodeQuery(projectId);
            HttpRequest request = authed("/api/teams/memberNames?memberId=" + m + "&projectId=" + p)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Team names failed");
            }
            return mapper.readValue(resp.body(), new TypeReference<java.util.List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Team names failed", e);
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
                        AppLog.warn("lan-client", "Failed to sync action: " + (ex == null ? "unknown" : ex.getMessage()));
                        return null;
                    });
        } catch (Exception e) {
            AppLog.warn("lan-client", "Failed to sync action: " + (e == null ? "unknown" : e.getMessage()));
        }
    }

    private void ensureToken() {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Not logged in");
        }
    }

    private HttpRequest.Builder authed(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("X-PP-Token", token);
    }

    private static String encodeQuery(String v) {
        if (v == null) return "";
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }
}
