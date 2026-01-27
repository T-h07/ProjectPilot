package com.projectpilot.data.db.auth;

import com.projectpilot.data.db.DbException;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.model.enums.ProjectRole;

import java.sql.*;
import java.util.*;

public final class UserAdminService {

    private final DbManager db;
    private final PasswordHasher hasher = new PasswordHasher(12);

    public UserAdminService(DbManager db) {
        this.db = db;
    }

    public record UserRow(String id, String username, String name, GlobalRole globalRole, boolean active) {}

    public List<UserRow> listLoginUsers() {
        return db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    SELECT
                        au.member_id,
                        au.username,
                        COALESCE(m.name, '') AS display_name,
                        au.global_role,
                        au.is_active
                    FROM auth_users au
                    LEFT JOIN members m ON m.id = au.member_id
                    ORDER BY lower(au.username)
                    """
            );
                 ResultSet rs = ps.executeQuery()) {

                List<UserRow> out = new ArrayList<>();
                while (rs.next()) {
                    String id = rs.getString("member_id");
                    String username = rs.getString("username");
                    String name = rs.getString("display_name");
                    String gr = rs.getString("global_role");
                    boolean active = rs.getInt("is_active") == 1;

                    GlobalRole role;
                    try { role = GlobalRole.valueOf(gr == null ? "USER" : gr); }
                    catch (Exception ignored) { role = GlobalRole.USER; }

                    out.add(new UserRow(id, username, name, role, active));
                }
                return out;
            } catch (Exception e) {
                throw new DbException("List users failed", e);
            }
        });
    }

    public void createUser(String displayName, String username, String password, GlobalRole role) {
        final String name = (displayName == null) ? "" : displayName.trim();
        final String u = (username == null) ? "" : username.trim();

        if (u.isBlank()) throw new IllegalArgumentException("Username is required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required");

        final GlobalRole roleFinal = (role == null) ? GlobalRole.USER : role;
        final String memberId = UUID.randomUUID().toString();
        final long now = System.currentTimeMillis();

        // Your PasswordHasher returns String (not HashOut)
        final String hash = hasher.hash(password);

        db.tx(conn -> {
            try {
                insertMember(conn, memberId, name.isBlank() ? u : name, now);

                // schema has salt column -> store empty string (or you can remove salt later)
                insertAuthUser(conn, memberId, u, hash, "", roleFinal, true, now);

            } catch (Exception e) {
                throw new DbException("Create user failed", e);
            }
        });
    }

    public void setUserActive(String userId, boolean active) {
        final long now = System.currentTimeMillis();

        db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE auth_users SET is_active = ?, updated_at = ? WHERE member_id = ?"
            )) {
                ps.setInt(1, active ? 1 : 0);
                ps.setLong(2, now);
                ps.setString(3, userId);
                ps.executeUpdate();
            } catch (Exception e) {
                throw new DbException("Set active failed", e);
            }
        });
    }

    public void resetPassword(String userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        final long now = System.currentTimeMillis();
        final String hash = hasher.hash(newPassword);

        db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE auth_users SET password_hash = ?, salt = ?, updated_at = ? WHERE member_id = ?"
            )) {
                ps.setString(1, hash);
                ps.setString(2, ""); // keep schema happy
                ps.setLong(3, now);
                ps.setString(4, userId);
                ps.executeUpdate();
            } catch (Exception e) {
                throw new DbException("Reset password failed", e);
            }
        });
    }

    public Map<String, ProjectRole> rolesForUser(String userId) {
        return db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT project_id, project_role FROM project_members WHERE member_id = ?"
            )) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, ProjectRole> out = new HashMap<>();
                    while (rs.next()) {
                        String projectId = rs.getString("project_id");
                        String pr = rs.getString("project_role");
                        out.put(projectId, parseProjectRole(pr));
                    }
                    return out;
                }
            } catch (Exception e) {
                throw new DbException("Load roles failed", e);
            }
        });
    }

    public void upsertProjectRole(String projectId, String userId, ProjectRole role) {
        final ProjectRole roleFinal = (role == null) ? ProjectRole.MEMBER : role;
        final long now = System.currentTimeMillis();

        db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO project_members(project_id, member_id, project_role, added_at)
                    VALUES(?,?,?,?)
                    ON CONFLICT(project_id, member_id)
                    DO UPDATE SET project_role = excluded.project_role
                    """
            )) {
                ps.setString(1, projectId);
                ps.setString(2, userId);
                ps.setString(3, roleFinal.name());
                ps.setLong(4, now);
                ps.executeUpdate();
            } catch (Exception e) {
                throw new DbException("Upsert project role failed", e);
            }
        });
    }

    public void removeFromProject(String projectId, String userId) {
        db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM project_members WHERE project_id = ? AND member_id = ?"
            )) {
                ps.setString(1, projectId);
                ps.setString(2, userId);
                ps.executeUpdate();
            } catch (Exception e) {
                throw new DbException("Remove from project failed", e);
            }
        });
    }

    // ---------------- helpers ----------------

    private static ProjectRole parseProjectRole(String v) {
        if (v == null || v.isBlank()) return ProjectRole.MEMBER;
        try { return ProjectRole.valueOf(v); }
        catch (Exception ignored) { return ProjectRole.MEMBER; }
    }

    private static void insertMember(Connection conn, String id, String name, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO members(id, name, role, email, created_at, updated_at) VALUES(?,?,?,?,?,?)"
        )) {
            ps.setString(1, id);
            ps.setString(2, name == null ? "" : name);
            ps.setString(3, "MEMBER");
            ps.setString(4, "");
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        }
    }

    private static void insertAuthUser(
            Connection conn,
            String memberId,
            String username,
            String passwordHash,
            String salt,
            GlobalRole role,
            boolean active,
            long now
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO auth_users(member_id, username, password_hash, salt, global_role, is_active, created_at, updated_at)
                VALUES(?,?,?,?,?,?,?,?)
                """
        )) {
            ps.setString(1, memberId);
            ps.setString(2, username);
            ps.setString(3, passwordHash);
            ps.setString(4, salt == null ? "" : salt);
            ps.setString(5, role == null ? "USER" : role.name());
            ps.setInt(6, active ? 1 : 0);
            ps.setLong(7, now);
            ps.setLong(8, now);
            ps.executeUpdate();
        }
    }
}
