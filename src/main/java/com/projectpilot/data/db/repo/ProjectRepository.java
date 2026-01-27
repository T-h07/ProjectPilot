package com.projectpilot.data.db.repo;

import com.projectpilot.data.db.DbException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProjectRepository {

    public record ProjectRow(
            String id,
            String name,
            String description,
            String stakeholders,
            String status,
            long createdAt,
            long updatedAt
    ) {}

    public void insert(Connection conn, ProjectRow p) {
        final String sql = """
            INSERT INTO projects (id, name, description, stakeholders, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.id());
            ps.setString(2, p.name());
            ps.setString(3, p.description());
            ps.setString(4, p.stakeholders());
            ps.setString(5, p.status());
            ps.setLong(6, p.createdAt());
            ps.setLong(7, p.updatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to insert project " + p.id(), e);
        }
    }

    public void update(Connection conn, ProjectRow p) {
        final String sql = """
            UPDATE projects
            SET name = ?, description = ?, stakeholders = ?, status = ?, updated_at = ?
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.name());
            ps.setString(2, p.description());
            ps.setString(3, p.stakeholders());
            ps.setString(4, p.status());
            ps.setLong(5, p.updatedAt());
            ps.setString(6, p.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to update project " + p.id(), e);
        }
    }

    public Optional<ProjectRow> findById(Connection conn, String id) {
        final String sql = """
            SELECT id, name, description, stakeholders, status, created_at, updated_at
            FROM projects
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DbException("Failed to find project " + id, e);
        }
    }

    public List<ProjectRow> findAll(Connection conn) {
        final String sql = """
            SELECT id, name, description, stakeholders, status, created_at, updated_at
            FROM projects
            ORDER BY updated_at DESC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<ProjectRow> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new DbException("Failed to list projects", e);
        }
    }

    public boolean deleteById(Connection conn, String id) {
        final String sql = "DELETE FROM projects WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DbException("Failed to delete project " + id, e);
        }
    }

    private static ProjectRow map(ResultSet rs) throws SQLException {
        return new ProjectRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("stakeholders"),
                rs.getString("status"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
