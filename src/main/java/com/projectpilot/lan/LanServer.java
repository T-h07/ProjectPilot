package com.projectpilot.lan;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.auth.AuthException;
import com.projectpilot.data.db.auth.AuthService;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.lan.dto.*;
import com.projectpilot.model.*;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;
import javafx.application.Platform;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class LanServer {

    private final InMemoryStore store;
    private final AuthService auth;
    private final HttpServer server;
    private final ObjectMapper mapper;
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public LanServer(InMemoryStore store, AuthService auth, int port) {
        this.store = Objects.requireNonNull(store);
        this.auth = Objects.requireNonNull(auth);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start LAN server", e);
        }

        server.createContext("/api/health", this::handleHealth);
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/snapshot", this::handleSnapshot);
        server.createContext("/api/sync", this::handleSync);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "pp-lan-http");
            t.setDaemon(true);
            return t;
        }));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method Not Allowed");
            return;
        }
        sendText(ex, 200, "ok");
    }

    private void handleLogin(HttpExchange ex) throws IOException {
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
            sendJson(ex, 200, SyncResponse.ok());
        } catch (Exception e) {
            sendJson(ex, 500, SyncResponse.error("Sync failed"));
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

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] data = mapper.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
