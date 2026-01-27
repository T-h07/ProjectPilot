package com.projectpilot.core.security;

import com.projectpilot.core.AppState;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.ProjectRole;

public final class AccessControl {

    private AccessControl() {}

    public static boolean isAdmin(AppState state) {
        UserSession s = (state == null) ? null : state.getSession();
        return s != null && s.globalRole() == GlobalRole.ADMIN;
    }

    public static ProjectRole roleFor(AppState state, Project project) {
        if (project == null) return ProjectRole.VIEWER;

        UserSession s = (state == null) ? null : state.getSession();
        if (s == null) return ProjectRole.VIEWER;

        // Global admin -> treat as leader everywhere
        if (s.globalRole() == GlobalRole.ADMIN) return ProjectRole.LEADER;

        // Match logged-in user to a project member by ID
        for (Member m : project.getMembers()) {
            if (m != null && s.id().equals(m.getId())) {
                ProjectRole r = m.getRole();
                return (r == null) ? ProjectRole.MEMBER : r;
            }
        }

        return ProjectRole.VIEWER;
    }

    public static boolean canEditProject(AppState state, Project project) {
        if (isAdmin(state)) return true;
        ProjectRole r = roleFor(state, project);
        return r == ProjectRole.LEADER || r == ProjectRole.ADMIN;
    }

    public static boolean canManageTeam(AppState state, Project project) {
        return canEditProject(state, project);
    }

    public static boolean canAssignTasks(AppState state, Project project) {
        return canEditProject(state, project);
    }

    public static boolean canCreateOrDeleteTask(AppState state, Project project) {
        return canEditProject(state, project);
    }

    public static boolean canEditTask(AppState state, Project project, Task task) {
        if (task == null) return false;
        if (isAdmin(state)) return true;

        ProjectRole r = roleFor(state, project);
        if (r == ProjectRole.LEADER || r == ProjectRole.ADMIN) return true;

        UserSession s = (state == null) ? null : state.getSession();
        if (s == null) return false;

        // MEMBER can edit only their own assigned tasks
        if (r == ProjectRole.MEMBER) {
            return task.getAssignee() != null && s.id().equals(task.getAssignee().getId());
        }

        return false;
    }

    public static boolean canChangeTaskStatus(AppState state, Project project, Task task) {
        return canEditTask(state, project, task);
    }
}
