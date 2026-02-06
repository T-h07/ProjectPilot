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

    public record UserRow(String id, String username, String name, String email, GlobalRole globalRole, boolean active) {}

    public List<UserRow> listLoginUsers() {
        return db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    SELECT
                        au.member_id,
                        au.username,
                        COALESCE(m.name, '') AS display_name,
                        COALESCE(m.email, '') AS email,
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
                    String email = rs.getString("email");
                    String gr = rs.getString("global_role");
                    boolean active = rs.getInt("is_active") == 1;

                    GlobalRole role;
                    try { role = GlobalRole.valueOf(gr == null ? "USER" : gr); }
                    catch (Exception ignored) { role = GlobalRole.USER; }

                    out.add(new UserRow(id, username, name, email, role, active));
                }
                return out;
            } catch (Exception e) {
                throw new DbException("List users failed", e);
            }
        });
    }

    /**
     * Update existing login user info.
     * - displayName updates members.name
     * - username updates auth_users.username (unique check)
     * - newPassword (optional) updates password_hash if provided (non-blank)
     * - globalRole updates auth_users.global_role
     * - active updates auth_users.is_active
     *
     * Safety:
     * - You cannot demote/deactivate the LAST active admin.
     */
    public void updateUser(
            String userId,
            String displayName,
            String username,
            String email,
            String newPasswordOrNull,
            GlobalRole newRole,
            boolean active
    ) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("User id is required");

        final String u = (username == null) ? "" : username.trim();
        if (u.isBlank()) throw new IllegalArgumentException("Username is required");

        final GlobalRole roleFinal = (newRole == null) ? GlobalRole.USER : newRole;
        final String dispTrim = (displayName == null) ? "" : displayName.trim();
        final String dispFinal = dispTrim.isBlank() ? u : dispTrim;
        final String emailFinal = (email == null) ? "" : email.trim();
        if (!emailFinal.isBlank() && !emailFinal.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }

        final long now = System.currentTimeMillis();
        final String pwTrim = (newPasswordOrNull == null) ? "" : newPasswordOrNull.trim();
        final boolean changePw = !pwTrim.isBlank();
        final String newHash = changePw ? hasher.hash(pwTrim) : null;

        db.tx(conn -> {
            try {
                Current cur = loadCurrent(conn, userId);
                if (cur == null) throw new IllegalArgumentException("User not found: " + userId);

                // Unique username check if changed
                if (!u.equalsIgnoreCase(cur.username)) {
                    if (usernameExistsOther(conn, u, userId)) {
                        throw new IllegalArgumentException("Username/email already exists: " + u);
                    }
                }

                // Safety: prevent losing last active admin
                boolean curIsActiveAdmin = cur.active && cur.role == GlobalRole.ADMIN;
                boolean willBeActiveAdmin = active && roleFinal == GlobalRole.ADMIN;

                if (curIsActiveAdmin && !willBeActiveAdmin) {
                    if (countActiveAdmins(conn) <= 1) {
                        throw new IllegalStateException("Cannot remove/deactivate the last active admin account.");
                    }
                }

                // Update members display name + email
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE members SET name = ?, email = ?, updated_at = ? WHERE id = ?"
                )) {
                    ps.setString(1, dispFinal);
                    ps.setString(2, emailFinal);
                    ps.setLong(3, now);
                    ps.setString(4, userId);
                    ps.executeUpdate();
                }

                // Update auth_users (+ password optionally)
                if (changePw) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            """
                            UPDATE auth_users
                               SET username = ?,
                                   password_hash = ?,
                                   salt = ?,
                                   global_role = ?,
                                   is_active = ?,
                                   updated_at = ?
                             WHERE member_id = ?
                            """
                    )) {
                        ps.setString(1, u);
                        ps.setString(2, newHash);
                        ps.setString(3, ""); // keep schema happy
                        ps.setString(4, roleFinal.name());
                        ps.setInt(5, active ? 1 : 0);
                        ps.setLong(6, now);
                        ps.setString(7, userId);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            """
                            UPDATE auth_users
                               SET username = ?,
                                   global_role = ?,
                                   is_active = ?,
                                   updated_at = ?
                             WHERE member_id = ?
                            """
                    )) {
                        ps.setString(1, u);
                        ps.setString(2, roleFinal.name());
                        ps.setInt(3, active ? 1 : 0);
                        ps.setLong(4, now);
                        ps.setString(5, userId);
                        ps.executeUpdate();
                    }
                }

                return null;
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new DbException("Update user failed", e);
            }
        });
    }

    /**
     * Create a new login user where username=email, and members.email is populated.
     */
    public void createUserWithEmail(String firstName, String lastName, String email, String password, GlobalRole role) {
        String fn = firstName == null ? "" : firstName.trim();
        String ln = lastName == null ? "" : lastName.trim();
        String em = email == null ? "" : email.trim();

        if (em.isBlank() || !em.contains("@")) throw new IllegalArgumentException("Email is required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required");

        String display = (fn + " " + ln).trim();
        if (display.isBlank()) display = em;

        createUserInternal(display, em, em, password, role);
    }

    /**
     * Create a new login user with a separate email field.
     */
    public void createUserWithEmailAndUsername(String displayName, String username, String email, String password, GlobalRole role) {
        String u = username == null ? "" : username.trim();
        String em = email == null ? "" : email.trim();
        if (u.isBlank()) throw new IllegalArgumentException("Username is required");
        if (em.isBlank()) throw new IllegalArgumentException("Email is required");
        if (!em.contains("@")) throw new IllegalArgumentException("Valid email is required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required");

        createUserInternal(displayName, u, em, password, role);
    }

    /**
     * Backward-compatible: create a user with arbitrary username (no members.email).
     */
    public void createUser(String displayName, String username, String password, GlobalRole role) {
        createUserInternal(displayName, username, "", password, role);
    }

    /**
     * Delete ONLY login account row; member/project data remains.
     * Safety: cannot delete the last active admin.
     */
    public void deleteUser(String memberId) {
        if (memberId == null || memberId.isBlank()) return;

        db.tx(conn -> {
            try {
                Current cur = loadCurrent(conn, memberId);
                if (cur == null) return null;

                if (cur.active && cur.role == GlobalRole.ADMIN) {
                    if (countActiveAdmins(conn) <= 1) {
                        throw new IllegalStateException("Cannot delete the last active admin account.");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM auth_users WHERE member_id = ?"
                )) {
                    ps.setString(1, memberId);
                    ps.executeUpdate();
                }
                return null;
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new DbException("Delete user failed", e);
            }
        });
    }

    public void setUserActive(String userId, boolean active) {
        final long now = System.currentTimeMillis();

        db.tx(conn -> {
            try {
                // Safety: prevent deactivating last active admin
                if (!active && isActiveAdmin(conn, userId) && countActiveAdmins(conn) <= 1) {
                    throw new IllegalStateException("Cannot deactivate the last active admin account.");
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE auth_users SET is_active = ?, updated_at = ? WHERE member_id = ?"
                )) {
                    ps.setInt(1, active ? 1 : 0);
                    ps.setLong(2, now);
                    ps.setString(3, userId);
                    ps.executeUpdate();
                }
                return null;
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new DbException("Set active failed", e);
            }
        });
    }

    // keep for compatibility (your Admin UI uses Edit dialog now)
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
                ps.setString(2, "");
                ps.setLong(3, now);
                ps.setString(4, userId);
                ps.executeUpdate();
                return null;
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
                return null;
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
                return null;
            } catch (Exception e) {
                throw new DbException("Remove from project failed", e);
            }
        });
    }

    // ---------------- internal create ----------------

    private void createUserInternal(String displayName, String username, String email, String password, GlobalRole role) {
        final String name = (displayName == null) ? "" : displayName.trim();
        final String u = (username == null) ? "" : username.trim();
        final String em = (email == null) ? "" : email.trim();

        if (u.isBlank()) throw new IllegalArgumentException("Username is required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required");

        final GlobalRole roleFinal = (role == null) ? GlobalRole.USER : role;
        final String memberId = UUID.randomUUID().toString();
        final long now = System.currentTimeMillis();

        final String hash = hasher.hash(password);

        db.tx(conn -> {
            try {
                if (usernameExists(conn, u)) {
                    throw new IllegalArgumentException("Username/email already exists: " + u);
                }

                insertMember(conn, memberId, name.isBlank() ? u : name, em, now);
                insertAuthUser(conn, memberId, u, hash, "", roleFinal, true, now);

                return null;
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new DbException("Create user failed", e);
            }
        });
    }

    private static boolean usernameExists(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM auth_users WHERE lower(username)=lower(?) LIMIT 1"
        )) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean usernameExistsOther(Connection conn, String username, String currentUserId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM auth_users WHERE lower(username)=lower(?) AND member_id <> ? LIMIT 1"
        )) {
            ps.setString(1, username);
            ps.setString(2, currentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ---------------- admin safety helpers ----------------

    private static int countActiveAdmins(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) AS c FROM auth_users WHERE global_role = 'ADMIN' AND is_active = 1"
        );
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("c") : 0;
        }
    }

    private static boolean isActiveAdmin(Connection conn, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM auth_users WHERE member_id = ? AND global_role = 'ADMIN' AND is_active = 1 LIMIT 1"
        )) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private record Current(String username, GlobalRole role, boolean active) {}

    private static Current loadCurrent(Connection conn, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT username, global_role, is_active FROM auth_users WHERE member_id = ? LIMIT 1"
        )) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String u = rs.getString("username");
                String gr = rs.getString("global_role");
                boolean a = rs.getInt("is_active") == 1;

                GlobalRole r;
                try { r = GlobalRole.valueOf(gr == null ? "USER" : gr); }
                catch (Exception ignored) { r = GlobalRole.USER; }

                return new Current(u, r, a);
            }
        }
    }

    // ---------------- helpers ----------------

    private static ProjectRole parseProjectRole(String v) {
        if (v == null || v.isBlank()) return ProjectRole.MEMBER;
        try { return ProjectRole.valueOf(v); }
        catch (Exception ignored) { return ProjectRole.MEMBER; }
    }

    private static void insertMember(Connection conn, String id, String name, String email, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO members(id, name, role, email, created_at, updated_at) VALUES(?,?,?,?,?,?)"
        )) {
            ps.setString(1, id);
            ps.setString(2, name == null ? "" : name);
            ps.setString(3, "MEMBER");
            ps.setString(4, email == null ? "" : email);
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
