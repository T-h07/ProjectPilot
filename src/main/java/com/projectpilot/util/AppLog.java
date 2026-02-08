package com.projectpilot.util;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class AppLog {

    private static final int MAX_ENTRIES = 400;
    private static final ObservableList<LogEntry> ENTRIES = FXCollections.observableArrayList();

    private AppLog() {}

    public static ObservableList<LogEntry> entries() {
        return ENTRIES;
    }

    public static void clear() {
        runOnFx(ENTRIES::clear);
    }

    public static void info(String source, String message) {
        add(LogLevel.INFO, source, message, null);
    }

    public static void warn(String source, String message) {
        add(LogLevel.WARN, source, message, null);
    }

    public static void error(String source, String message, Throwable error) {
        add(LogLevel.ERROR, source, message, error);
    }

    private static void add(LogLevel level, String source, String message, Throwable error) {
        String src = source == null ? "" : source.trim();
        String msg = message == null ? "" : message.trim();
        LogEntry entry = new LogEntry(System.currentTimeMillis(), level, src, msg);

        runOnFx(() -> {
            ENTRIES.add(entry);
            int overflow = ENTRIES.size() - MAX_ENTRIES;
            if (overflow > 0) {
                ENTRIES.remove(0, overflow);
            }
        });

        if (error != null) {
            error.printStackTrace();
        }
    }

    private static void runOnFx(Runnable action) {
        if (action == null) return;
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException e) {
            action.run();
        }
    }
}
