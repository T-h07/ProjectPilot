package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.TeamService;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.lang.reflect.Method;
import java.util.*;
import com.projectpilot.security.AccessPolicy;

public class TeamPage extends VBox {

    private final InMemoryStore store;
    private final AppState appState;

    private final Label header = new Label("Team");
    private final Label sub = new Label("");
    private final Label myTeams = new Label("");

    // Directory: add from global users (admin-created)
    private final TextField directorySearch = new TextField();
    private final ComboBox<Member> directoryBox = new ComboBox<>();
    private final Button addFromDirectoryBtn = new Button("Add to project");
    private final Button refreshBtn = new Button("Refresh");

    // Teams
    private final ComboBox<TeamService.TeamRow> teamBox = new ComboBox<>();
    private final Button assignTeamBtn = new Button("Assign team");
    private final Label teamStatus = new Label();

    private final ListView<Member> membersList = new ListView<>();

    private final Label selectedName = new Label("-");
    private final Label selectedRole = new Label("-");
    private final Label openTasksLabel = new Label("-");
    private final Button removeBtn = new Button("Remove member");
    private final AccessPolicy policy = new AccessPolicy();

    private final BooleanBinding canEdit;

    private final ObservableList<Member> directorySource = FXCollections.observableArrayList();
    private final FilteredList<Member> directoryFiltered = new FilteredList<>(directorySource, m -> true);
    private final ObservableList<TeamService.TeamRow> teamsSource = FXCollections.observableArrayList();

    private String dirQuery = "";

    public TeamPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        this.canEdit = Bindings.createBooleanBinding(
                () -> policy.canManageTeam(appState),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );

        setPadding(new Insets(16));
        setSpacing(14);

        header.getStyleClass().add("page-title");
        sub.getStyleClass().add("muted");
        myTeams.getStyleClass().add("muted");

        directorySearch.setPromptText("Search users...");
        directorySearch.setPrefWidth(280);
        directorySearch.textProperty().addListener((obs, ov, nv) -> {
            dirQuery = (nv == null) ? "" : nv.trim().toLowerCase();
            updateDirectoryPredicate();
        });

