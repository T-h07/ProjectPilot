package com.projectpilot.service;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.repo.NotificationDao;
import com.projectpilot.model.Notification;
import com.projectpilot.model.NotificationItem;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.NotificationType;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.security.AccessPolicy;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

public final class NotificationService {

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();
    private final Preferences prefs = Preferences.userRoot().node("projectpilot/notifications");

    private final ObservableList<NotificationItem> items = FXCollections.observableArrayList();
    private LocalDateTime lastLoginShownAt = null;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pp-notifications");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean rebuildPending = new AtomicBoolean(false);

    public NotificationService(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        // refresh when user changes
        appState.sessionProperty().addListener((obs, o, n) -> requestRebuild());

        // refresh when project selection changes (helps keep it feeling “live”)
        appState.selectedProjectProperty().addListener((obs, o, n) -> requestRebuild());

        // If store is observable, rebuild when projects list changes
        try {
            store.getProjects().addListener((javafx.collections.ListChangeListener<Project>) c -> requestRebuild());
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("notifications", "Failed to add projects listener: " + (e == null ? "" : e.getMessage())); }

        rebuildNow();
    }

    public ObservableList<NotificationItem> items() { return items; }

    public void rebuildNow() {
        List<NotificationItem> built = buildForCurrentUserPreserveRead();
        if (Platform.isFxApplicationThread()) items.setAll(built);
        else Platform.runLater(() -> items.setAll(built));
    }

    private void requestRebuild() {
        if (!rebuildPending.compareAndSet(false, true)) return;
        exec.schedule(() -> {
            rebuildPending.set(false);
            rebuildNow();
        }, 150, TimeUnit.MILLISECONDS);
    }

    public int unreadCount() {
        int c = 0;
        for (NotificationItem it : items) if (!it.read()) c++;
        return c;
    }

    public void markAllRead() {
        List<NotificationItem> next = new ArrayList<>(items.size());
        for (NotificationItem it : items) next.add(it.withRead(true));
        items.setAll(next);
        persistMarkAllRead();
    }

    public void markRead(String key) {
        if (key == null) return;
        List<NotificationItem> next = new ArrayList<>(items.size());
        for (NotificationItem it : items) {
            next.add(Objects.equals(key, it.key()) ? it.withRead(true) : it);
        }
        items.setAll(next);
        persistMarkRead(key);
    }

    public void markLoginShown() {
        lastLoginShownAt = LocalDateTime.now();
    }

    public List<NotificationItem> loginHighlights() {
        if (items.isEmpty()) return List.of();

        LocalDateTime since = (lastLoginShownAt != null)
                ? lastLoginShownAt
                : LocalDateTime.now().minusDays(1);

        List<NotificationItem> out = new ArrayList<>();
        for (NotificationItem it : items) {
            if (it.urgent() || it.at().isAfter(since)) out.add(it);
        }

        out.sort(Comparator.comparing(NotificationItem::urgent).reversed()
                .thenComparing(NotificationItem::at).reversed());
        return out;
    }

    // ---------------- internals ----------------

    private List<NotificationItem> buildForCurrentUserPreserveRead() {
        Map<String, Boolean> wasRead = new HashMap<>();
        for (NotificationItem it : items) wasRead.put(it.key(), it.read());

        List<NotificationItem> built = buildForCurrentUser();
        String userId = currentUserId();
        Map<String, Boolean> persisted = loadPersistentReadState(userId, built);

        // preserve read state by key
        List<NotificationItem> out = new ArrayList<>(built.size());
        for (NotificationItem it : built) {
            boolean r = wasRead.getOrDefault(it.key(), false);
            if (userId != null) {
                String id = notificationId(userId, it.key());
                if (id != null) r = persisted.getOrDefault(id, r);
            }
            out.add(r == it.read() ? it : it.withRead(r));
        }
        return out;
    }

