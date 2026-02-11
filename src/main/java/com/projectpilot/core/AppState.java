package com.projectpilot.core;

import com.projectpilot.chat.ChatThread;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAccount;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.util.OwnerProfile;
import com.projectpilot.util.UserSettingsStore;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.projectpilot.util.AppLog;

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

    private final IntegerProperty unreadMessages = new SimpleIntegerProperty(0);
    private final Map<String, Long> chatLastSeen = new ConcurrentHashMap<>();
    private final Map<String, Long> chatLastActivity = new ConcurrentHashMap<>();

    private final BooleanProperty hosting = new SimpleBooleanProperty(false);
    private final IntegerProperty hostPort = new SimpleIntegerProperty(0);
    private final IntegerProperty hostWsPort = new SimpleIntegerProperty(0);
    private final IntegerProperty hostConnections = new SimpleIntegerProperty(0);
    private final LongProperty hostStartedAt = new SimpleLongProperty(0L);
    private final StringProperty hostMode = new SimpleStringProperty("local");
    private final BooleanProperty clientOnline = new SimpleBooleanProperty(false);
    private final StringProperty clientStatus = new SimpleStringProperty("offline");
    private final StringProperty lanToken = new SimpleStringProperty("");
    private final StringProperty lanBaseUrl = new SimpleStringProperty("");

    private final StringProperty theme = new SimpleStringProperty("default");
    private final StringProperty density = new SimpleStringProperty("comfortable");
    private final StringProperty ownerMessageStyle = new SimpleStringProperty("none");

    private final ListChangeListener<Member> membersListener = c -> refreshCurrentProjectRole();
    private Project membersBoundProject;
    private final ListChangeListener<Task> tasksListener = c -> {
        while (c.next()) {
            if (c.wasRemoved()) {
                for (Task t : c.getRemoved()) {
                    try {
                        if (t != null && t == getSelectedTask()) {
                            setSelectedTask(null);
                            break;
                        }
                    } catch (Exception e) { AppLog.warn("appstate", "Failed clearing selected task: " + e.getMessage()); }
                }
            }
        }
    };
    private Project tasksBoundProject;

    private boolean refreshingRole = false;

    public AppState() {
        session.addListener((obs, o, n) -> {
            refreshCurrentProjectRole();
            resetChatState();
        });
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

    public IntegerProperty unreadMessagesProperty() { return unreadMessages; }
    public int getUnreadMessages() { return unreadMessages.get(); }

    public BooleanProperty hostingProperty() { return hosting; }
    public boolean isHosting() { return hosting.get(); }

    public IntegerProperty hostPortProperty() { return hostPort; }
    public int getHostPort() { return hostPort.get(); }

    public IntegerProperty hostWsPortProperty() { return hostWsPort; }
    public int getHostWsPort() { return hostWsPort.get(); }

    public IntegerProperty hostConnectionsProperty() { return hostConnections; }
    public int getHostConnections() { return hostConnections.get(); }

    public LongProperty hostStartedAtProperty() { return hostStartedAt; }
    public long getHostStartedAt() { return hostStartedAt.get(); }

    public StringProperty hostModeProperty() { return hostMode; }
    public String getHostMode() { return hostMode.get(); }

    public BooleanProperty clientOnlineProperty() { return clientOnline; }
    public boolean isClientOnline() { return clientOnline.get(); }

    public StringProperty clientStatusProperty() { return clientStatus; }
    public String getClientStatus() { return clientStatus.get(); }

    public StringProperty lanTokenProperty() { return lanToken; }
    public String getLanToken() { return lanToken.get(); }
    public void setLanToken(String token) { lanToken.set(token == null ? "" : token.trim()); }

    public StringProperty lanBaseUrlProperty() { return lanBaseUrl; }
    public String getLanBaseUrl() { return lanBaseUrl.get(); }
    public void setLanBaseUrl(String url) { lanBaseUrl.set(url == null ? "" : url.trim()); }

    public StringProperty themeProperty() { return theme; }
    public String getTheme() { return theme.get(); }
    public void setTheme(String value) { theme.set(value == null ? "default" : value.trim()); }

    public StringProperty densityProperty() { return density; }
    public String getDensity() { return density.get(); }
    public void setDensity(String value) { density.set(value == null ? "comfortable" : value.trim()); }

    public StringProperty ownerMessageStyleProperty() { return ownerMessageStyle; }
    public String getOwnerMessageStyle() { return ownerMessageStyle.get(); }
    public void setOwnerMessageStyle(String value) { ownerMessageStyle.set(value == null ? "none" : value.trim()); }

    public void applySettings(UserSettingsStore.Settings settings) {
        if (settings == null) return;
        setTheme(settings.theme());
        setDensity(settings.density());
        setOwnerMessageStyle(settings.ownerMessageStyle());
    }

    public boolean isOwnerUser() {
        return OwnerProfile.isOwnerUser(getSession());
    }

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
                try { membersBoundProject.getTasks().removeListener(tasksListener); } catch (Exception e) { AppLog.warn("appstate", "Failed removing tasks listener: " + e.getMessage()); }
            }
        } catch (Exception e) { AppLog.warn("appstate", "Failed unbinding members listener: " + e.getMessage()); }

        membersBoundProject = newP;
        tasksBoundProject = newP;

        try {
            if (newP != null) {
                newP.getMembers().addListener(membersListener);
                try { newP.getTasks().addListener(tasksListener); } catch (Exception e) { AppLog.warn("appstate", "Failed adding tasks listener: " + e.getMessage()); }
            }
        } catch (Exception e) { AppLog.warn("appstate", "Failed binding members listener: " + e.getMessage()); }
    }

    public void updateChatThreads(List<ChatThread> threads) {
        chatLastActivity.clear();
        if (threads != null) {
            for (ChatThread t : threads) {
                if (t == null) continue;
                String id = safe(t.id());
                if (id == null) continue;
                long lastAt = t.lastAt() == null ? 0L : t.lastAt();
                chatLastActivity.put(id, lastAt);
            }
        }
        recomputeUnread();
    }

    public boolean isChatThreadUnread(String threadId) {
        String id = safe(threadId);
        if (id == null) return false;
        long lastAt = chatLastActivity.getOrDefault(id, 0L);
        if (lastAt <= 0) return false;
        long seen = chatLastSeen.getOrDefault(id, 0L);
        return lastAt > seen;
    }

    public void markChatThreadSeen(String threadId, Long lastAt) {
        String id = safe(threadId);
        if (id == null) return;
        Long seen = chatLastSeen.get(id);
        long candidate = lastAt == null ? chatLastActivity.getOrDefault(id, 0L) : lastAt;
        if (seen == null || candidate > seen) {
            chatLastSeen.put(id, candidate);
        }
        recomputeUnread();
    }

    private void recomputeUnread() {
        int count = 0;
        for (Map.Entry<String, Long> entry : chatLastActivity.entrySet()) {
            long lastAt = entry.getValue() == null ? 0L : entry.getValue();
            if (lastAt <= 0) continue;
            long seen = chatLastSeen.getOrDefault(entry.getKey(), 0L);
            if (lastAt > seen) count++;
        }
        setUnreadMessages(count);
    }

    private void setUnreadMessages(int count) {
        if (Objects.equals(unreadMessages.get(), count)) return;
        if (Platform.isFxApplicationThread()) unreadMessages.set(count);
        else Platform.runLater(() -> unreadMessages.set(count));
    }

    public void updateHostingStatus(boolean isHosting, int port, int wsPort, long startedAt, int connections, String mode) {
        Runnable update = () -> {
            hosting.set(isHosting);
            hostPort.set(Math.max(0, port));
            hostWsPort.set(Math.max(0, wsPort));
            hostStartedAt.set(isHosting ? Math.max(0L, startedAt) : 0L);
            hostConnections.set(Math.max(0, connections));
            hostMode.set(mode == null || mode.isBlank() ? "local" : mode);
        };
        if (Platform.isFxApplicationThread()) update.run();
        else Platform.runLater(update);
    }

    public void setHostConnections(int connections) {
        int safe = Math.max(0, connections);
        if (Platform.isFxApplicationThread()) hostConnections.set(safe);
        else Platform.runLater(() -> hostConnections.set(safe));
    }

    public void setClientStatus(boolean online, String status) {
        String msg = status == null ? "" : status.trim();
        Runnable update = () -> {
            clientOnline.set(online);
            clientStatus.set(msg.isEmpty() ? (online ? "online" : "offline") : msg);
        };
        if (Platform.isFxApplicationThread()) update.run();
        else Platform.runLater(update);
    }

    private void resetChatState() {
        chatLastSeen.clear();
        chatLastActivity.clear();
        setUnreadMessages(0);
    }

    private static String safe(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }
}
