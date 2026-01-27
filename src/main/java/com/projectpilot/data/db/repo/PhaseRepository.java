package com.projectpilot.data.db.repo;

import com.projectpilot.data.db.DbException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.projectpilot.data.db.repo.JdbcSupport.*;

public final class PhaseRepository {

    public record PhaseRow(
            String id,
            String projectId,
            String name,
            int sortIndex,
            Long startAt,
            Long endAt,
            boolean isDone,
            long createdAt,
            long updatedAt
    ) {}

    public void insert(Connection conn, PhaseRow p) {
        final String sql = """
            INSERT INTO phases (id, project_id, name, sort_index, start_at, end_at, is_done, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.id());
            ps.setString(2, p.projectId());
            ps.setString(3, p.name());
            ps.setInt(4, p.sortIndex());
            bindNullableLong(ps, 5, p.startAt());
            bindNullableLong(ps, 6, p.endAt());
            ps.setInt(7, boolToInt(p.isDone()));
            ps.setLong(8, p.createdAt());
            ps.setLong(9, p.updatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to insert phase " + p.id(), e);
        }
    }

    public void update(Connection conn, PhaseRow p) {
        final String sql = """
            UPDATE phases
            SET name = ?, sort_index = ?, start_at = ?, end_at = ?, is_done = ?, updated_at = ?
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.name());
            ps.setInt(2, p.sortIndex());
            bindNullableLong(ps, 3, p.startAt());
            bindNullableLong(ps, 4, p.endAt());
            ps.setInt(5, boolToInt(p.isDone()));
            ps.setLong(6, p.updatedAt());
            ps.setString(7, p.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to update phase " + p.id(), e);
        }
    }

    public List<PhaseRow> findByProjectId(Connection conn, String projectId) {
        final String sql = """
            SELECT id, project_id, name, sort_index, start_at, end_at, is_done, created_at, updated_at
            FROM phases
            WHERE project_id = ?
            ORDER BY sort_index ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PhaseRow> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DbException("Failed to list phases for project " + projectId, e);
        }
    }

    public boolean deleteById(Connection conn, String id) {
        final String sql = "DELETE FROM phases WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DbException("Failed to delete phase " + id, e);
        }
    }

    private static PhaseRow map(ResultSet rs) throws SQLException {
        return new PhaseRow(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("name"),
                rs.getInt("sort_index"),
                readNullableLong(rs, "start_at"),
                readNullableLong(rs, "end_at"),
                intToBool(rs.getInt("is_done")),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
