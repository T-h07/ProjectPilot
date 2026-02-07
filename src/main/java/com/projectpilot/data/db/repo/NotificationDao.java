package com.projectpilot.data.db.repo;

import com.projectpilot.model.Notification;
import com.projectpilot.model.enums.NotificationType;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class NotificationDao {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Connection cx;

    public NotificationDao(Connection cx) {
        this.cx = Objects.requireNonNull(cx);
    }

    public void insert(Notification n) throws SQLException {
        String sql = """
            INSERT INTO notifications(
              id, target_user_id, created_at, type, title, body,
              entity_kind, entity_id, actor_user_id, read_at
            ) VALUES(?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, n.id());
            ps.setString(2, n.targetUserId());
            ps.setString(3, toDb(n.createdAt()));
            ps.setString(4, n.type().name());
            ps.setString(5, n.title());
            ps.setString(6, n.body());
            ps.setString(7, n.entityKind());
            ps.setString(8, n.entityId());
            ps.setString(9, n.actorUserId());
            ps.setString(10, n.readAt() == null ? null : toDb(n.readAt()));
            ps.executeUpdate();
        }
    }

    public void insertIgnore(Notification n) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO notifications(
              id, target_user_id, created_at, type, title, body,
              entity_kind, entity_id, actor_user_id, read_at
            ) VALUES(?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, n.id());
            ps.setString(2, n.targetUserId());
            ps.setString(3, toDb(n.createdAt()));
            ps.setString(4, n.type().name());
            ps.setString(5, n.title());
            ps.setString(6, n.body());
            ps.setString(7, n.entityKind());
            ps.setString(8, n.entityId());
            ps.setString(9, n.actorUserId());
            ps.setString(10, n.readAt() == null ? null : toDb(n.readAt()));
            ps.executeUpdate();
        }
    }

    public List<Notification> listRecent(String userId, int limit) throws SQLException {
        String sql = """
            SELECT * FROM notifications
            WHERE target_user_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        }
    }

    public List<Notification> listSince(String userId, LocalDateTime since, int limit) throws SQLException {
        String sql = """
            SELECT * FROM notifications
            WHERE target_user_id = ?
              AND created_at > ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, toDb(since));
            ps.setInt(3, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        }
    }

    public int countUnread(String userId) throws SQLException {
        String sql = """
            SELECT COUNT(*) AS c
            FROM notifications
            WHERE target_user_id = ?
              AND read_at IS NULL
            """;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("c") : 0;
            }
        }
    }

    public void markRead(String notificationId, LocalDateTime at) throws SQLException {
        String sql = "UPDATE notifications SET read_at = ? WHERE id = ?";
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, toDb(at));
            ps.setString(2, notificationId);
            ps.executeUpdate();
        }
    }

    public void markAllRead(String userId, LocalDateTime at) throws SQLException {
        String sql = """
            UPDATE notifications
            SET read_at = ?
            WHERE target_user_id = ?
              AND read_at IS NULL
            """;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, toDb(at));
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    public Map<String, LocalDateTime> readStateByUser(String userId) throws SQLException {
        String sql = "SELECT id, read_at FROM notifications WHERE target_user_id = ?";
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, LocalDateTime> out = new HashMap<>();
                while (rs.next()) {
                    String id = rs.getString("id");
                    LocalDateTime readAt = fromDb(rs.getString("read_at"));
                    if (id != null) out.put(id, readAt);
                }
                return out;
            }
        }
    }

    private static String toDb(LocalDateTime t) { return t.format(FMT); }

    private static LocalDateTime fromDb(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDateTime.parse(s, FMT);
    }

    private static List<Notification> readAll(ResultSet rs) throws SQLException {
        List<Notification> out = new ArrayList<>();
        while (rs.next()) {
            out.add(new Notification(
                    rs.getString("id"),
                    rs.getString("target_user_id"),
                    fromDb(rs.getString("created_at")),
                    NotificationType.valueOf(rs.getString("type")),
                    rs.getString("title"),
                    rs.getString("body"),
                    rs.getString("entity_kind"),
                    rs.getString("entity_id"),
                    rs.getString("actor_user_id"),
                    fromDb(rs.getString("read_at"))
            ));
        }
        return out;
    }
}
