package com.projectpilot.data.db.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AuthRepository {

    // Row returned for login: includes member display name by joining members
    public record LoginRow(
            String memberId,
            String username,
            String passwordHash,
            GlobalRole globalRole,
            boolean active,
            String displayName
    ) {}

    public boolean hasAnyLoginUsers(Connection conn) throws SQLException {
        if (!tableExists(conn, "auth_users")) return false;
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM auth_users LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        if (conn == null || table == null || table.isBlank()) return false;
        java.sql.DatabaseMetaData meta = conn.getMetaData();
        String schema = null;
        try {
            schema = conn.getSchema();
        } catch (Exception ignored) {
        }
        String name = table.toLowerCase();
        try (ResultSet rs = meta.getTables(null, schema, name, new String[] { "TABLE" })) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = meta.getTables(null, "public", name, new String[] { "TABLE" })) {
            if (rs.next()) return true;
        }
        return false;
    }
    public LoginRow findByUsername(Connection conn, String username) throws SQLException {
        if (username == null || username.isBlank()) return null;

        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT
                    au.member_id,
                    au.username,
                    au.password_hash,
                    au.global_role,
                    au.is_active,
                    COALESCE(m.name, '') AS display_name
                FROM auth_users au
                LEFT JOIN members m ON m.id = au.member_id
                WHERE au.username = ?
                LIMIT 1
                """
        )) {
            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String memberId = rs.getString("member_id");
                String uname = rs.getString("username");
                String ph = rs.getString("password_hash");
                String gr = rs.getString("global_role");
                boolean active = rs.getInt("is_active") == 1;
                String dn = rs.getString("display_name");

                GlobalRole role;
                try {
                    role = GlobalRole.valueOf(gr == null ? "USER" : gr);
                } catch (Exception ignored) {
                    role = GlobalRole.USER;
                }

                return new LoginRow(memberId, uname, ph, role, active, dn);
            }
        }
    }

    public void insertAuthUser(
            Connection conn,
            String memberId,
            String username,
            String passwordHash,
            GlobalRole globalRole,
            boolean isActive,
            long nowMillis
    ) throws SQLException {

        if (memberId == null || memberId.isBlank()) throw new AuthException("Missing member id.");
        if (username == null || username.isBlank()) throw new AuthException("Username required.");
        if (passwordHash == null || passwordHash.isBlank()) throw new AuthException("Password hash required.");
        if (globalRole == null) globalRole = GlobalRole.USER;

        // NOTE: username is UNIQUE (case-sensitive by default). If you want case-insensitive:
        // use COLLATE NOCASE in schema or compare with LOWER().

        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO auth_users(member_id, username, password_hash, global_role, is_active, created_at, updated_at)
                VALUES(?,?,?,?,?,?,?)
                """
        )) {
            ps.setString(1, memberId);
            ps.setString(2, username.trim());
            ps.setString(3, passwordHash);
            ps.setString(4, globalRole.name());
            ps.setInt(5, isActive ? 1 : 0);
            ps.setLong(6, nowMillis);
            ps.setLong(7, nowMillis);
            ps.executeUpdate();
        }
    }

    public void setActive(Connection conn, String memberId, boolean active, long nowMillis) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE auth_users SET is_active = ?, updated_at = ? WHERE member_id = ?"
        )) {
            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, nowMillis);
            ps.setString(3, memberId);
            ps.executeUpdate();
        }
    }

    public void setLastOnline(Connection conn, String memberId, long nowMillis) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE auth_users SET last_online_at = ? WHERE member_id = ?"
        )) {
            ps.setLong(1, nowMillis);
            ps.setString(2, memberId);
            ps.executeUpdate();
        }
    }

    public void setPasswordHash(Connection conn, String memberId, String passwordHash, long nowMillis) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE auth_users SET password_hash = ?, updated_at = ? WHERE member_id = ?"
        )) {
            ps.setString(1, passwordHash);
            ps.setLong(2, nowMillis);
            ps.setString(3, memberId);
            ps.executeUpdate();
        }
    }
}
