package com.projectpilot.data.db.repo;

import com.projectpilot.data.db.DbException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MemberRepository {

    public record MemberRow(
            String id,
            String name,
            String role,
            String email,
            long createdAt,
            long updatedAt
    ) {}

    public record ProjectMemberRow(
            String projectId,
            String memberId,
            String projectRole,
            long addedAt
    ) {}

    public void insert(Connection conn, MemberRow m) {
        final String sql = """
            INSERT INTO members (id, name, role, email, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.id());
            ps.setString(2, m.name());
            ps.setString(3, m.role());
            ps.setString(4, m.email());
            ps.setLong(5, m.createdAt());
            ps.setLong(6, m.updatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to insert member " + m.id(), e);
        }
    }

    public void update(Connection conn, MemberRow m) {
        final String sql = """
            UPDATE members
            SET name = ?, role = ?, email = ?, updated_at = ?
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.name());
            ps.setString(2, m.role());
            ps.setString(3, m.email());
            ps.setLong(4, m.updatedAt());
            ps.setString(5, m.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to update member " + m.id(), e);
        }
    }

    public Optional<MemberRow> findById(Connection conn, String id) {
        final String sql = """
            SELECT id, name, role, email, created_at, updated_at
            FROM members
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DbException("Failed to find member " + id, e);
        }
    }

    public List<MemberRow> findAll(Connection conn) {
        final String sql = """
            SELECT id, name, role, email, created_at, updated_at
            FROM members
            ORDER BY name ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<MemberRow> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new DbException("Failed to list members", e);
        }
    }

    public boolean deleteById(Connection conn, String id) {
        final String sql = "DELETE FROM members WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DbException("Failed to delete member " + id, e);
        }
    }

    public void addToProject(Connection conn, ProjectMemberRow pm) {
        final String sql = """
            INSERT INTO project_members (project_id, member_id, project_role, added_at)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pm.projectId());
            ps.setString(2, pm.memberId());
            ps.setString(3, pm.projectRole());
            ps.setLong(4, pm.addedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Failed to add member to project (project=" + pm.projectId() + ", member=" + pm.memberId() + ")", e);
        }
    }

    public boolean removeFromProject(Connection conn, String projectId, String memberId) {
        final String sql = """
            DELETE FROM project_members
            WHERE project_id = ? AND member_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, memberId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DbException("Failed to remove member from project (project=" + projectId + ", member=" + memberId + ")", e);
        }
    }

    public List<ProjectMemberRow> listProjectMembers(Connection conn, String projectId) {
        final String sql = """
            SELECT project_id, member_id, project_role, added_at
            FROM project_members
            WHERE project_id = ?
            ORDER BY added_at ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProjectMemberRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new ProjectMemberRow(
                            rs.getString("project_id"),
                            rs.getString("member_id"),
                            rs.getString("project_role"),
                            rs.getLong("added_at")
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new DbException("Failed to list project members for project " + projectId, e);
        }
    }

    private static MemberRow map(ResultSet rs) throws SQLException {
        return new MemberRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("role"),
                rs.getString("email"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
