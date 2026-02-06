package com.projectpilot.data.db.repo;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class NotificationStateDao {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Connection cx;

    public NotificationStateDao(Connection cx) {
        this.cx = Objects.requireNonNull(cx);
    }

    public LocalDateTime getLastSeen(String userId) throws SQLException {
        String sql = "SELECT last_seen_at FROM user_notification_state WHERE user_id = ?";
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String s = rs.getString("last_seen_at");
                if (s == null || s.isBlank()) return null;
                return LocalDateTime.parse(s, FMT);
            }
        }
    }

    public void upsertLastSeen(String userId, LocalDateTime at) throws SQLException {
        String sql = """
            INSERT INTO user_notification_state(user_id, last_seen_at)
            VALUES(?, ?)
            ON CONFLICT(user_id) DO UPDATE SET last_seen_at = excluded.last_seen_at
            """;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, at.format(FMT));
            ps.executeUpdate();
        }
    }
}
