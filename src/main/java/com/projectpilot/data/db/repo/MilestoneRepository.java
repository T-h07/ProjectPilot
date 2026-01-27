package com.projectpilot.data.db.repo;

import com.projectpilot.data.db.DbException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.projectpilot.data.db.repo.JdbcSupport.*;

public final class MilestoneRepository {

    public record MilestoneRow(
            String id,
            String projectId,
            String title,
            Long targetAt,
            boolean isDone,
            Long doneAt,
            long createdAt,
            long updatedAt
    ) {}

    public void insert(Connection conn, MilestoneRow m) {
        final String sql = """
            INSERT INTO milestones (id, project_id, title, target_at, is_done, done_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.id());
            ps.setString(2, m.projectId());
            ps.setString(3, m.title());
            bindNullableLong(ps, 4, m.targetAt());
            ps.setInt(5, boolToInt(m.isDone()));
            bindNullableLong(ps, 6, m.doneAt());
            ps.setLong(7, m.createdAt());
            ps.setLong(8, m.updatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to insert milestone " + m.id(), e);
        }
    }

    public void update(Connection conn, MilestoneRow m) {
        final String sql = """
            UPDATE milestones
            SET title = ?, target_at = ?, is_done = ?, done_at = ?, updated_at = ?
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.title());
            bindNullableLong(ps, 2, m.targetAt());
            ps.setInt(3, boolToInt(m.isDone()));
            bindNullableLong(ps, 4, m.doneAt());
            ps.setLong(5, m.updatedAt());
            ps.setString(6, m.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to update milestone " + m.id(), e);
        }
    }

    public List<MilestoneRow> findByProjectId(Connection conn, String projectId) {
        final String sql = """
            SELECT id, project_id, title, target_at, is_done, done_at, created_at, updated_at
            FROM milestones
            WHERE project_id = ?
            ORDER BY is_done ASC, target_at IS NULL, target_at ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<MilestoneRow> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DbException("Failed to list milestones for project " + projectId, e);
        }
    }

    public boolean deleteById(Connection conn, String id) {
        final String sql = "DELETE FROM milestones WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DbException("Failed to delete milestone " + id, e);
        }
    }

    private static MilestoneRow map(ResultSet rs) throws SQLException {
        return new MilestoneRow(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("title"),
                readNullableLong(rs, "target_at"),
                intToBool(rs.getInt("is_done")),
                readNullableLong(rs, "done_at"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
