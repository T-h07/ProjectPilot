package com.projectpilot.data.db;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

final class DbDates {
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private DbDates() {}

    static Long toEpochMillis(LocalDate d) {
        if (d == null) return null;
        return d.atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    static Long toEpochMillis(LocalDateTime dt) {
        if (dt == null) return null;
        return dt.atZone(ZONE).toInstant().toEpochMilli();
    }

    static LocalDate fromEpochMillisToLocalDate(Long ms) {
        if (ms == null) return null;
        return java.time.Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDate();
    }

    static LocalDateTime fromEpochMillisToLocalDateTime(Long ms) {
        if (ms == null) return null;
        return java.time.Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDateTime();
    }
}
