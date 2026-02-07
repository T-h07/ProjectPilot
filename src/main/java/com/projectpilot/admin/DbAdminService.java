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
}
