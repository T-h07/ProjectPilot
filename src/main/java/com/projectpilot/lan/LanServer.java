package com.projectpilot.lan;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.chat.ChatService;
import com.projectpilot.chat.ChatThread;
import com.projectpilot.chat.ChatMessage;
import com.projectpilot.chat.ChatUser;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.TeamService;
import com.projectpilot.data.db.auth.AuthException;
import com.projectpilot.data.db.auth.AuthService;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.lan.dto.ServerStatusDto;
import com.projectpilot.lan.dto.*;
import com.projectpilot.model.*;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.ResourceType;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.util.ChecklistCodec;
import javafx.application.Platform;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class LanServer {

    private final InMemoryStore store;
    private final DbStore dbStore;
    private final UserAdminService userAdmin;
    private final AuthService auth;
    private final ChatService chatService;
    private final HttpServer server;
    private final HttpsServer httpsServer;
    private final ObjectMapper mapper;
    private final LanSessionRegistry sessions;
    private final LanWsServer wsServer;
    private final RateLimiter limiter;
    private final LanServerSettings settings;
    private final String mode;
    private final String publicUrl;
    private final int httpPort;
    private final int wsPort;
    private volatile long startedAt;

    public LanServer(InMemoryStore store, AuthService auth, ChatService chatService, LanSessionRegistry sessions, LanWsServer wsServer, int port) {
        this(store, auth, chatService, sessions, wsServer, null,
                LanServerSettings.forLan(port, wsServer == null ? 0 : wsServer.port()));
    }

    public LanServer(InMemoryStore store, AuthService auth, ChatService chatService, LanSessionRegistry sessions,
                     LanWsServer wsServer, RateLimiter limiter, int port) {
        this(store, auth, chatService, sessions, wsServer, limiter,
                LanServerSettings.forLan(port, wsServer == null ? 0 : wsServer.port()));
    }

    public LanServer(InMemoryStore store, AuthService auth, ChatService chatService, LanSessionRegistry sessions,
                     LanWsServer wsServer, RateLimiter limiter, LanServerSettings settings) {
        this.store = Objects.requireNonNull(store);
        DbStore ds = store instanceof DbStore dbs ? dbs : null;
        this.dbStore = ds;
        this.userAdmin = ds == null ? null : new UserAdminService(ds.manager());
        this.auth = Objects.requireNonNull(auth);
        this.chatService = Objects.requireNonNull(chatService);
        this.sessions = Objects.requireNonNull(sessions);
        this.wsServer = wsServer;
        this.limiter = limiter;
        this.settings = settings == null ? LanServerSettings.forLan(8090, wsServer == null ? 0 : wsServer.port()) : settings;
        this.mode = safe(this.settings.mode(), "lan");
        this.publicUrl = safe(this.settings.publicUrl(), "");
        this.httpPort = Math.max(0, this.settings.httpPort());
        this.wsPort = Math.max(0, this.settings.wsPort());
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        try {
            this.server = HttpServer.create(new InetSocketAddress(this.httpPort), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start LAN server", e);
        }

        HttpsServer https = null;
        if (this.settings.httpsPort() > 0 && this.settings.sslContext() != null) {
            try {
                https = HttpsServer.create(new InetSocketAddress(this.settings.httpsPort()), 0);
                https.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(this.settings.sslContext()));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start HTTPS server", e);
            }
        }
        this.httpsServer = https;

        configureContexts(server);
        if (httpsServer != null) {
            configureContexts(httpsServer);
        }
    }

    public void start() {
        startedAt = System.currentTimeMillis();
        server.start();
        if (httpsServer != null) httpsServer.start();
    }

    public void stop() {
        server.stop(1);
        if (httpsServer != null) httpsServer.stop(1);
        startedAt = 0L;
    }

    private void configureContexts(HttpServer target) {
        target.createContext("/api/health", this::handleHealth);
        target.createContext("/api/status", this::handleStatus);
        target.createContext("/api/auth/login", this::handleLogin);
        target.createContext("/api/snapshot", this::handleSnapshot);
        target.createContext("/api/sync", this::handleSync);
        target.createContext("/api/chat", this::handleChat);
        target.createContext("/api/directory", this::handleDirectory);
        target.createContext("/api/admin", this::handleAdmin);
        target.createContext("/api/teams", this::handleTeams);
        target.createContext("/meet", this::handleMeet);
        target.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "pp-lan-http");
            t.setDaemon(true);
            return t;
        }));
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        if (!allowRequest(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method Not Allowed");
            return;
        }
        sendText(ex, 200, "ok");
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (!allowRequest(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method Not Allowed");
            return;
        }
        long started = startedAt;
        long uptime = started <= 0 ? 0L : Math.max(0L, System.currentTimeMillis() - started);
        int connections = wsServer == null ? 0 : wsServer.connectedCount();
        ServerStatusDto status = ServerStatusDto.ok(started, uptime, publicUrl, httpPort, wsPort, connections, mode);
        sendJson(ex, 200, status);
    }
    private void handleLogin(HttpExchange ex) throws IOException {
        if (!allowRequest(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method Not Allowed");
            return;
        }

        try {
            LoginRequest req = readJson(ex, LoginRequest.class);
            UserSession session = auth.login(req.username(), req.password());
            String token = UUID.randomUUID().toString();
            sessions.put(token, session);
            sendJson(ex, 200, new LoginResponse(token, session));
        } catch (AuthException ae) {
            sendText(ex, 401, ae.getMessage());
        } catch (Exception e) {
            sendText(ex, 500, "Login failed");
        }
    }

    private void handleSnapshot(HttpExchange ex) throws IOException {
        if (!allowRequest(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method Not Allowed");
            return;
        }

        UserSession session = requireSession(ex);
        if (session == null) {
            sendText(ex, 401, "Unauthorized");
            return;
        }

        try {
            SnapshotDto snapshot = callOnFx(() -> LanMapper.toSnapshot(store));
            sendJson(ex, 200, snapshot);
        } catch (Exception e) {
            sendText(ex, 500, "Snapshot failed");
        }
    }

    private void handleSync(HttpExchange ex) throws IOException {
        if (!allowRequest(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method Not Allowed");
            return;
        }

        UserSession session = requireSession(ex);
        if (session == null) {
            sendText(ex, 401, "Unauthorized");
            return;
        }

        try {
            SyncAction action = readJson(ex, SyncAction.class);
            callOnFx(() -> {
                applyAction(action);
                return null;
            });
            broadcastRefresh();
            sendJson(ex, 200, SyncResponse.success());
        } catch (Exception e) {
            sendJson(ex, 500, SyncResponse.error("Sync failed"));
        }
    }

    private void handleChat(HttpExchange ex) throws IOException {
        if (!allowRequest(ex)) return;
        UserSession session = requireSession(ex);
        if (session == null) {
            sendText(ex, 401, "Unauthorized");
            return;
        }

        String path = ex.getRequestURI() == null ? "" : ex.getRequestURI().getPath();
        String sub = path.startsWith("/api/chat") ? path.substring("/api/chat".length()) : path;
        if (sub.isEmpty()) sub = "/";

        try {
            if ("/threads".equals(sub) && "GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 200, chatService.listThreads(session.id()));
                return;
            }

            if ("/users".equals(sub) && "GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 200, chatService.listUsers(session.id()));
                return;
            }

            if ("/direct".equals(sub) && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ChatDirectRequest req = readJson(ex, ChatDirectRequest.class);
                ChatThread thread = chatService.getOrCreateDirect(session.id(), req == null ? null : req.otherId());
                sendJson(ex, 200, thread);
                return;
            }

            if ("/messages".equals(sub)) {
                if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    String threadId = getQueryParam(ex.getRequestURI(), "threadId");
                    int limit = getQueryParamInt(ex.getRequestURI(), "limit", 120);
                    if (threadId == null || threadId.isBlank()) {
                        sendText(ex, 400, "threadId is required");
                        return;
                    }
                    sendJson(ex, 200, chatService.listMessages(threadId, session.id(), limit));
                    return;
                }

                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    ChatSendRequest req = readJson(ex, ChatSendRequest.class);
                    if (req == null || req.threadId() == null || req.threadId().isBlank()) {
                        sendText(ex, 400, "threadId is required");
                        return;
                    }
                    ChatMessage msg = chatService.sendMessage(req.threadId(), session.id(), req.body());
                    sendJson(ex, 200, msg);
                    return;
                }
            }

            sendText(ex, 404, "Not Found");
        } catch (IllegalArgumentException iae) {
            sendText(ex, 400, iae.getMessage());
        } catch (Exception e) {
            sendText(ex, 500, "Chat failed");
        }
    }

    private void handleDirectory(HttpExchange ex) throws IOException {
        if (!allowRequest(ex)) return;
        UserSession session = requireSession(ex);
        if (session == null) {
            sendText(ex, 401, "Unauthorized");
            return;
        }
        if (dbStore == null) {
            sendText(ex, 501, "Directory unavailable");
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method Not Allowed");
            return;
        }

        try {
            List<Member> directory = dbStore.listDirectoryUsers();
            List<DirectoryUserDto> out = new ArrayList<>();
            for (Member m : directory) {
                if (m == null) continue;
                out.add(new DirectoryUserDto(m.getId(), m.getName(), m.getRole()));
            }
            sendJson(ex, 200, out);
        } catch (Exception e) {
            sendText(ex, 500, "Directory failed");
        }
    }

    private void handleAdmin(HttpExchange ex) throws IOException {
        if (!allowRequest(ex)) return;
        UserSession session = requireSession(ex);
        if (session == null) {
            sendText(ex, 401, "Unauthorized");
            return;
        }
        if (session.globalRole() != GlobalRole.ADMIN) {
            sendText(ex, 403, "Forbidden");
            return;
        }
        if (userAdmin == null || dbStore == null) {
            sendText(ex, 501, "Admin API unavailable");
            return;
        }

        String method = ex.getRequestMethod();
        String path = ex.getRequestURI() == null ? "" : ex.getRequestURI().getPath();
        String sub = path.startsWith("/api/admin") ? path.substring("/api/admin".length()) : path;
        if (sub.isEmpty()) sub = "/";

        try {
            if ("/users".equals(sub) && "GET".equalsIgnoreCase(method)) {
                sendJson(ex, 200, userAdmin.listLoginUsers());
                return;
            }

            if ("/users".equals(sub) && "POST".equalsIgnoreCase(method)) {
                AdminCreateUserRequest req = readJson(ex, AdminCreateUserRequest.class);
                if (req == null) {
                    sendText(ex, 400, "Invalid request");
                    return;
                }
                userAdmin.createUserWithEmailAndUsername(
                        req.displayName(),
                        req.username(),
                        req.email(),
                        req.password(),
                        req.globalRole()
                );
                sendText(ex, 200, "ok");
                return;
            }

            if ("/users/update".equals(sub) && "POST".equalsIgnoreCase(method)) {
                AdminUpdateUserRequest req = readJson(ex, AdminUpdateUserRequest.class);
                if (req == null || req.id() == null || req.id().isBlank()) {
                    sendText(ex, 400, "User id is required");
                    return;
                }
                userAdmin.updateUser(
                        req.id(),
                        req.displayName(),
                        req.username(),
                        req.email(),
                        req.newPassword(),
                        req.globalRole(),
                        req.active()
                );
                sendText(ex, 200, "ok");
                return;
            }

            if ("/users/active".equals(sub) && "POST".equalsIgnoreCase(method)) {
                AdminActiveRequest req = readJson(ex, AdminActiveRequest.class);
                if (req == null || req.id() == null || req.id().isBlank()) {
                    sendText(ex, 400, "User id is required");
                    return;
                }
                userAdmin.setUserActive(req.id(), req.active());
                sendText(ex, 200, "ok");
                return;
            }

            if ("/users/delete".equals(sub) && "POST".equalsIgnoreCase(method)) {
                AdminDeleteRequest req = readJson(ex, AdminDeleteRequest.class);
                if (req == null || req.id() == null || req.id().isBlank()) {
                    sendText(ex, 400, "User id is required");
                    return;
                }
                userAdmin.deleteUser(req.id());
                sendText(ex, 200, "ok");
                return;
            }

            if ("/roles".equals(sub) && "GET".equalsIgnoreCase(method)) {
                String userId = getQueryParam(ex.getRequestURI(), "userId");
                if (userId == null || userId.isBlank()) {
                    sendText(ex, 400, "userId is required");
                    return;
                }
                sendJson(ex, 200, userAdmin.rolesForUser(userId));
                return;
            }

            if ("/roles".equals(sub) && "POST".equalsIgnoreCase(method)) {
                AdminRoleUpdateRequest req = readJson(ex, AdminRoleUpdateRequest.class);
                if (req == null || req.userId() == null || req.userId().isBlank()) {
                    sendText(ex, 400, "User id is required");
                    return;
                }
                userAdmin.upsertProjectRole(req.projectId(), req.userId(), req.role());
                sendText(ex, 200, "ok");
                return;
            }

            if ("/directory".equals(sub) && "GET".equalsIgnoreCase(method)) {
                List<Member> directory = dbStore.listDirectoryUsers();
                List<DirectoryUserDto> out = new ArrayList<>();
                for (Member m : directory) {
                    if (m == null) continue;
                    out.add(new DirectoryUserDto(m.getId(), m.getName(), m.getRole()));
                }
                sendJson(ex, 200, out);
                return;
            }

            if ("/teams".equals(sub) && "POST".equalsIgnoreCase(method)) {
                AdminCreateTeamRequest req = readJson(ex, AdminCreateTeamRequest.class);
                if (req == null) {
                    sendText(ex, 400, "Invalid request");
                    return;
                }
                List<TeamService.TeamMemberSpec> members = req.members() == null ? List.of() : req.members();
                dbStore.createTeam(req.name(), req.leaderId(), members);
                sendText(ex, 200, "ok");
                return;
            }

            if ("/validate".equals(sub) && "GET".equalsIgnoreCase(method)) {
                try {
                    java.util.List<String> issues = dbStore.manager().tx(conn -> {
                        java.util.List<String> out = new java.util.ArrayList<>();
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT id, project_id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects)")) {
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) out.add("Task " + rs.getString("id") + " references missing project " + rs.getString("project_id"));
                            }
                        } catch (SQLException ignored) {}

                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT id, phase_id FROM tasks WHERE phase_id IS NOT NULL AND phase_id NOT IN (SELECT id FROM phases)")) {
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) out.add("Task " + rs.getString("id") + " references missing phase " + rs.getString("phase_id"));
                            }
                        } catch (SQLException ignored) {}

                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT id, assignee_member_id FROM tasks WHERE assignee_member_id IS NOT NULL AND assignee_member_id NOT IN (SELECT id FROM members)")) {
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) out.add("Task " + rs.getString("id") + " has invalid assignee " + rs.getString("assignee_member_id"));
                            }
                        } catch (SQLException ignored) {}

                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT project_id, member_id FROM project_members WHERE member_id NOT IN (SELECT id FROM members)")) {
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) out.add("Project membership references missing member: project=" + rs.getString("project_id") + " member=" + rs.getString("member_id"));
                            }
                        } catch (SQLException ignored) {}

                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT id, project_id FROM phases WHERE project_id NOT IN (SELECT id FROM projects)")) {
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) out.add("Phase " + rs.getString("id") + " references missing project " + rs.getString("project_id"));
                            }
                        } catch (SQLException ignored) {}

                        return out;
                    });
                    sendJson(ex, 200, issues);
                } catch (Exception e) {
                    sendText(ex, 500, "Validation failed");
                }
                return;
            }

            sendText(ex, 404, "Not Found");
        } catch (IllegalArgumentException iae) {
            sendText(ex, 400, iae.getMessage());
        } catch (Exception e) {
            sendText(ex, 500, "Admin failed");
        }
    }

    private void handleTeams(HttpExchange ex) throws IOException {
        if (!allowRequest(ex)) return;
        UserSession session = requireSession(ex);
        if (session == null) {
            sendText(ex, 401, "Unauthorized");
            return;
        }
        if (dbStore == null) {
            sendText(ex, 501, "Teams unavailable");
            return;
        }

        String method = ex.getRequestMethod();
        String path = ex.getRequestURI() == null ? "" : ex.getRequestURI().getPath();
        String sub = path.startsWith("/api/teams") ? path.substring("/api/teams".length()) : path;
        if (sub.isEmpty()) sub = "/";

        boolean isAdmin = session.globalRole() == GlobalRole.ADMIN;

        try {
            if ("/".equals(sub) && "GET".equalsIgnoreCase(method)) {
                if (!isAdmin) {
                    sendText(ex, 403, "Forbidden");
                    return;
                }
                sendJson(ex, 200, dbStore.listTeams());
                return;
            }

            if ("/members".equals(sub) && "GET".equalsIgnoreCase(method)) {
                if (!isAdmin) {
                    sendText(ex, 403, "Forbidden");
                    return;
                }
                String teamId = getQueryParam(ex.getRequestURI(), "teamId");
                if (teamId == null || teamId.isBlank()) {
                    sendText(ex, 400, "teamId is required");
                    return;
                }
                sendJson(ex, 200, dbStore.listTeamMembers(teamId));
                return;
            }

            if ("/assign".equals(sub) && "POST".equalsIgnoreCase(method)) {
                if (!isAdmin) {
                    sendText(ex, 403, "Forbidden");
                    return;
                }
                TeamAssignRequest req = readJson(ex, TeamAssignRequest.class);
                if (req == null || req.teamId() == null || req.teamId().isBlank()
                        || req.projectId() == null || req.projectId().isBlank()) {
                    sendText(ex, 400, "teamId and projectId are required");
                    return;
                }
                dbStore.assignTeamToProject(req.teamId(), req.projectId());
                sendText(ex, 200, "ok");
                return;
            }

            if ("/memberNames".equals(sub) && "GET".equalsIgnoreCase(method)) {
                String memberId = getQueryParam(ex.getRequestURI(), "memberId");
                String projectId = getQueryParam(ex.getRequestURI(), "projectId");
                if (memberId == null || memberId.isBlank() || projectId == null || projectId.isBlank()) {
                    sendText(ex, 400, "memberId and projectId are required");
                    return;
                }
                if (!isAdmin && !memberId.equals(session.id())) {
                    sendText(ex, 403, "Forbidden");
                    return;
                }
                sendJson(ex, 200, dbStore.listTeamNamesForMemberInProject(memberId, projectId));
                return;
            }

            sendText(ex, 404, "Not Found");
        } catch (IllegalArgumentException iae) {
            sendText(ex, 400, iae.getMessage());
        } catch (Exception e) {
            sendText(ex, 500, "Teams failed");
        }
    }

    private void handleMeet(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method Not Allowed");
            return;
        }

        if (!sendResource(ex, "/meetings/meeting.html", "text/html; charset=utf-8")) {
            sendText(ex, 404, "Not Found");
        }
    }

    private void applyAction(SyncAction action) {
        if (action == null || action.type() == null) return;

        switch (action.type()) {
            case PROJECT_UPSERT -> upsertProject(action.project());
            case PROJECT_DELETE -> deleteProject(action.projectId());
            case PROJECT_DONE -> markProjectDone(action.projectId());
            case PROJECT_RESTORE -> restoreProject(action.projectId());
            case TASK_UPSERT -> upsertTask(action.projectId(), action.task());
            case TASK_DELETE -> deleteTask(action.projectId(), action.entityId());
            case MEMBER_UPSERT -> upsertMember(action.projectId(), action.member());
            case MEMBER_REMOVE -> removeMember(action.projectId(), action.entityId());
            case PHASE_UPSERT -> upsertPhase(action.projectId(), action.phase());
            case PHASE_DELETE -> deletePhase(action.projectId(), action.entityId());
            case MILESTONE_UPSERT -> upsertMilestone(action.projectId(), action.milestone());
            case MILESTONE_DELETE -> deleteMilestone(action.projectId(), action.entityId());
            case RESOURCE_UPSERT -> upsertResource(action.projectId(), action.resource());
            case RESOURCE_DELETE -> deleteResource(action.projectId(), action.entityId());
            case NOTE_UPSERT -> upsertNote(action.projectId(), action.note());
            case NOTE_DELETE -> deleteNote(action.projectId(), action.entityId());
            default -> { }
        }
    }

    private void upsertProject(ProjectDto dto) {
        if (dto == null || dto.id() == null) return;

        Project p = findProject(dto.id());
        if (p == null) {
            p = new Project(dto.id(), safe(dto.name()));
            store.createProject(p);
        }

        p.setName(safe(dto.name()));
        p.setDescription(safe(dto.description()));
        p.setStakeholders(safe(dto.stakeholders()));
        p.setPhaseTemplate(safe(dto.phaseTemplate()));
        if (dto.health() != null) p.setHealth(dto.health());
        if (dto.startDate() != null) p.setStartDate(dto.startDate());
        if (dto.endDate() != null) p.setEndDate(dto.endDate());
        if (dto.completedDate() != null) p.setCompletedDate(dto.completedDate());
        if (dto.status() != null) p.setStatus(dto.status());

        ensureProjectList(p, dto.status());
    }

    private void deleteProject(String projectId) {
        Project p = findProject(projectId);
        if (p != null) store.deleteProject(p);
    }

    private void markProjectDone(String projectId) {
        Project p = findProject(projectId);
        if (p != null) store.markProjectDone(p);
    }

    private void restoreProject(String projectId) {
        Project p = findProject(projectId);
        if (p != null) store.restoreProject(p);
    }

    private void upsertTask(String projectId, TaskDto dto) {
        if (projectId == null || dto == null || dto.id() == null) return;
        Project p = findProject(projectId);
        if (p == null) return;

        Task t = findTask(p, dto.id());
        if (t == null) {
            t = new Task(dto.id(), safe(dto.title()));
            store.addTask(p, t);
        }

        t.setTitle(safe(dto.title()));
        t.setDescription(safe(dto.description()));
        t.setStatus(dto.status() == null ? TaskStatus.TODO : dto.status());
        t.setPriority(dto.priority() == null ? Priority.MEDIUM : dto.priority());
        t.setDueDate(dto.dueDate());
        t.setAssignee(findMember(p, dto.assigneeId()));
        t.setPhase(findPhase(p, dto.phaseId()));
        t.setChecklist(ChecklistCodec.decode(dto.checklistJson()));
    }

    private void deleteTask(String projectId, String taskId) {
        if (projectId == null || taskId == null) return;
        Project p = findProject(projectId);
        if (p == null) return;
        Task t = findTask(p, taskId);
        if (t != null) p.getTasks().remove(t);
    }

    private void upsertMember(String projectId, MemberDto dto) {
        if (projectId == null || dto == null || dto.id() == null) return;
        Project p = findProject(projectId);
        if (p == null) return;

        Member m = findMember(p, dto.id());
        if (m == null) {
            ProjectRole role = dto.role() == null ? ProjectRole.MEMBER : dto.role();
            m = new Member(dto.id(), safe(dto.name()), role);
            store.addMember(p, m);
        } else {
            m.setName(safe(dto.name()));
            if (dto.role() != null) m.setRole(dto.role());
        }
    }

    private void removeMember(String projectId, String memberId) {
        if (projectId == null || memberId == null) return;
        Project p = findProject(projectId);
        if (p == null) return;
        Member m = findMember(p, memberId);
        if (m != null) store.removeMember(p, m);
    }

    private void upsertPhase(String projectId, PhaseDto dto) {
        if (projectId == null || dto == null || dto.id() == null) return;
        Project p = findProject(projectId);
        if (p == null) return;

        Phase ph = findPhase(p, dto.id());
        if (ph == null) {
            ph = new Phase(dto.id(), safe(dto.name()));
            store.addPhase(p, ph);
        }

        ph.setName(safe(dto.name()));
        if (dto.start() != null) ph.startProperty().set(dto.start());
        if (dto.end() != null) ph.endProperty().set(dto.end());

        int idx = dto.sortIndex();
        if (idx >= 0 && idx < p.getPhases().size()) {
            p.getPhases().remove(ph);
            p.getPhases().add(idx, ph);
        }
    }

    private void deletePhase(String projectId, String phaseId) {
        if (projectId == null || phaseId == null) return;
        Project p = findProject(projectId);
        if (p == null) return;
        Phase ph = findPhase(p, phaseId);
        if (ph != null) p.getPhases().remove(ph);
    }

    private void upsertMilestone(String projectId, MilestoneDto dto) {
        if (projectId == null || dto == null || dto.id() == null) return;
        Project p = findProject(projectId);
        if (p == null) return;

        Milestone ms = findMilestone(p, dto.id());
        if (ms == null) {
            ms = new Milestone(dto.id(), safe(dto.title()));
            store.addMilestone(p, ms);
        }

        ms.nameProperty().set(safe(dto.title()));
        ms.dueDateProperty().set(dto.dueDate());
        ms.completedProperty().set(dto.done());
    }

    private void deleteMilestone(String projectId, String milestoneId) {
        if (projectId == null || milestoneId == null) return;
        Project p = findProject(projectId);
        if (p == null) return;
        Milestone ms = findMilestone(p, milestoneId);
        if (ms != null) p.getMilestones().remove(ms);
    }

    private void upsertResource(String projectId, ResourceDto dto) {
        if (projectId == null || dto == null || dto.id() == null) return;
        Project p = findProject(projectId);
        if (p == null) return;

        ResourceItem r = findResource(p, dto.id());
        if (r == null) {
            r = new ResourceItem(dto.id(), projectId);
            store.addResource(p, r);
        }

        r.setTaskId(dto.taskId());
        r.setType(dto.type() == null ? ResourceType.LINK : dto.type());
        r.setTitle(safe(dto.title()));
        r.setTarget(safe(dto.target()));
        r.setNotes(safe(dto.notes()));
        r.setAddedBy(safe(dto.addedBy()));
        if (dto.createdAt() != null) r.setCreatedAt(dto.createdAt());
        if (dto.updatedAt() != null) r.setUpdatedAt(dto.updatedAt());
    }

    private void deleteResource(String projectId, String resourceId) {
        if (projectId == null || resourceId == null) return;
        Project p = findProject(projectId);
        if (p == null) return;
        ResourceItem r = findResource(p, resourceId);
        if (r != null) p.getResources().remove(r);
    }

    private void upsertNote(String projectId, NoteDto dto) {
        if (projectId == null || dto == null || dto.id() == null) return;
        Project p = findProject(projectId);
        if (p == null) return;

        PersonalNote note = findNote(p, dto.id());
        if (note == null) {
            note = new PersonalNote(dto.id(), projectId, safe(dto.ownerId()));
            store.addNote(p, note);
        }

        note.setTaskId(dto.taskId());
        note.setOwnerId(safe(dto.ownerId()));
        note.setTitle(safe(dto.title()));
        note.setBody(safe(dto.body()));
        if (dto.createdAt() != null) note.setCreatedAt(dto.createdAt());
        if (dto.updatedAt() != null) note.setUpdatedAt(dto.updatedAt());
    }

    private void deleteNote(String projectId, String noteId) {
        if (projectId == null || noteId == null) return;
        Project p = findProject(projectId);
        if (p == null) return;
        PersonalNote note = findNote(p, noteId);
        if (note != null) p.getNotes().remove(note);
    }

    private Project findProject(String id) {
        if (id == null) return null;
        for (Project p : store.getProjects()) if (id.equals(p.getId())) return p;
        for (Project p : store.getHistoryProjects()) if (id.equals(p.getId())) return p;
        return null;
    }

    private Task findTask(Project p, String id) {
        if (p == null || id == null) return null;
        for (Task t : p.getTasks()) if (id.equals(t.getId())) return t;
        return null;
    }

    private Member findMember(Project p, String id) {
        if (p == null || id == null) return null;
        for (Member m : p.getMembers()) if (id.equals(m.getId())) return m;
        return null;
    }

    private Phase findPhase(Project p, String id) {
        if (p == null || id == null) return null;
        for (Phase ph : p.getPhases()) if (id.equals(ph.getId())) return ph;
        return null;
    }

    private Milestone findMilestone(Project p, String id) {
        if (p == null || id == null) return null;
        for (Milestone ms : p.getMilestones()) if (id.equals(ms.getId())) return ms;
        return null;
    }

    private ResourceItem findResource(Project p, String id) {
        if (p == null || id == null) return null;
        for (ResourceItem r : p.getResources()) if (id.equals(r.getId())) return r;
        return null;
    }

    private PersonalNote findNote(Project p, String id) {
        if (p == null || id == null) return null;
        for (PersonalNote note : p.getNotes()) if (id.equals(note.getId())) return note;
        return null;
    }

    private void ensureProjectList(Project p, Project.ProjectStatus status) {
        if (p == null || status == null) return;
        if (status == Project.ProjectStatus.DONE) {
            store.getProjects().remove(p);
            if (!store.getHistoryProjects().contains(p)) store.getHistoryProjects().add(p);
        } else {
            store.getHistoryProjects().remove(p);
            if (!store.getProjects().contains(p)) store.getProjects().add(p);
        }
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static String safe(String v, String fallback) {
        String s = safe(v);
        return s.isBlank() ? fallback : s;
    }

    private <T> T callOnFx(Callable<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) return task.call();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                out.set(task.call());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new TimeoutException("FX sync timed out");
        }
        if (err.get() != null) {
            if (err.get() instanceof Exception ex) throw ex;
            throw new RuntimeException(err.get());
        }
        return out.get();
    }

    private UserSession requireSession(HttpExchange ex) {
        String token = ex.getRequestHeaders().getFirst("X-PP-Token");
        if ((token == null || token.isBlank())) {
            token = getQueryParam(ex.getRequestURI(), "token");
        }
        if (token == null || token.isBlank()) return null;
        return sessions.get(token.trim());
    }

    private void broadcastRefresh() {
        if (wsServer != null) wsServer.broadcastRefresh();
    }

    private String getQueryParam(URI uri, String key) {
        if (uri == null || key == null) return null;
        String q = uri.getRawQuery();
        if (q == null || q.isBlank()) return null;
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                return decode(kv[1]);
            }
        }
        return null;
    }

    private int getQueryParamInt(URI uri, String key, int fallback) {
        String v = getQueryParam(uri, key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String decode(String v) {
        if (v == null) return null;
        return v.replace("%3A", ":").replace("%2F", "/");
    }

    private <T> T readJson(HttpExchange ex, Class<T> type) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return mapper.readValue(in, type);
        }
    }

    private void sendText(HttpExchange ex, int status, String body) throws IOException {
        byte[] data = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private boolean sendResource(HttpExchange ex, String path, String contentType) throws IOException {
        try (InputStream in = LanServer.class.getResourceAsStream(path)) {
            if (in == null) return false;
            byte[] data = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(data);
            }
            return true;
        }
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] data = mapper.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private boolean allowRequest(HttpExchange ex) throws IOException {
        if (limiter == null) return true;
        String key = clientKey(ex);
        if (limiter.allow(key)) return true;
        sendText(ex, 429, "Too Many Requests");
        return false;
    }

    private String clientKey(HttpExchange ex) {
        if (ex == null) return "unknown";
        String forwarded = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = ex.getRequestHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        if (ex.getRemoteAddress() != null && ex.getRemoteAddress().getAddress() != null) {
            return ex.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
