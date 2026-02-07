package com.projectpilot.service;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.NotificationItem;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
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

public final class NotificationService {

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();

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
        } catch (Exception ignored) {}

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
    }

    public void markRead(String key) {
        if (key == null) return;
        List<NotificationItem> next = new ArrayList<>(items.size());
        for (NotificationItem it : items) {
            next.add(Objects.equals(key, it.key()) ? it.withRead(true) : it);
        }
        items.setAll(next);
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

        // preserve read state by key
        List<NotificationItem> out = new ArrayList<>(built.size());
        for (NotificationItem it : built) {
            boolean r = wasRead.getOrDefault(it.key(), false);
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
}
