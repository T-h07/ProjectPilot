package com.projectpilot.admin;

import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.TeamService;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.model.Member;
import com.projectpilot.model.enums.ProjectRole;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class DbAdminService implements AdminService {

    private final UserAdminService users;
    private final DbStore store;

    public DbAdminService(DbManager db, DbStore store) {
        this.users = new UserAdminService(Objects.requireNonNull(db));
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public List<UserAdminService.UserRow> listLoginUsers() {
        return users.listLoginUsers();
    }

    @Override
    public void createUserWithEmailAndUsername(String displayName, String username, String email, String password, GlobalRole role) {
        users.createUserWithEmailAndUsername(displayName, username, email, password, role);
    }

    @Override
    public void updateUser(String userId, String displayName, String username, String email, String newPassword, GlobalRole role, boolean active) {
        users.updateUser(userId, displayName, username, email, newPassword, role, active);
    }

    @Override
    public void deleteUser(String userId) {
        users.deleteUser(userId);
    }

    @Override
    public void setUserActive(String userId, boolean active) {
        users.setUserActive(userId, active);
    }

    @Override
    public Map<String, ProjectRole> rolesForUser(String userId) {
        return users.rolesForUser(userId);
    }

    @Override
    public void upsertProjectRole(String projectId, String userId, ProjectRole role) {
        users.upsertProjectRole(projectId, userId, role);
    }

    @Override
    public List<Member> listDirectoryUsers() {
        return store.listDirectoryUsers();
    }

    @Override
    public void createTeam(String name, String leaderId, List<TeamService.TeamMemberSpec> members) {
        store.createTeam(name, leaderId, members);
    }

    @Override
    public java.util.List<String> runDataValidator() {
        try {
            return store.manager().tx(conn -> {
                java.util.List<String> issues = new java.util.ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, project_id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects)")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            issues.add("Task " + rs.getString("id") + " references missing project " + rs.getString("project_id"));
                        }
                    }
                } catch (Exception e) {
                    issues.add("Validator error (tasks->projects): " + e.getMessage());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, phase_id FROM tasks WHERE phase_id IS NOT NULL AND phase_id NOT IN (SELECT id FROM phases)")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            issues.add("Task " + rs.getString("id") + " references missing phase " + rs.getString("phase_id"));
                        }
                    }
                } catch (Exception e) {
                    issues.add("Validator error (tasks->phases): " + e.getMessage());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, assignee_member_id FROM tasks WHERE assignee_member_id IS NOT NULL AND assignee_member_id NOT IN (SELECT id FROM members)")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            issues.add("Task " + rs.getString("id") + " has invalid assignee " + rs.getString("assignee_member_id"));
                        }
                    }
                } catch (Exception e) {
                    issues.add("Validator error (tasks->assignee): " + e.getMessage());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT project_id, member_id FROM project_members WHERE member_id NOT IN (SELECT id FROM members)")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            issues.add("Project membership references missing member: project=" + rs.getString("project_id") + " member=" + rs.getString("member_id"));
                        }
                    }
                } catch (Exception e) {
                    issues.add("Validator error (project_members->members): " + e.getMessage());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, project_id FROM phases WHERE project_id NOT IN (SELECT id FROM projects)")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            issues.add("Phase " + rs.getString("id") + " references missing project " + rs.getString("project_id"));
                        }
                    }
                } catch (Exception e) {
                    issues.add("Validator error (phases->projects): " + e.getMessage());
                }

                return issues;
            });
        } catch (Exception e) {
            throw new IllegalStateException("Validation failed", e);
        }
    }
}
