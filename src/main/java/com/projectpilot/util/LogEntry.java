package com.projectpilot.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class LogEntry {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final long timestamp;
    private final LogLevel level;
    private final String source;
    private final String message;

    public LogEntry(long timestamp, LogLevel level, String source, String message) {
        this.timestamp = timestamp;
        this.level = level == null ? LogLevel.INFO : level;
        this.source = source == null ? "" : source.trim();
        this.message = message == null ? "" : message.trim();
    }

    public long timestamp() {
        return timestamp;
    }

    public LogLevel level() {
        return level;
    }

    public String source() {
        return source;
    }

    public String message() {
        return message;
    }

    public String timeLabel() {
        return TIME_FMT.format(Instant.ofEpochMilli(timestamp));
    }

    public String formatLine() {
        String src = source.isBlank() ? "app" : source;
        return timeLabel() + " [" + level + "] " + src + " - " + message;
    }
}
