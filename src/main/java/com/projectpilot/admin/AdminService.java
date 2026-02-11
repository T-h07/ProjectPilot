package com.projectpilot.admin;

import com.projectpilot.data.db.TeamService;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.model.Member;
import com.projectpilot.model.enums.ProjectRole;

import java.util.List;
import java.util.Map;

public interface AdminService {
    List<UserAdminService.UserRow> listLoginUsers();

    void createUserWithEmailAndUsername(String displayName, String username, String email, String password, GlobalRole role);

    void updateUser(
            String userId,
            String displayName,
            String username,
            String email,
            String newPassword,
            GlobalRole role,
            boolean active
    );

    void deleteUser(String userId);

    void setUserActive(String userId, boolean active);

    Map<String, ProjectRole> rolesForUser(String userId);

    void upsertProjectRole(String projectId, String userId, ProjectRole role);

    List<Member> listDirectoryUsers();

    void createTeam(String name, String leaderId, List<TeamService.TeamMemberSpec> members);

    java.util.List<String> runDataValidator();
}
