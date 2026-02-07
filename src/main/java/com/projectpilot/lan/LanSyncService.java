package com.projectpilot.lan;

import com.projectpilot.core.AppState;
import com.projectpilot.lan.dto.SnapshotDto;
import com.projectpilot.model.Project;
import com.projectpilot.security.AccessPolicy;
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
    }

    private void poll() {
        if (polling) {
            refreshPending.set(true);
            return;
        }
        polling = true;
        refreshPending.set(false);
        try {
            SnapshotDto snapshot = client.fetchSnapshot();
            scheduleApply(snapshot);
        } catch (Exception e) {
            System.err.println("[LAN] Sync failed: " + e.getMessage());
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
}
