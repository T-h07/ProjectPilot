package com.projectpilot.data.db.repo;

import com.projectpilot.data.db.DbException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class ActivityLogRepository {

    public record ActivityRow(
            String id,
            long at,
            String actor,
            String projectId,
            String entityType,
            String entityId,
            String action,
            String details
    ) {}

    public void append(Connection conn, ActivityRow a) {
        final String sql = """
            INSERT INTO activity_log (id, at, actor, project_id, entity_type, entity_id, action, details)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.id());
            ps.setLong(2, a.at());
            ps.setString(3, a.actor());
            ps.setString(4, a.projectId());
            ps.setString(5, a.entityType());
            ps.setString(6, a.entityId());
            ps.setString(7, a.action());
            ps.setString(8, a.details());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to append activity " + a.id(), e);
        }
    }

    public List<ActivityRow> listRecent(Connection conn, int limit) {
        final String sql = """
            SELECT id, at, actor, project_id, entity_type, entity_id, action, details
            FROM activity_log
            ORDER BY at DESC
            LIMIT ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<ActivityRow> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DbException("Failed to list recent activity", e);
        }
    }

    public List<ActivityRow> listByProject(Connection conn, String projectId, int limit) {
        final String sql = """
            SELECT id, at, actor, project_id, entity_type, entity_id, action, details
            FROM activity_log
            WHERE project_id = ?
            ORDER BY at DESC
            LIMIT ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<ActivityRow> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DbException("Failed to list activity for project " + projectId, e);
        }
    }

    private static ActivityRow map(ResultSet rs) throws SQLException {
        return new ActivityRow(
                rs.getString("id"),
                rs.getLong("at"),
                rs.getString("actor"),
                rs.getString("project_id"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("action"),
                rs.getString("details")
        );
    }
}
