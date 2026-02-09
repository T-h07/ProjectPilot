package com.projectpilot.security;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.ProjectRole;

import java.util.Objects;

public final class AccessPolicy {

    public boolean isLoggedIn(AppState appState) {
        return appState != null && appState.getSession() != null;
    }

    public boolean isAdmin(AppState appState) {
        return appState != null && appState.isAdmin();
    }

    /** Convenience alias in case any code calls policy.canAccess(id, appState). */
    public boolean canAccess(PageId id, AppState appState) {
        return canAccessPage(appState, id);
    }

    public boolean canAccessPage(AppState appState, PageId id) {
        if (!isLoggedIn(appState)) return false;
        if (id == null) return false;

        return switch (id) {
            case DASHBOARD, ACTIVITY, PROJECTS, PROJECT_OVERVIEW, TASKS, GANTT, CALENDAR, RESOURCES, NOTES, TEAM, MEETINGS, MESSAGES -> true;

            case EXPORT_REPORT ->
                    isAdmin(appState) || appState.getCurrentProjectRole() == ProjectRole.LEADER;

            case HISTORY, ADMIN -> isAdmin(appState);

            default -> false;
        };
    }

    public String denialMessage(PageId id, AppState appState) {
        if (!isLoggedIn(appState)) return "Access denied: please log in.";
        if (id == null) return "Access denied.";

        return switch (id) {
            case HISTORY, ADMIN -> "Access denied: admin only.";
            case EXPORT_REPORT -> "Access denied: requires project leader (or admin).";
            default -> "Access denied.";
        };
    }

    /**
     * IMPORTANT:
     * For "My Tasks" + project visibility to work, this must return the SAME id as Member.getId().
     * Some builds store member_id on the UserSession under a different accessor.
     */
    public String memberId(AppState appState) {
        UserSession s = (appState == null) ? null : appState.getSession();
        if (s == null) return null;

        // Try common member-id accessors first
        String v = readStringViaReflection(s,
                "memberId", "getMemberId",
                "member_id", "getMember_id",
                "memberUUID", "getMemberUUID"
        );

        // Fallback to id()
        if (v == null || v.isBlank()) v = s.id();

        return normalizeId(v);
    }

    private static String normalizeId(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }

    private static String readStringViaReflection(Object obj, String... methodNames) {
        for (String name : methodNames) {
            try {
                var m = obj.getClass().getMethod(name);
                Object out = m.invoke(obj);
                if (out instanceof String str && !str.isBlank()) return str;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Admin can view all. Non-admin can view only projects where they exist in members list. */
    public boolean canViewProject(AppState appState, Project p) {
        if (p == null) return false;
        if (isAdmin(appState)) return true;

        String myId = memberId(appState);
        if (myId == null) return false;

        for (Member m : p.getMembers()) {
            if (m == null) continue;
            if (Objects.equals(myId, safeId(m))) return true;
        }
        return false;
    }

    // ---------------- Projects ----------------

    public boolean canCreateProject(AppState appState) {
        return isAdmin(appState);
    }

    public boolean canDeleteProject(AppState appState) {
        return isAdmin(appState);
    }

    public boolean canMarkProjectDone(AppState appState) {
        if (isAdmin(appState)) return true;
        return appState != null && appState.getCurrentProjectRole() == ProjectRole.LEADER;
    }

    public boolean canEditProjectOverview(AppState appState) {
        if (isAdmin(appState)) return true;
        ProjectRole r = (appState == null) ? null : appState.getCurrentProjectRole();
        return r == ProjectRole.LEADER;
    }

    public boolean canManageTeam(AppState appState) {
        if (isAdmin(appState)) return true;
        ProjectRole r = (appState == null) ? null : appState.getCurrentProjectRole();
        return r == ProjectRole.LEADER;
    }

    // ---------------- Tasks ----------------

    public boolean canSeeAllProjectTasks(AppState appState) {
        if (isAdmin(appState)) return true;
        ProjectRole r = (appState == null) ? null : appState.getCurrentProjectRole();
        return r == ProjectRole.LEADER || r == ProjectRole.ADMIN;
    }

    public boolean canCreateTasks(AppState appState) {
        if (isAdmin(appState)) return true;
        ProjectRole r = (appState == null) ? null : appState.getCurrentProjectRole();
        return r == ProjectRole.LEADER;
    }

    /** Admin/Leader/AdminRole: edit any task. Member: edit only tasks assigned to them. Viewer: none. */
    public boolean canEditTask(AppState appState, Task t) {
        if (t == null) return false;
        if (isAdmin(appState)) return true;

        ProjectRole r = (appState == null) ? null : appState.getCurrentProjectRole();
        if (r == ProjectRole.LEADER || r == ProjectRole.ADMIN) return true;

        if (r == ProjectRole.MEMBER) return isAssignedToMe(appState, t);
        return false;
    }

    /** Only Admin/Leader/AdminRole can change title/priority/phase/assignee (task “meta”). */
    public boolean canEditTaskMeta(AppState appState) {
        if (isAdmin(appState)) return true;
        ProjectRole r = (appState == null) ? null : appState.getCurrentProjectRole();
        return r == ProjectRole.LEADER || r == ProjectRole.ADMIN;
    }

    public boolean canReassignTasks(AppState appState) {
        return canEditTaskMeta(appState);
    }

    public boolean isAssignedToMe(AppState appState, Task t) {
        if (t == null) return false;

        String myId = memberId(appState);
        if (myId == null) return false;

        Member a = t.getAssignee();
        if (a == null) return false;

        return Objects.equals(myId, safeId(a));
    }

    private static String safeId(Member m) {
        try {
            String id = m.getId();
            if (id == null) return null;
            String v = id.trim();
            return v.isEmpty() ? null : v;
        } catch (Exception e) {
            return null;
        }
    }
}
