package com.projectpilot.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.data.db.TeamService;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.lan.LanClient;
import com.projectpilot.lan.dto.*;
import com.projectpilot.model.Member;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.util.SslUtil;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LanAdminClient implements AdminService {

    private final LanClient client;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public LanAdminClient(LanClient client) {
        this.client = client;
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

    @Override
    public List<UserAdminService.UserRow> listLoginUsers() {
        HttpRequest req = request("/api/admin/users").GET().build();
        String body = send(req);
        try {
            return mapper.readValue(body, new TypeReference<List<UserAdminService.UserRow>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load users");
        }
    }

    @Override
    public void createUserWithEmailAndUsername(String displayName, String username, String email, String password, GlobalRole role) {
        AdminCreateUserRequest payload = new AdminCreateUserRequest(displayName, username, email, password, role);
        postJson("/api/admin/users", payload);
    }

    @Override
    public void updateUser(String userId, String displayName, String username, String email, String newPassword, GlobalRole role, boolean active) {
        AdminUpdateUserRequest payload = new AdminUpdateUserRequest(userId, displayName, username, email, newPassword, role, active);
        postJson("/api/admin/users/update", payload);
    }

    @Override
    public void deleteUser(String userId) {
        postJson("/api/admin/users/delete", new AdminDeleteRequest(userId));
    }

    @Override
    public void setUserActive(String userId, boolean active) {
        postJson("/api/admin/users/active", new AdminActiveRequest(userId, active));
    }

    @Override
    public Map<String, ProjectRole> rolesForUser(String userId) {
        String id = userId == null ? "" : userId.trim();
        String path = "/api/admin/roles?userId=" + urlEncode(id);
        HttpRequest req = request(path).GET().build();
        String body = send(req);
        try {
            return mapper.readValue(body, new TypeReference<Map<String, ProjectRole>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load roles");
        }
    }

    @Override
    public void upsertProjectRole(String projectId, String userId, ProjectRole role) {
        AdminRoleUpdateRequest payload = new AdminRoleUpdateRequest(projectId, userId, role);
        postJson("/api/admin/roles", payload);
    }

    @Override
    public List<Member> listDirectoryUsers() {
        HttpRequest req = request("/api/admin/directory").GET().build();
        String body = send(req);
        try {
            List<DirectoryUserDto> rows = mapper.readValue(body, new TypeReference<List<DirectoryUserDto>>() {});
            List<Member> out = new ArrayList<>();
            for (DirectoryUserDto row : rows) {
                if (row == null) continue;
                ProjectRole role = row.role() == null ? ProjectRole.MEMBER : row.role();
                out.add(new Member(row.id(), row.name(), role));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load directory");
        }
    }

    @Override
    public void createTeam(String name, String leaderId, List<TeamService.TeamMemberSpec> members) {
        postJson("/api/admin/teams", new AdminCreateTeamRequest(name, leaderId, members));
    }

    private HttpRequest.Builder request(String path) {
        String token = client.token();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Not logged in");
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(client.baseUrl() + path))
                .timeout(Duration.ofSeconds(5))
                .header("X-PP-Token", token);
    }

    private void postJson(String path, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            HttpRequest req = request(path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            send(req);
        } catch (Exception e) {
            throw new IllegalStateException("Request failed");
        }
    }

    private String send(HttpRequest req) {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String msg = resp.body() == null || resp.body().isBlank()
                        ? "Request failed (HTTP " + resp.statusCode() + ")"
                        : resp.body();
                throw new IllegalStateException(msg);
            }
            return resp.body() == null ? "" : resp.body();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new IllegalStateException("Request failed");
        }
    }

    private static String urlEncode(String v) {
        if (v == null) return "";
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
