package com.projectpilot.data.db.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class UserAccountDao {

    private final Connection conn;

    public UserAccountDao(Connection conn) {
        this.conn = conn;
    }

    public List<UserAccount> listAll() {
        // Try "global_role" first, then fallback to "role"
        String sql1 = "SELECT id, email, username, global_role AS role FROM users ORDER BY username COLLATE NOCASE";
        String sql2 = "SELECT id, email, username, role AS role FROM users ORDER BY username COLLATE NOCASE";

        try {
            return run(sql1);
        } catch (Exception ignored) {
            return run(sql2);
        }
    }

    private List<UserAccount> run(String sql) {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<UserAccount> out = new ArrayList<>();
            while (rs.next()) {
                long id = rs.getLong("id");
                String email = rs.getString("email");
                String username = rs.getString("username");
                String roleStr = rs.getString("role");
                GlobalRole role = (roleStr == null) ? GlobalRole.USER : GlobalRole.valueOf(roleStr);

                out.add(new UserAccount(id, email, username, role));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("UserAccountDao.listAll failed", e);
        }
    }
}
