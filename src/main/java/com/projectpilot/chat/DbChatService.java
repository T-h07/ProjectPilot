package com.projectpilot.chat;

import com.projectpilot.data.db.DbException;
import com.projectpilot.data.db.DbManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public final class DbChatService implements ChatService {

    private final DbManager db;

    public DbChatService(DbManager db) {
        this.db = Objects.requireNonNull(db);
    }

    @Override
    public List<ChatThread> listThreads(String memberId) {
        String me = safe(memberId);
        if (me.isBlank()) return List.of();

        return db.tx(conn -> {
            try {
                Map<String, String> teamNames = ensureTeamThreads(conn, me);

                List<ChatThread> out = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT ct.id,
                               ct.type,
                               ct.team_id,
                               ct.title,
                               ct.updated_at,
                               (SELECT body FROM chat_messages m WHERE m.thread_id = ct.id ORDER BY m.created_at DESC LIMIT 1) AS last_body,
                               (SELECT created_at FROM chat_messages m WHERE m.thread_id = ct.id ORDER BY m.created_at DESC LIMIT 1) AS last_at
                        FROM chat_threads ct
                        JOIN chat_members cm ON cm.thread_id = ct.id
                        WHERE cm.member_id = ?
                        ORDER BY COALESCE(last_at, ct.updated_at) DESC
                        """
                )) {
                    ps.setString(1, me);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String id = rs.getString("id");
                            ChatType type = parseType(rs.getString("type"));
                            String teamId = rs.getString("team_id");
                            String title = safe(rs.getString("title"));
                            String lastBody = rs.getString("last_body");
                            Long lastAt = rs.getObject("last_at") == null ? null : rs.getLong("last_at");

                            String subtitle = lastBody == null ? "" : trimSnippet(lastBody);
                            String otherId = null;

                            if (type == ChatType.TEAM) {
                                String tn = teamId == null ? "" : teamNames.getOrDefault(teamId, "");
                                if (!tn.isBlank()) title = tn;
                                if (subtitle.isBlank()) subtitle = "Team chat";
                            } else if (type == ChatType.DIRECT) {
                                otherId = findOtherMember(conn, id, me);
                                String otherName = lookupDisplayName(conn, otherId);
                                if (!otherName.isBlank()) title = otherName;
                                if (subtitle.isBlank()) subtitle = "Direct message";
                            } else {
                                if (title.isBlank()) title = "Group chat";
                                if (subtitle.isBlank()) subtitle = "Group chat";
                            }

                            out.add(new ChatThread(id, type, title, subtitle, teamId, otherId, lastAt));
                        }
                    }
                }
                return out;
            } catch (Exception e) {
                throw new DbException("List chat threads failed", e);
            }
        });
    }

    @Override
    public List<ChatUser> listUsers(String excludeMemberId) {
        String exclude = safe(excludeMemberId);
        return db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    SELECT au.member_id AS id,
                           COALESCE(NULLIF(m.name,''), au.username) AS display_name
                    FROM auth_users au
                    LEFT JOIN members m ON m.id = au.member_id
                    WHERE au.is_active = 1
                    ORDER BY lower(display_name)
                    """
            )) {
                List<ChatUser> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        if (!exclude.isBlank() && exclude.equals(id)) continue;
                        String display = rs.getString("display_name");
                        out.add(new ChatUser(id, safe(display)));
                    }
                }
                return out;
            } catch (Exception e) {
                throw new DbException("List chat users failed", e);
            }
        });
    }

    @Override
    public ChatThread getOrCreateDirect(String memberId, String otherMemberId) {
        String me = safe(memberId);
        String other = safe(otherMemberId);
        if (me.isBlank() || other.isBlank()) throw new IllegalArgumentException("Missing user");
        if (me.equals(other)) throw new IllegalArgumentException("Cannot start a chat with yourself");

        return db.tx(conn -> {
            try {
                String key = directKey(me, other);
                String threadId = findDirectThreadId(conn, key);
                if (threadId == null) {
                    threadId = UUID.randomUUID().toString();
                    long now = System.currentTimeMillis();
                    insertThread(conn, threadId, ChatType.DIRECT, "", null, key, now);
                    upsertMember(conn, threadId, me, now);
                    upsertMember(conn, threadId, other, now);
                }

                String otherName = lookupDisplayName(conn, other);
                String title = otherName.isBlank() ? "Direct message" : otherName;
                return new ChatThread(threadId, ChatType.DIRECT, title, "Direct message", null, other, null);
            } catch (Exception e) {
                throw new DbException("Create direct chat failed", e);
            }
        });
    }

    @Override
    public List<ChatMessage> listMessages(String threadId, String viewerId, int limit) {
        String tid = safe(threadId);
        String viewer = safe(viewerId);
        if (tid.isBlank()) return List.of();
        int lim = limit <= 0 ? 100 : limit;

        return db.tx(conn -> {
            try {
                if (!isMember(conn, tid, viewer)) return List.of();

                try (PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT m.id,
                               m.thread_id,
                               m.sender_id,
                               COALESCE(NULLIF(mem.name,''), au.username, 'User') AS sender_name,
                               m.body,
                               m.created_at
                        FROM (
                            SELECT * FROM chat_messages
                            WHERE thread_id = ?
                            ORDER BY created_at DESC
                            LIMIT ?
                        ) m
                        LEFT JOIN members mem ON mem.id = m.sender_id
                        LEFT JOIN auth_users au ON au.member_id = m.sender_id
                        ORDER BY m.created_at ASC
                        """
                )) {
                    ps.setString(1, tid);
                    ps.setInt(2, lim);
                    List<ChatMessage> out = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(new ChatMessage(
                                    rs.getString("id"),
                                    rs.getString("thread_id"),
                                    rs.getString("sender_id"),
                                    rs.getString("sender_name"),
                                    rs.getString("body"),
                                    rs.getLong("created_at")
                            ));
                        }
                    }
                    return out;
                }
            } catch (Exception e) {
                throw new DbException("List chat messages failed", e);
            }
        });
    }

    @Override
    public ChatMessage sendMessage(String threadId, String senderId, String body) {
        String tid = safe(threadId);
        String sid = safe(senderId);
        String msg = body == null ? "" : body.trim();
        if (tid.isBlank()) throw new IllegalArgumentException("Thread is required");
        if (sid.isBlank()) throw new IllegalArgumentException("Sender is required");
        if (msg.isBlank()) throw new IllegalArgumentException("Message is empty");

        return db.tx(conn -> {
            try {
                if (!isMember(conn, tid, sid)) {
                    throw new IllegalArgumentException("Not a member of this chat");
                }

                String id = UUID.randomUUID().toString();
                long now = System.currentTimeMillis();

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO chat_messages(id, thread_id, sender_id, body, created_at) VALUES(?,?,?,?,?)"
                )) {
                    ps.setString(1, id);
                    ps.setString(2, tid);
                    ps.setString(3, sid);
                    ps.setString(4, msg);
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE chat_threads SET updated_at = ? WHERE id = ?"
                )) {
                    ps.setLong(1, now);
                    ps.setString(2, tid);
                    ps.executeUpdate();
                }

                String senderName = lookupDisplayName(conn, sid);
                return new ChatMessage(id, tid, sid, senderName, msg, now);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new DbException("Send message failed", e);
            }
        });
    }

    // ---------------- internal helpers ----------------

    private Map<String, String> ensureTeamThreads(Connection conn, String memberId) throws Exception {
        Map<String, String> teams = new HashMap<>();
        List<TeamInfo> list = listTeamsForMember(conn, memberId);
        Set<String> teamIds = new HashSet<>();
        for (TeamInfo t : list) {
            if (t != null && t.id() != null && !t.id().isBlank()) teamIds.add(t.id());
        }
        pruneTeamMemberships(conn, memberId, teamIds);

        for (TeamInfo t : list) {
            teams.put(t.id(), t.name());
            String threadId = findTeamThreadId(conn, t.id());
            if (threadId == null) {
                threadId = UUID.randomUUID().toString();
                long now = System.currentTimeMillis();
                insertThread(conn, threadId, ChatType.TEAM, t.name(), t.id(), null, now);
            } else {
                updateThreadTitle(conn, threadId, t.name());
            }
            syncTeamMembers(conn, threadId, t.id());
        }

        return teams;
    }

    private List<TeamInfo> listTeamsForMember(Connection conn, String memberId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT t.id, t.name
                FROM teams t
                JOIN team_members tm ON tm.team_id = t.id
                WHERE tm.member_id = ?
                ORDER BY lower(t.name)
                """
        )) {
            ps.setString(1, memberId);
            List<TeamInfo> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TeamInfo(
                            rs.getString("id"),
                            safe(rs.getString("name"))
                    ));
                }
            }
            return out;
        }
    }

    private String findTeamThreadId(Connection conn, String teamId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM chat_threads WHERE team_id = ? AND type = 'TEAM' LIMIT 1"
        )) {
            ps.setString(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("id") : null;
            }
        }
    }

    private String findDirectThreadId(Connection conn, String directKey) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM chat_threads WHERE direct_key = ? AND type = 'DIRECT' LIMIT 1"
        )) {
            ps.setString(1, directKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("id") : null;
            }
        }
    }

    private void insertThread(
            Connection conn,
            String id,
            ChatType type,
            String title,
            String teamId,
            String directKey,
            long now
    ) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO chat_threads(id, type, title, team_id, direct_key, created_at, updated_at)
                VALUES(?,?,?,?,?,?,?)
                """
        )) {
            ps.setString(1, id);
            ps.setString(2, type == null ? ChatType.GROUP.name() : type.name());
            ps.setString(3, safe(title));
            ps.setString(4, teamId);
            ps.setString(5, directKey);
            ps.setLong(6, now);
            ps.setLong(7, now);
            ps.executeUpdate();
        }
    }

    private void updateThreadTitle(Connection conn, String threadId, String title) throws Exception {
        if (threadId == null || threadId.isBlank()) return;
        String t = safe(title);
        if (t.isBlank()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE chat_threads SET title = ? WHERE id = ?"
        )) {
            ps.setString(1, t);
            ps.setString(2, threadId);
            ps.executeUpdate();
        }
    }

    private void syncTeamMembers(Connection conn, String threadId, String teamId) throws Exception {
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM chat_members WHERE thread_id = ?"
        )) {
            del.setString(1, threadId);
            del.executeUpdate();
        }

        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT member_id FROM team_members WHERE team_id = ?
                """
        )) {
            ps.setString(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String memberId = rs.getString("member_id");
                    if (memberId == null || memberId.isBlank()) continue;
                    upsertMember(conn, threadId, memberId, now);
                }
            }
        }
    }

    private void pruneTeamMemberships(Connection conn, String memberId, Set<String> teamIds) throws Exception {
        if (memberId == null || memberId.isBlank()) return;
        if (teamIds == null || teamIds.isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM chat_members WHERE member_id = ? AND thread_id IN (SELECT id FROM chat_threads WHERE type = 'TEAM')"
            )) {
                ps.setString(1, memberId);
                ps.executeUpdate();
            }
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(teamIds.size(), "?"));
        String sql = "DELETE FROM chat_members WHERE member_id = ? AND thread_id IN (" +
                "SELECT id FROM chat_threads WHERE type = 'TEAM' AND team_id NOT IN (" + placeholders + "))";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, memberId);
            int idx = 2;
            for (String id : teamIds) {
                ps.setString(idx++, id);
            }
            ps.executeUpdate();
        }
    }

    private void upsertMember(Connection conn, String threadId, String memberId, long now) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO chat_members(thread_id, member_id, joined_at)
                VALUES(?,?,?)
                ON CONFLICT(thread_id, member_id) DO NOTHING
                """
        )) {
            ps.setString(1, threadId);
            ps.setString(2, memberId);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private boolean isMember(Connection conn, String threadId, String memberId) throws Exception {
        if (threadId == null || memberId == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM chat_members WHERE thread_id = ? AND member_id = ? LIMIT 1"
        )) {
            ps.setString(1, threadId);
            ps.setString(2, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String findOtherMember(Connection conn, String threadId, String memberId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT member_id FROM chat_members WHERE thread_id = ? AND member_id <> ? LIMIT 1"
        )) {
            ps.setString(1, threadId);
            ps.setString(2, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("member_id") : null;
            }
        }
    }

    private String lookupDisplayName(Connection conn, String memberId) throws Exception {
        if (memberId == null || memberId.isBlank()) return "";
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT COALESCE(NULLIF(m.name,''), au.username, 'User') AS display_name
                FROM members m
                LEFT JOIN auth_users au ON au.member_id = m.id
                WHERE m.id = ?
                LIMIT 1
                """
        )) {
            ps.setString(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? safe(rs.getString("display_name")) : "User";
            }
        }
    }

    private static ChatType parseType(String v) {
        if (v == null || v.isBlank()) return ChatType.GROUP;
        try {
            return ChatType.valueOf(v);
        } catch (Exception ignored) {
            return ChatType.GROUP;
        }
    }

    private static String directKey(String a, String b) {
        if (a.compareToIgnoreCase(b) <= 0) return a + ":" + b;
        return b + ":" + a;
    }

    private static String trimSnippet(String s) {
        if (s == null) return "";
        String v = s.trim();
        if (v.length() <= 80) return v;
        return v.substring(0, 77) + "...";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private record TeamInfo(String id, String name) {}
}