    private List<NotificationItem> buildForCurrentUser() {
        if (appState.getSession() == null) return List.of();

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<NotificationItem> out = new ArrayList<>();

        for (Project p : store.getProjects()) {
            if (p == null) continue;
            if (!policy.canViewProject(appState, p)) continue;

            for (Task t : safeTasks(p)) {
                if (t == null) continue;
                if (!policy.isAssignedToMe(appState, t)) continue;

                TaskStatus st = t.getStatus();
                if (st == TaskStatus.DONE) continue;

                String taskTitle = safe(t.getTitle(), "Task");
                String projectName = safe(p.getName(), "Project");

                LocalDate due = t.getDueDate();

                if (due != null && due.isBefore(today)) {
                    out.add(new NotificationItem(
                            "overdue:" + p.getId() + ":" + t.getId(),
                            now,
                            "Overdue task",
                            taskTitle + "  •  " + projectName + "  •  Due " + due,
                            true,
                            false
                    ));
                } else if (due != null && due.equals(today)) {
                    out.add(new NotificationItem(
                            "due-today:" + p.getId() + ":" + t.getId(),
                            now,
                            "Task due today",
                            taskTitle + "  •  " + projectName,
                            true,
                            false
                    ));
                } else {
                    out.add(new NotificationItem(
                            "assigned:" + p.getId() + ":" + t.getId(),
                            now,
                            "Assigned task",
                            taskTitle + "  •  " + projectName,
                            false,
                            false
                    ));
                }
            }
        }

        out.sort(Comparator.comparing(NotificationItem::urgent).reversed()
                .thenComparing(NotificationItem::at).reversed());

        if (out.size() > 30) return out.subList(0, 30);
        return out;
    }

