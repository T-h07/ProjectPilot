package com.projectpilot.data.db.auth;

import com.projectpilot.data.db.DbManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

public final class AuthService implements AuthProvider {

    private final DbManager db;
    private final AuthRepository repo;
    private final PasswordHasher hasher = new PasswordHasher(12);

    public AuthService(DbManager db) {
        this.db = db;
        this.repo = new AuthRepository();
    }

    public boolean needsInitialAdmin() {
        return db.tx(conn -> {
            try {
                return !repo.hasAnyLoginUsers(conn);
            } catch (Exception e) {
                throw new AuthException("Failed to check users", e);
            }
        });
    }

    public UserSession login(String username, String password) {
        if (username == null || username.isBlank()) throw new AuthException("Enter username.");
        if (password == null || password.isBlank()) throw new AuthException("Enter password.");

        return db.tx(conn -> {
            try {
                AuthRepository.LoginRow row = repo.findByUsername(conn, username.trim());
                if (row == null) throw new AuthException("Invalid username or password.");
                if (!row.active()) throw new AuthException("This account is disabled.");
                if (!hasher.verify(password, row.passwordHash())) throw new AuthException("Invalid username or password.");

                // member_id is the user identity; displayName can come from members table
                return new UserSession(row.memberId(), row.username(), row.displayName(), row.globalRole());
            } catch (AuthException ae) {
                throw ae;
            } catch (Exception e) {
                throw new AuthException("Login failed", e);
            }
        });
    }

    public UserSession createInitialAdmin(String displayName, String username, String email, String password) {
        if (displayName == null || displayName.isBlank()) throw new AuthException("Enter name.");
        if (username == null || username.isBlank()) throw new AuthException("Enter username.");
        if (email == null || email.isBlank() || !email.contains("@")) throw new AuthException("Enter a valid email.");
        if (password == null || password.isBlank()) throw new AuthException("Enter password.");

        return db.tx(conn -> {
            try {
                if (repo.hasAnyLoginUsers(conn)) {
                    throw new AuthException("Admin already exists. Please log in.");
                }

                String memberId = UUID.randomUUID().toString();
                long now = System.currentTimeMillis();

                // 1) create a member row (so auth_users.member_id FK is valid)
                insertMemberIfMissing(conn, memberId, displayName.trim(), email.trim(), now);

                // 2) create auth row
                String hash = hasher.hash(password);
                repo.insertAuthUser(conn, memberId, username.trim(), hash, GlobalRole.ADMIN, true, now);

                return new UserSession(memberId, username.trim(), displayName.trim(), GlobalRole.ADMIN);

            } catch (AuthException ae) {
                throw ae;
            } catch (Exception e) {
                throw new AuthException("Failed to create admin", e);
            }
        });
    }

    // ---- helpers ----

    private static void insertMemberIfMissing(Connection conn, String memberId, String name, String email, long now) {
        // members table (v2) columns:
        // id, name, role, email, created_at, updated_at
        // (If your members table differs, tell me and I’ll adjust.)
        try (PreparedStatement check = conn.prepareStatement(
                "SELECT 1 FROM members WHERE id = ?"
        )) {
            check.setString(1, memberId);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return;
            }
        } catch (Exception e) {
            throw new AuthException("Failed to check member", e);
        }

        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO members(id, name, role, email, created_at, updated_at) VALUES(?,?,?,?,?,?)"
        )) {
            ins.setString(1, memberId);
            ins.setString(2, name);
            ins.setString(3, "ADMIN"); // this is your ProjectRole enum default area; it won't control login role
            ins.setString(4, email == null ? "" : email.trim());
            ins.setLong(5, now);
            ins.setLong(6, now);
            ins.executeUpdate();
        } catch (Exception e) {
            throw new AuthException("Failed to create member", e);
        }
    }
}
