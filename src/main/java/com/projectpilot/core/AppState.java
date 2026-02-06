package com.projectpilot.core;

import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAccount;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.ProjectRole;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

public class AppState {

    private final ObjectProperty<PageId> currentPage = new SimpleObjectProperty<>(PageId.DASHBOARD);
    private final ObjectProperty<Project> selectedProject = new SimpleObjectProperty<>();
    private final ObjectProperty<Task> selectedTask = new SimpleObjectProperty<>();

    private final ObjectProperty<UserSession> session = new SimpleObjectProperty<>();

    // derived from selectedProject.members + session.id (admins forced to ADMIN)
    private final ObjectProperty<ProjectRole> currentProjectRole = new SimpleObjectProperty<>();

    private final ObservableList<UserAccount> globalUsers = FXCollections.observableArrayList();
    public ObservableList<UserAccount> getGlobalUsers() { return globalUsers; }
    public void setGlobalUsers(java.util.List<UserAccount> users) { globalUsers.setAll(users); }

    private final ListChangeListener<Member> membersListener = c -> refreshCurrentProjectRole();
    private Project membersBoundProject;

    private boolean refreshingRole = false;

    public AppState() {
        session.addListener((obs, o, n) -> refreshCurrentProjectRole());
        selectedProject.addListener((obs, o, n) -> {
            rebindMembersListener(o, n);
            refreshCurrentProjectRole();
        });
    }

    public ObjectProperty<PageId> currentPageProperty() { return currentPage; }
    public PageId getCurrentPage() { return currentPage.get(); }
    public void setCurrentPage(PageId page) { currentPage.set(page); }

    public ObjectProperty<Project> selectedProjectProperty() { return selectedProject; }
    public Project getSelectedProject() { return selectedProject.get(); }

    /** Guard: non-admins can’t select a project they are not assigned to. */
    public void setSelectedProject(Project p) {
        // short-circuit prevents noisy re-entrant selection churn
        if (p == selectedProject.get()) return;
        if (p == null) {
            if (selectedProject.get() == null) return;
            selectedProject.set(null);
            return;
        }

        if (!isAdmin()) {
            UserSession s = getSession();
            String myId = safe(s == null ? null : s.id());
            if (myId == null) return;

            boolean ok = p.getMembers().stream().anyMatch(m -> m != null && myId.equals(safe(m.getId())));
            if (!ok) return; // ignore
        }

        selectedProject.set(p);
    }

    public ObjectProperty<Task> selectedTaskProperty() { return selectedTask; }
    public Task getSelectedTask() { return selectedTask.get(); }
    public void setSelectedTask(Task t) { selectedTask.set(t); }

    public ObjectProperty<UserSession> sessionProperty() { return session; }
    public UserSession getSession() { return session.get(); }
    public void setSession(UserSession s) { session.set(s); }

    public ObjectProperty<ProjectRole> currentProjectRoleProperty() { return currentProjectRole; }
    public ProjectRole getCurrentProjectRole() { return currentProjectRole.get(); }

    public boolean isAdmin() {
        UserSession s = getSession();
        return s != null && s.globalRole() == GlobalRole.ADMIN;
    }

    public void refreshCurrentProjectRole() {
        if (refreshingRole) return; // prevents rare re-entrant loops
        refreshingRole = true;
        try {
            if (isAdmin()) {
                currentProjectRole.set(ProjectRole.ADMIN);
                return;
            }

            UserSession s = getSession();
            Project p = getSelectedProject();
            if (s == null || p == null) {
                currentProjectRole.set(null);
                return;
            }

            String myId = safe(s.id());
            if (myId == null) {
                currentProjectRole.set(null);
                return;
            }

            ProjectRole found = null;
            for (Member m : p.getMembers()) {
                if (m == null) continue;
                if (myId.equals(safe(m.getId()))) {
                    found = m.getRole();
                    break;
                }
            }

            currentProjectRole.set(found);

            // If you were removed from the project, kick you out of it.
            if (found == null) {
                setSelectedProject(null);
            }
        } finally {
            refreshingRole = false;
        }
    }

    private void rebindMembersListener(Project oldP, Project newP) {
        try {
            if (membersBoundProject != null) {
                membersBoundProject.getMembers().removeListener(membersListener);
            }
        } catch (Exception ignored) {}

        membersBoundProject = newP;

        try {
            if (newP != null) {
                newP.getMembers().addListener(membersListener);
            }
        } catch (Exception ignored) {}
    }

    private static String safe(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }
}
