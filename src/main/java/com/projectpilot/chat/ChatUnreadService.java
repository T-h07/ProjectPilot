package com.projectpilot.chat;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import javafx.application.Platform;
import com.projectpilot.util.AppLog;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatUnreadService {

    private final ChatService chat;
    private final AppState appState;
    private final int pollMs;
    private final ScheduledExecutorService exec;
    private final AtomicBoolean polling = new AtomicBoolean(false);

    public ChatUnreadService(ChatService chat, AppState appState, int pollMs) {
        this.chat = Objects.requireNonNull(chat);
        this.appState = Objects.requireNonNull(appState);
        this.pollMs = Math.max(1500, pollMs);
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pp-chat-unread");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        exec.scheduleWithFixedDelay(this::poll, 0, pollMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        exec.shutdownNow();
    }

    private void poll() {
        if (appState.getSession() == null) return;
        if (appState.getCurrentPage() == PageId.MESSAGES) return;
        if (!polling.compareAndSet(false, true)) return;

        try {
            String me = appState.getSession().id();
            if (me == null || me.isBlank()) return;
            List<ChatThread> threads = chat.listThreads(me);
            Platform.runLater(() -> appState.updateChatThreads(threads));
        } catch (Exception e) {
            AppLog.warn("chat", "Unread poll failed: " + (e == null ? "" : e.getMessage()));
        } finally {
            polling.set(false);
        }
    }
}
