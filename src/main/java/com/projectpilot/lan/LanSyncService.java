package com.projectpilot.lan;

import com.projectpilot.core.AppState;
import com.projectpilot.lan.dto.SnapshotDto;
import com.projectpilot.model.Project;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.util.AppLog;
import javafx.application.Platform;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LanSyncService {

    private final RemoteStore store;
    private final LanClient client;
    private final AppState appState;
    private final int pollMs;
    private final LanWsClient wsClient;
    private final AccessPolicy policy = new AccessPolicy();

    private ScheduledExecutorService exec;
    private volatile boolean polling = false;
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);
    private final AtomicBoolean applyScheduled = new AtomicBoolean(false);
    private volatile SnapshotDto pendingSnapshot;
    private volatile long nextAllowedAt = 0L;
    private int failureCount = 0;

    public LanSyncService(RemoteStore store, LanClient client, AppState appState, int pollMs, LanWsClient wsClient) {
        this.store = store;
        this.client = client;
        this.appState = appState;
        this.pollMs = pollMs;
        this.wsClient = wsClient;
    }

    public void start() {
        if (exec != null) return;
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pp-lan-sync");
            t.setDaemon(true);
            return t;
        });
        if (appState != null) {
            appState.setClientStatus(false, "connecting");
        }
        exec.scheduleWithFixedDelay(this::poll, 0, pollMs, TimeUnit.MILLISECONDS);
        if (wsClient != null) {
            wsClient.connect(client.token());
        }
    }

    public void stop() {
        if (exec == null) return;
        exec.shutdownNow();
        exec = null;
        if (wsClient != null) wsClient.close();
        if (appState != null) {
            appState.setClientStatus(false, "offline");
        }
    }

    private void poll() {
        long now = System.currentTimeMillis();
        if (now < nextAllowedAt && !refreshPending.get()) {
            return;
        }
        if (polling) {
            refreshPending.set(true);
            return;
        }
        polling = true;
        refreshPending.set(false);
        try {
            SnapshotDto snapshot = client.fetchSnapshot();
            scheduleApply(snapshot);
            failureCount = 0;
            nextAllowedAt = 0L;
            if (appState != null) {
                appState.setClientStatus(true, "synced just now");
            }
        } catch (Exception e) {
            AppLog.warn("lan-sync", "Sync failed: " + shortError(e));
            failureCount++;
            nextAllowedAt = now + computeBackoffMs();
            if (appState != null) {
                appState.setClientStatus(false, "sync failed");
            }
        } finally {
            polling = false;
            if (refreshPending.getAndSet(false) && exec != null) {
                exec.execute(this::poll);
            }
        }
    }

    public void requestRefresh() {
        if (exec == null) return;
        refreshPending.set(true);
        nextAllowedAt = 0L;
        exec.execute(this::poll);
    }

    private void scheduleApply(SnapshotDto snapshot) {
        if (snapshot == null) return;
        pendingSnapshot = snapshot;
        if (applyScheduled.compareAndSet(false, true)) {
            Platform.runLater(this::drainApplyQueue);
        }
    }

    private void drainApplyQueue() {
        try {
            SnapshotDto next;
            do {
                next = pendingSnapshot;
                pendingSnapshot = null;
                if (next != null) applySnapshot(next);
            } while (pendingSnapshot != null);
        } finally {
            applyScheduled.set(false);
            if (pendingSnapshot != null && applyScheduled.compareAndSet(false, true)) {
                Platform.runLater(this::drainApplyQueue);
            }
        }
    }

    private void applySnapshot(SnapshotDto snapshot) {
        String selectedId = appState.getSelectedProject() == null ? null : appState.getSelectedProject().getId();
        store.applySnapshot(snapshot);

        if (selectedId != null) {
            Project selected = store.findProjectById(selectedId);
            if (selected != null) appState.setSelectedProject(selected);
        }

        if (appState.getSelectedProject() == null) {
            Project initial = store.getProjects().stream()
                    .filter(p -> policy.canViewProject(appState, p))
                    .findFirst()
                    .orElse(null);
            if (initial != null) appState.setSelectedProject(initial);
        }

        appState.refreshCurrentProjectRole();
    }

    private long computeBackoffMs() {
        int attempts = Math.min(failureCount, 5);
        long base = Math.max(1500L, pollMs);
        long backoff = base * (1L << attempts);
        long max = Math.max(8000L, pollMs * 6L);
        return Math.min(backoff, max);
    }

    private static String shortError(Exception e) {
        if (e == null) return "unknown";
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return e.getClass().getSimpleName();
        String trimmed = msg.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 117) + "..." : trimmed;
    }
}
