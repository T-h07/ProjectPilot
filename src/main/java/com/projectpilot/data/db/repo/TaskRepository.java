package com.projectpilot.data.db.repo;

import com.projectpilot.data.db.DbException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.projectpilot.data.db.repo.JdbcSupport.*;

public final class TaskRepository {

    public record TaskRow(
            String id,
            String projectId,
            String phaseId,
            String title,
            String details,
            String status,
            String assigneeMemberId,
            Long startAt,
            Long dueAt,
            Long completedAt,
            int sortIndex,
            long createdAt,
            long updatedAt
    ) {}

    public void insert(Connection conn, TaskRow t) {
        final String sql = """
            INSERT INTO tasks (
              id, project_id, phase_id, title, details, status, assignee_member_id,
              start_at, due_at, completed_at, sort_index, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.id());
            ps.setString(2, t.projectId());
            ps.setString(3, t.phaseId());
            ps.setString(4, t.title());
            ps.setString(5, t.details());
            ps.setString(6, t.status());
            ps.setString(7, t.assigneeMemberId());
            bindNullableLong(ps, 8, t.startAt());
            bindNullableLong(ps, 9, t.dueAt());
            bindNullableLong(ps, 10, t.completedAt());
            ps.setInt(11, t.sortIndex());
            ps.setLong(12, t.createdAt());
            ps.setLong(13, t.updatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to insert task " + t.id(), e);
        }
    }

    public void update(Connection conn, TaskRow t) {
        final String sql = """
            UPDATE tasks
            SET phase_id = ?, title = ?, details = ?, status = ?, assignee_member_id = ?,
                start_at = ?, due_at = ?, completed_at = ?, sort_index = ?, updated_at = ?
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.phaseId());
            ps.setString(2, t.title());
            ps.setString(3, t.details());
            ps.setString(4, t.status());
            ps.setString(5, t.assigneeMemberId());
            bindNullableLong(ps, 6, t.startAt());
            bindNullableLong(ps, 7, t.dueAt());
            bindNullableLong(ps, 8, t.completedAt());
            ps.setInt(9, t.sortIndex());
            ps.setLong(10, t.updatedAt());
            ps.setString(11, t.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to update task " + t.id(), e);
        }
    }

    public Optional<TaskRow> findById(Connection conn, String id) {
        final String sql = """
            SELECT id, project_id, phase_id, title, details, status, assignee_member_id,
                   start_at, due_at, completed_at, sort_index, created_at, updated_at
            FROM tasks
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DbException("Failed to find task " + id, e);
        }
    }

    public List<TaskRow> findByProjectId(Connection conn, String projectId) {
        final String sql = """
            SELECT id, project_id, phase_id, title, details, status, assignee_member_id,
                   start_at, due_at, completed_at, sort_index, created_at, updated_at
            FROM tasks
            WHERE project_id = ?
            ORDER BY phase_id IS NULL DESC, phase_id ASC, sort_index ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TaskRow> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DbException("Failed to list tasks for project " + projectId, e);
        }
    }

    public boolean deleteById(Connection conn, String id) {
        final String sql = "DELETE FROM tasks WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DbException("Failed to delete task " + id, e);
        }
    }

    private static TaskRow map(ResultSet rs) throws SQLException {
        return new TaskRow(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("phase_id"),
                rs.getString("title"),
                rs.getString("details"),
                rs.getString("status"),
                rs.getString("assignee_member_id"),
                readNullableLong(rs, "start_at"),
                readNullableLong(rs, "due_at"),
                readNullableLong(rs, "completed_at"),
                rs.getInt("sort_index"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
