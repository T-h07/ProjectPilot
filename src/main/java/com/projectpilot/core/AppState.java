package com.projectpilot.core;

import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserSession;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;


public class AppState {

    private final ObjectProperty<PageId> currentPage = new SimpleObjectProperty<>(PageId.DASHBOARD);
    private final ObjectProperty<Project> selectedProject = new SimpleObjectProperty<>();
    private final ObjectProperty<Task> selectedTask = new SimpleObjectProperty<>();


    // ✅ logged-in user session
    private final ObjectProperty<UserSession> session = new SimpleObjectProperty<>();

    public ObjectProperty<PageId> currentPageProperty() { return currentPage; }
    public PageId getCurrentPage() { return currentPage.get(); }
    public void setCurrentPage(PageId page) { currentPage.set(page); }

    public ObjectProperty<Project> selectedProjectProperty() { return selectedProject; }
    public Project getSelectedProject() { return selectedProject.get(); }
    public void setSelectedProject(Project p) { selectedProject.set(p); }

    public ObjectProperty<Task> selectedTaskProperty() { return selectedTask; }
    public Task getSelectedTask() { return selectedTask.get(); }
    public void setSelectedTask(Task t) { selectedTask.set(t); }

    public ObjectProperty<UserSession> sessionProperty() { return session; }
    public UserSession getSession() { return session.get(); }
    public void setSession(UserSession s) { session.set(s); }

    public boolean isAdmin() {
        UserSession s = getSession();
        return s != null && s.globalRole() == GlobalRole.ADMIN;
    }
}