        directoryBox.setPrefWidth(320);
        directoryBox.setPromptText("Select existing user...");
        directoryBox.setItems(directoryFiltered);
        directoryBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (item.getName() + " • " + safeRole(item)));
            }
        });
        directoryBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select existing user..." : (item.getName() + " • " + safeRole(item)));
            }
        });

        teamBox.setPrefWidth(320);
        teamBox.setPromptText("Select team...");
        teamBox.setItems(teamsSource);
        teamBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(TeamService.TeamRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String leader = (item.leaderName() == null || item.leaderName().isBlank()) ? "-" : item.leaderName();
                setText(item.name() + " - leader: " + leader + " - members: " + item.memberCount());
            }
        });
        teamBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(TeamService.TeamRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText("Select team..."); return; }
                setText(item.name());
            }
        });

        assignTeamBtn.getStyleClass().add("primary");
        teamStatus.getStyleClass().add("muted");

        addFromDirectoryBtn.getStyleClass().add("primary");
        refreshBtn.getStyleClass().add("secondary");

        // Admin-only actions
        directorySearch.disableProperty().bind(canEdit.not());
        directoryBox.disableProperty().bind(canEdit.not());
        addFromDirectoryBtn.disableProperty().bind(canEdit.not());
        teamBox.disableProperty().bind(canEdit.not());
        assignTeamBtn.disableProperty().bind(canEdit.not());
        removeBtn.disableProperty().bind(canEdit.not());

        Label existingLbl = new Label("Add existing admin-created user to project");
        existingLbl.getStyleClass().add("section-title");

        HBox addExistingRow = new HBox(10, directorySearch, directoryBox, addFromDirectoryBtn, refreshBtn);
        addExistingRow.setAlignment(Pos.CENTER_LEFT);

        Label teamLbl = new Label("Assign existing team to project");
        teamLbl.getStyleClass().add("section-title");

        HBox addTeamRow = new HBox(10, teamBox, assignTeamBtn);
        addTeamRow.setAlignment(Pos.CENTER_LEFT);

        VBox topCard = new VBox(10, header, sub, myTeams, existingLbl, addExistingRow, teamLbl, addTeamRow, teamStatus);
        topCard.getStyleClass().add("card");
        topCard.setPadding(new Insets(16));

        // Members list
        Label membersTitle = new Label("Members");
        membersTitle.getStyleClass().add("section-title");

        membersList.setPrefWidth(360);
        membersList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }

                Project p = appState.getSelectedProject();
                long open = (p == null) ? 0 : openTasksFor(p, item);
                setText(item.getName() + "  •  " + safeRole(item) + "  •  open: " + open);
            }
        });

        VBox membersCard = new VBox(10, membersTitle, membersList);
        membersCard.getStyleClass().add("card");
        membersCard.setPadding(new Insets(12));
        VBox.setVgrow(membersList, Priority.ALWAYS);

        // Details
        Label detailsTitle = new Label("Member Details");
        detailsTitle.getStyleClass().add("section-title");

        selectedName.getStyleClass().add("panel-title");
        selectedRole.getStyleClass().add("muted");

        removeBtn.getStyleClass().add("secondary");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);

        form.add(new Label("Name"), 0, 0);
        form.add(selectedName, 1, 0);

        form.add(new Label("Role"), 0, 1);
        form.add(selectedRole, 1, 1);

        form.add(new Label("Open tasks"), 0, 2);
        form.add(openTasksLabel, 1, 2);

        VBox detailsCard = new VBox(10, detailsTitle, form, new HBox(10, removeBtn));
        detailsCard.getStyleClass().add("card");
        detailsCard.setPadding(new Insets(12));
        detailsCard.setMinWidth(420);

        HBox bottom = new HBox(14, membersCard, detailsCard);
        HBox.setHgrow(membersCard, Priority.ALWAYS);
        HBox.setHgrow(detailsCard, Priority.ALWAYS);
        VBox.setVgrow(bottom, Priority.ALWAYS);

        getChildren().addAll(topCard, bottom);
        VBox.setVgrow(bottom, Priority.ALWAYS);

        // Actions
        addFromDirectoryBtn.setOnAction(e -> addExistingToProject());
        refreshBtn.setOnAction(e -> {
            loadDirectory();
            loadTeams();
            refresh(appState.getSelectedProject());
        });
        assignTeamBtn.setOnAction(e -> assignSelectedTeam());

        membersList.getSelectionModel().selectedItemProperty().addListener((obs, oldM, newM) -> showMemberDetails(newM));
        removeBtn.setOnAction(e -> removeSelectedMember());

        // Wiring
        appState.selectedProjectProperty().addListener((obs, o, n) -> refresh(n));
        store.getProjects().addListener((ListChangeListener<Project>) c -> refresh(appState.getSelectedProject()));

        // Initial
        loadDirectory();
        loadTeams();
        refresh(appState.getSelectedProject());
    }

    private void loadDirectory() {
        directorySource.clear();

        // Prefer DB-backed directory method
        int loaded = 0;

        for (String mName : List.of("listDirectoryUsers", "listMembersDirectory", "listUsers")) {
            try {
                Method m = store.getClass().getMethod(mName);
                Object res = m.invoke(store);
                loaded = addMembersFromUnknownIterable(res);
                if (loaded > 0) break;
            } catch (Exception ignored) {}
        }

        updateDirectoryPredicate();
        System.out.println("[TeamPage] directory loaded = " + loaded);
    }

    private void loadTeams() {
        teamsSource.clear();
        teamStatus.setText("");

        int loaded = 0;
        try {
            for (String name : List.of("listTeams")) {
                try {
                    Method m = store.getClass().getMethod(name);
                    Object res = m.invoke(store);
                    loaded = addTeamsFromUnknownIterable(res);
                    if (loaded > 0) break;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            teamStatus.setText("Failed to load teams: " + e.getMessage());
        }
    }

    private int addMembersFromUnknownIterable(Object obj) {
        if (obj == null) return 0;

        Iterable<?> it;
        if (obj instanceof Iterable<?> iterable) it = iterable;
        else if (obj.getClass().isArray()) {
            List<Object> tmp = new ArrayList<>();
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) tmp.add(java.lang.reflect.Array.get(obj, i));
            it = tmp;
        } else return 0;

        int c = 0;
        for (Object o : it) {
            if (o instanceof Member m) {
                directorySource.add(m);
                c++;
            }
        }
        return c;
    }

    private int addTeamsFromUnknownIterable(Object obj) {
        if (obj == null) return 0;

        Iterable<?> it;
        if (obj instanceof Iterable<?> iterable) it = iterable;
        else if (obj.getClass().isArray()) {
            List<Object> tmp = new ArrayList<>();
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) tmp.add(java.lang.reflect.Array.get(obj, i));
            it = tmp;
        } else return 0;

        int c = 0;
        for (Object o : it) {
            if (o instanceof TeamService.TeamRow row) {
                teamsSource.add(row);
                c++;
            }
        }
        return c;
    }

        private void updateDirectoryPredicate() {
        Project p = appState.getSelectedProject();

        directoryFiltered.setPredicate(m -> {
            if (m == null) return false;

            if (dirQuery != null && !dirQuery.isBlank()) {
                String n = (m.getName() == null) ? "" : m.getName().toLowerCase();
                if (!n.contains(dirQuery)) return false;
            }

            // hide users already in project
            if (p != null && p.getMembers() != null) {
                String mid = safeId(m);
                for (Member pm : p.getMembers()) {
                    if (pm == null) continue;
                    if (mid != null && mid.equals(safeId(pm))) return false;
                }
            }

            return true;
        });
    }

    private void updateMyTeams(Project p) {
        myTeams.setText("");
        if (p == null) return;

        String myId = null;
        try {
            var s = appState.getSession();
            myId = (s == null) ? null : s.id();
        } catch (Exception ignored) {}

        if (myId == null || myId.isBlank()) return;

        try {
            List<String> names = invokeListTeamNamesForMemberInProject(myId, p.getId());
            if (names.isEmpty()) {
                myTeams.setText("Your team: -");
            } else if (names.size() == 1) {
                myTeams.setText("Your team: " + names.get(0));
            } else {
                myTeams.setText("Your teams: " + String.join(", ", names));
            }
        } catch (Exception e) {
            myTeams.setText("Your team: -");
        }
    }

    private void refresh(Project p) {
        if (p == null) {
            sub.setText("No project selected");
            myTeams.setText("");
            membersList.setItems(null);
            membersList.getSelectionModel().clearSelection();
            showMemberDetails(null);
            updateDirectoryPredicate();
            return;
        }

        sub.setText(p.getName() + "  •  members (admin-assigned roles)");
        membersList.setItems(p.getMembers());
        membersList.refresh();

        updateMyTeams(p);
        updateDirectoryPredicate();

        if (!p.getMembers().isEmpty() && membersList.getSelectionModel().getSelectedItem() == null) {
            membersList.getSelectionModel().selectFirst();
        } else {
            showMemberDetails(membersList.getSelectionModel().getSelectedItem());
        }
    }

    private void addExistingToProject() {
        if (!canEdit.get()) return;

        Project p = appState.getSelectedProject();
        if (p == null) return;

        Member base = directoryBox.getValue();
        if (base == null) {
            alertInfo("No selection", "Pick an existing user first.");
            return;
        }

        String baseId = safeId(base);

        boolean exists = p.getMembers().stream().anyMatch(m ->
                baseId != null && baseId.equals(safeId(m))
        );
        if (exists) {
            alertInfo("Already added", "That user is already a member of this project.");
            return;
        }

        // IMPORTANT: role comes from admin-created user (members.role)
        Member m = new Member(base.getId(), base.getName(), base.getRole());
        store.addMember(p, m);

        membersList.getSelectionModel().select(m);
        membersList.refresh();

        directoryBox.getSelectionModel().clearSelection();
        updateDirectoryPredicate();
    }

    private void assignSelectedTeam() {
        if (!canEdit.get()) return;

        Project p = appState.getSelectedProject();
        if (p == null) {
            alertInfo("No project selected", "Select a project first.");
            return;
        }

        TeamService.TeamRow team = teamBox.getValue();
        if (team == null) {
            alertInfo("No team selected", "Pick a team to assign.");
            return;
        }

        try {
            if (!invokeAssignTeam(team.id(), p.getId())) {
                alertInfo("Not available", "Teams are unavailable for this connection.");
                return;
            }

            List<TeamService.TeamMemberRow> members = invokeListTeamMembers(team.id());
            if (members.isEmpty()) {
                teamStatus.setText("Assigned team: " + team.name());
                teamBox.getSelectionModel().clearSelection();
                return;
            }

            for (TeamService.TeamMemberRow row : members) {
                if (row == null || row.memberId() == null || row.memberId().isBlank()) continue;
                ProjectRole role = row.role() == null ? ProjectRole.MEMBER : row.role();
                String name = (row.name() == null || row.name().isBlank()) ? "User" : row.name();

                Member existing = p.getMembers().stream()
                        .filter(m -> row.memberId().equals(safeId(m)))
                        .findFirst()
                        .orElse(null);

                if (existing == null) {
                    store.addMember(p, new Member(row.memberId(), name, role));
                } else {
                    if (existing.getName() == null || existing.getName().isBlank()) {
                        existing.nameProperty().set(name);
                    }
                    existing.roleProperty().set(role);
                }
            }

            membersList.refresh();
            updateDirectoryPredicate();
            appState.refreshCurrentProjectRole();

            updateMyTeams(p);
            teamStatus.setText("Assigned team: " + team.name());
            teamBox.getSelectionModel().clearSelection();
        } catch (Exception ex) {
            teamStatus.setText("Failed to assign team: " + ex.getMessage());
        }
    }
    private boolean invokeAssignTeam(String teamId, String projectId) {
        for (String name : List.of("assignTeamToProject")) {
            try {
                Method m = store.getClass().getMethod(name, String.class, String.class);
                m.invoke(store, teamId, projectId);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private List<TeamService.TeamMemberRow> invokeListTeamMembers(String teamId) {
        for (String name : List.of("listTeamMembers")) {
            try {
                Method m = store.getClass().getMethod(name, String.class);
                Object res = m.invoke(store, teamId);
                return toTeamMemberRows(res);
            } catch (Exception ignored) {}
        }
        return List.of();
    }

    private List<String> invokeListTeamNamesForMemberInProject(String memberId, String projectId) {
        for (String name : List.of("listTeamNamesForMemberInProject")) {
            try {
                Method m = store.getClass().getMethod(name, String.class, String.class);
                Object res = m.invoke(store, memberId, projectId);
                if (res instanceof List<?> list) {
                    List<String> out = new ArrayList<>();
                    for (Object o : list) {
                        if (o instanceof String s && !s.isBlank()) out.add(s);
                    }
                    return out;
                }
            } catch (Exception ignored) {}
        }
        return List.of();
    }

    private List<TeamService.TeamMemberRow> toTeamMemberRows(Object obj) {
        if (obj == null) return List.of();
        Iterable<?> it;
        if (obj instanceof Iterable<?> iterable) it = iterable;
        else if (obj.getClass().isArray()) {
            List<Object> tmp = new ArrayList<>();
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) tmp.add(java.lang.reflect.Array.get(obj, i));
            it = tmp;
        } else return List.of();

        List<TeamService.TeamMemberRow> out = new ArrayList<>();
        for (Object o : it) {
            if (o instanceof TeamService.TeamMemberRow row) out.add(row);
        }
        return out;
    }
    private void removeSelectedMember() {
        if (!canEdit.get()) return;

        Project p = appState.getSelectedProject();
        Member m = membersList.getSelectionModel().getSelectedItem();
        if (p == null || m == null) return;

        long assignedOpen = openTasksFor(p, m);

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove member");
        confirm.setHeaderText("Remove " + m.getName() + "?");
        confirm.setContentText(
                assignedOpen > 0
                        ? ("This member has " + assignedOpen + " open task(s). They will be unassigned.")
                        : "This will remove the member from the project."
        );

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        String mid = safeId(m);
        for (Task t : p.getTasks()) {
            Member a = t.getAssignee();
            if (a == null) continue;
            if (mid != null && mid.equals(safeId(a))) t.setAssignee(null);
        }

        store.removeMember(p, m);
        appState.refreshCurrentProjectRole();


        membersList.getSelectionModel().clearSelection();
        membersList.refresh();

        updateDirectoryPredicate();


        if (!p.getMembers().isEmpty()) membersList.getSelectionModel().selectFirst();
        else showMemberDetails(null);
    }

    private void showMemberDetails(Member m) {
        Project p = appState.getSelectedProject();

        if (m == null || p == null) {
            selectedName.setText("-");
            selectedRole.setText("-");
            openTasksLabel.setText("-");
            return;
        }

        selectedName.setText(m.getName());
        selectedRole.setText(safeRole(m));
        openTasksLabel.setText(String.valueOf(openTasksFor(p, m)));
    }

    private long openTasksFor(Project p, Member m) {
        String mid = safeId(m);
        return p.getTasks().stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .filter(t -> {
                    Member a = t.getAssignee();
                    return a != null && mid != null && mid.equals(safeId(a));
                })
                .count();
    }

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(text);
        a.showAndWait();
    }

    private static String safeId(Member m) {
        try {
            String id = m.getId();
            return (id == null || id.isBlank()) ? null : id;
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeRole(Member m) {
        try {
            return (m.getRole() == null) ? "-" : m.getRole().name();
        } catch (Exception e) {
            return "-";
        }
    }
}