    private static List<Task> safeTasks(Project p) {
        try {
            return new ArrayList<>(p.getTasks());
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String safe(String v, String fallback) {
        if (v == null) return fallback;
        String s = v.trim();
        return s.isEmpty() ? fallback : s;
    }

    private String currentUserId() {
        if (appState == null || appState.getSession() == null) return null;
        String id = appState.getSession().id();
        if (id == null) return null;
        String v = id.trim();
        return v.isEmpty() ? null : v;
    }

    private DbManager dbIfAvailable() {
        if (store instanceof DbStore ds) return ds.manager();
        return null;
    }

    private Map<String, Boolean> loadPersistentReadState(String userId, List<NotificationItem> items) {
        DbManager db = dbIfAvailable();
        if (userId == null || userId.isBlank()) return Map.of();
        if (db == null) {
            return loadLocalReadState(userId, items);
        }

        try {
            return db.tx(conn -> {
                try {
                    NotificationDao dao = new NotificationDao(conn);
                    Map<String, LocalDateTime> readAtById = dao.readStateByUser(userId);
                    for (NotificationItem it : items) {
                        String key = it == null ? null : it.key();
                        if (key == null || key.isBlank()) continue;
                        String id = notificationId(userId, key);
                        if (id == null) continue;
                        if (!readAtById.containsKey(id)) {
                            dao.insertIgnore(toNotification(userId, id, it));
                        }
                    }
                    Map<String, Boolean> out = new HashMap<>();
                    for (Map.Entry<String, LocalDateTime> e : readAtById.entrySet()) {
                        if (e.getKey() != null) out.put(e.getKey(), e.getValue() != null);
                    }
                    return out;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            com.projectpilot.util.AppLog.warn("notifications", "Persisted read state failed: " + (e == null ? "unknown" : e.getMessage()));
            return Map.of();
        }
    }

    private void persistMarkRead(String key) {
        DbManager db = dbIfAvailable();
        String userId = currentUserId();
        if (userId == null || key == null || key.isBlank()) return;
        String id = notificationId(userId, key);
        if (id == null) return;

        if (db == null) {
            markLocalRead(userId, id, System.currentTimeMillis());
            return;
        }

        try {
            db.tx(conn -> {
                try {
                    new NotificationDao(conn).markRead(id, LocalDateTime.now());
                    return null;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (Exception e) {
            com.projectpilot.util.AppLog.warn("notifications", "markRead failed: " + (e == null ? "unknown" : e.getMessage()));
        }
    }

    private void persistMarkAllRead() {
        DbManager db = dbIfAvailable();
        String userId = currentUserId();
        if (userId == null) return;
        if (db == null) {
            markLocalAllRead(userId);
            return;
        }
        try {
            db.tx(conn -> {
                try {
                    new NotificationDao(conn).markAllRead(userId, LocalDateTime.now());
                    return null;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (Exception e) {
            com.projectpilot.util.AppLog.warn("notifications", "markAllRead failed: " + (e == null ? "unknown" : e.getMessage()));
        }
    }

    private static String notificationId(String userId, String key) {
        if (userId == null || userId.isBlank() || key == null || key.isBlank()) return null;
        return userId + "|" + key;
    }

    private static Notification toNotification(String userId, String id, NotificationItem it) {
        String title = it == null ? "Notification" : safe(it.title(), "Notification");
        String body = it == null ? "" : safe(it.detail(), "");
        String key = it == null ? "" : safe(it.key(), "");
        NotificationType type = mapType(title, key);

        String entityKind = null;
        String entityId = null;
        if (key != null && !key.isBlank()) {
            String[] parts = key.split(":", 3);
            if (parts.length == 3) {
                entityKind = "TASK";
                entityId = parts[2];
            }
        }

        LocalDateTime at = (it != null && it.at() != null) ? it.at() : LocalDateTime.now();
        return new Notification(id, userId, at, type, title, body, entityKind, entityId, null, null);
    }

    private static NotificationType mapType(String title, String key) {
        String t = title == null ? "" : title.toLowerCase();
        if (t.contains("due") || t.contains("overdue") || key.startsWith("due-") || key.startsWith("overdue:")) {
            return NotificationType.TASK_DUE_SOON;
        }
        if (t.contains("assigned") || key.startsWith("assigned:")) {
            return NotificationType.TASK_ASSIGNED;
        }
        return NotificationType.TASK_UPDATED;
    }

    private Map<String, Boolean> loadLocalReadState(String userId, List<NotificationItem> items) {
        Map<String, Long> readAtById = parseLocalMap(userId);
        Set<String> knownIds = new HashSet<>();
        if (items != null) {
            for (NotificationItem it : items) {
                if (it == null) continue;
                String id = notificationId(userId, it.key());
                if (id != null) knownIds.add(id);
            }
        }

        boolean changed = readAtById.keySet().removeIf(id -> !knownIds.contains(id));
        if (changed) saveLocalMap(userId, readAtById);

        Map<String, Boolean> out = new HashMap<>();
        for (String id : knownIds) {
            out.put(id, readAtById.containsKey(id));
        }
        return out;
    }

    private void markLocalRead(String userId, String id, long at) {
        Map<String, Long> map = parseLocalMap(userId);
        map.put(id, at);
        saveLocalMap(userId, map);
    }

    private void markLocalAllRead(String userId) {
        Map<String, Long> map = parseLocalMap(userId);
        long now = System.currentTimeMillis();
        for (NotificationItem it : items) {
            if (it == null) continue;
            String id = notificationId(userId, it.key());
            if (id != null) map.put(id, now);
        }
        saveLocalMap(userId, map);
    }

    private Map<String, Long> parseLocalMap(String userId) {
        String raw = prefs.get(prefKey(userId), "");
        Map<String, Long> out = new HashMap<>();
        if (raw == null || raw.isBlank()) return out;

        String[] pairs = raw.split(";");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) continue;
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            String id = pair.substring(0, idx);
            String v = pair.substring(idx + 1);
            try {
                long ts = Long.parseLong(v);
                out.put(id, ts);
            } catch (NumberFormatException nfe) {
                com.projectpilot.util.AppLog.warn("notifications", "Invalid persisted timestamp for " + id + ": " + (nfe == null ? "" : nfe.getMessage()));
            }
        }
        return out;
    }

    private void saveLocalMap(String userId, Map<String, Long> map) {
        if (map == null || map.isEmpty()) {
            prefs.remove(prefKey(userId));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue() == null ? 0L : e.getValue());
        }
        prefs.put(prefKey(userId), sb.toString());
    }

    private static String prefKey(String userId) {
        return "read:" + userId;
    }
}
