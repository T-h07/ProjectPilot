package com.projectpilot.data.db;

import com.projectpilot.model.enums.ProjectRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TeamService {

    private final DbManager db;

    public TeamService(DbManager db) {
        this.db = Objects.requireNonNull(db);
    }

    public record TeamRow(
            String id,
            String name,
            String leaderId,
            String leaderName,
            int memberCount
    ) {}

    public record TeamMemberRow(
            String memberId,
            String name,
            ProjectRole role
    ) {}

    public record TeamMemberSpec(
            String memberId,
            ProjectRole role
    ) {}

    public List<TeamRow> listTeams() {
        return db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    SELECT
                        t.id,
                        t.name,
                        tm.member_id AS leader_id,
                        COALESCE(NULLIF(m.name,''), au.username, '') AS leader_name,
                        (SELECT COUNT(*) FROM team_members tm2 WHERE tm2.team_id = t.id) AS member_count
                    FROM teams t
                    LEFT JOIN team_members tm
                           ON tm.team_id = t.id AND tm.team_role = 'LEADER'
                    LEFT JOIN members m ON m.id = tm.member_id
                    LEFT JOIN auth_users au ON au.member_id = tm.member_id
                    ORDER BY lower(t.name)
                    """
            );
                 ResultSet rs = ps.executeQuery()) {

                List<TeamRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new TeamRow(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("leader_id"),
                            rs.getString("leader_name"),
                            rs.getInt("member_count")
                    ));
                }
                return out;
            } catch (Exception e) {
                throw new DbException("List teams failed", e);
            }
        });
    }

    public List<TeamMemberRow> listTeamMembers(String teamId) {
        if (teamId == null || teamId.isBlank()) return List.of();

        return db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    SELECT tm.member_id,
                           COALESCE(NULLIF(m.name,''), au.username, '') AS display_name,
                           tm.team_role
                    FROM team_members tm
                    LEFT JOIN members m ON m.id = tm.member_id
                    LEFT JOIN auth_users au ON au.member_id = tm.member_id
                    WHERE tm.team_id = ?
                    ORDER BY CASE tm.team_role WHEN 'LEADER' THEN 0 WHEN 'MEMBER' THEN 1 ELSE 2 END,
                             lower(display_name)
                    """
            )) {
                ps.setString(1, teamId);

                List<TeamMemberRow> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String roleStr = rs.getString("team_role");
                        ProjectRole role = parseRole(roleStr, ProjectRole.MEMBER);
                        out.add(new TeamMemberRow(
                                rs.getString("member_id"),
                                rs.getString("display_name"),
                                role
                        ));
                    }
                }
                return out;
            } catch (Exception e) {
                throw new DbException("List team members failed", e);
            }
        });
    }

    public List<String> listTeamNamesForMemberInProject(String memberId, String projectId) {
        if (memberId == null || memberId.isBlank()) return List.of();
        if (projectId == null || projectId.isBlank()) return List.of();

        return db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    SELECT t.name
                    FROM teams t
                    JOIN team_members tm ON tm.team_id = t.id AND tm.member_id = ?
                    JOIN project_teams pt ON pt.team_id = t.id AND pt.project_id = ?
                    ORDER BY lower(t.name)
                    """
            )) {
                ps.setString(1, memberId);
                ps.setString(2, projectId);

                List<String> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        if (name != null && !name.isBlank()) out.add(name);
                    }
                }
                return out;
            } catch (Exception e) {
                throw new DbException("List member teams failed", e);
            }
        });
    }

    public void createTeam(String name, String leaderId, List<TeamMemberSpec> members) {
        String nm = name == null ? "" : name.trim();
        String leader = leaderId == null ? "" : leaderId.trim();
        if (nm.isBlank()) throw new IllegalArgumentException("Team name is required");
        if (leader.isBlank()) throw new IllegalArgumentException("Leader is required");

        final String teamId = UUID.randomUUID().toString();
        final long now = System.currentTimeMillis();

        db.tx(conn -> {
            if (teamNameExists(conn, nm)) {
                throw new IllegalArgumentException("Team already exists: " + nm);
            }
            insertTeam(conn, teamId, nm, now);

            insertTeamMember(conn, teamId, leader, ProjectRole.LEADER, now);

            if (members != null) {
                for (TeamMemberSpec spec : members) {
                    if (spec == null) continue;
                    String mid = spec.memberId() == null ? "" : spec.memberId().trim();
                    if (mid.isBlank() || mid.equals(leader)) continue;
                    ProjectRole role = normalizeMemberRole(spec.role());
                    insertTeamMember(conn, teamId, mid, role, now);
                }
            }
            return null;
        });
    }

    public void assignTeamToProject(String teamId, String projectId) {
        if (teamId == null || teamId.isBlank()) throw new IllegalArgumentException("Team is required");
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("Project is required");

        final long now = System.currentTimeMillis();

        db.tx(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO project_teams(project_id, team_id, added_at)
                    VALUES (?,?,?)
                    ON CONFLICT(project_id, team_id) DO NOTHING
                    """
            )) {
                ps.setString(1, projectId);
                ps.setString(2, teamId);
                ps.setLong(3, now);
                ps.executeUpdate();
            } catch (Exception e) {
                throw new DbException("Assign team failed", e);
            }

            List<TeamMemberRow> members = listTeamMembersInternal(conn, teamId);
            for (TeamMemberRow m : members) {
                ProjectRole role = normalizeMemberRole(m.role());
                upsertProjectMember(conn, projectId, m.memberId(), role, now);
            }
            return null;
        });
    }

    // ---------------- internal helpers ----------------

    private static void insertTeam(Connection conn, String id, String name, long now) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teams(id, name, created_at, updated_at) VALUES(?,?,?,?)"
        )) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Create team failed", e);
        }
    }

    private static void insertTeamMember(Connection conn, String teamId, String memberId, ProjectRole role, long now) {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO team_members(team_id, member_id, team_role, added_at)
                VALUES (?,?,?,?)
                ON CONFLICT(team_id, member_id) DO UPDATE SET team_role = excluded.team_role
                """
        )) {
            ps.setString(1, teamId);
            ps.setString(2, memberId);
            ps.setString(3, role.name());
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Add team member failed", e);
        }
    }

    private static void upsertProjectMember(Connection conn, String projectId, String memberId, ProjectRole role, long now) {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO project_members(project_id, member_id, project_role, added_at)
                VALUES (?,?,?,?)
                ON CONFLICT(project_id, member_id) DO UPDATE SET project_role = excluded.project_role
                """
        )) {
            ps.setString(1, projectId);
            ps.setString(2, memberId);
            ps.setString(3, role.name());
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Upsert project member failed", e);
        }
    }

    private static List<TeamMemberRow> listTeamMembersInternal(Connection conn, String teamId) {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT tm.member_id,
                       COALESCE(NULLIF(m.name,''), au.username, '') AS display_name,
                       tm.team_role
                FROM team_members tm
                LEFT JOIN members m ON m.id = tm.member_id
                LEFT JOIN auth_users au ON au.member_id = tm.member_id
                WHERE tm.team_id = ?
                """
        )) {
            ps.setString(1, teamId);
            List<TeamMemberRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String roleStr = rs.getString("team_role");
                    ProjectRole role = parseRole(roleStr, ProjectRole.MEMBER);
                    out.add(new TeamMemberRow(
                            rs.getString("member_id"),
                            rs.getString("display_name"),
                            role
                    ));
                }
            }
            return out;
        } catch (Exception e) {
            throw new DbException("Read team members failed", e);
        }
    }

    private static ProjectRole normalizeMemberRole(ProjectRole role) {
        if (role == null) return ProjectRole.MEMBER;
        if (role == ProjectRole.ADMIN) return ProjectRole.MEMBER;
        return role;
    }

    private static ProjectRole parseRole(String v, ProjectRole fallback) {
        if (v == null || v.isBlank()) return fallback;
        try { return ProjectRole.valueOf(v); } catch (Exception e) { com.projectpilot.util.AppLog.warn("db-team", "parseRole failed for '" + v + "': " + (e == null ? "" : e.getMessage())); return fallback; }
    }

    private static boolean teamNameExists(Connection conn, String name) {
        if (name == null || name.isBlank()) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM teams WHERE lower(name) = lower(?) LIMIT 1"
        )) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new DbException("Check team name failed", e);
        }
    }
}
