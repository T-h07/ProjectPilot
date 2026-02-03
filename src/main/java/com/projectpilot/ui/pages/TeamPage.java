package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
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

public class TeamPage extends VBox {

    private final InMemoryStore store;
    private final AppState appState;

    private final Label header = new Label("Team");
    private final Label sub = new Label("");

    // Directory: add from global users (admin-created)
    private final TextField directorySearch = new TextField();
    private final ComboBox<Member> directoryBox = new ComboBox<>();
    private final Button addFromDirectoryBtn = new Button("Add to project");
    private final Button refreshBtn = new Button("Refresh");

    private final ListView<Member> membersList = new ListView<>();

    private final Label selectedName = new Label("-");
    private final Label selectedRole = new Label("-");
    private final Label openTasksLabel = new Label("-");
    private final Button removeBtn = new Button("Remove member");

    private final BooleanBinding canEdit;

    private final ObservableList<Member> directorySource = FXCollections.observableArrayList();
    private final FilteredList<Member> directoryFiltered = new FilteredList<>(directorySource, m -> true);

    private String dirQuery = "";

    public TeamPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        this.canEdit = Bindings.createBooleanBinding(
                () -> appState.sessionProperty().get() != null && appState.isAdmin(),
                appState.sessionProperty()
        );

        setPadding(new Insets(16));
        setSpacing(14);

        header.getStyleClass().add("page-title");
        sub.getStyleClass().add("muted");

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

        addFromDirectoryBtn.getStyleClass().add("primary");
        refreshBtn.getStyleClass().add("secondary");

        // Admin-only actions
        directorySearch.disableProperty().bind(canEdit.not());
        directoryBox.disableProperty().bind(canEdit.not());
        addFromDirectoryBtn.disableProperty().bind(canEdit.not());
        removeBtn.disableProperty().bind(canEdit.not());

        Label existingLbl = new Label("Add existing admin-created user to project");
        existingLbl.getStyleClass().add("section-title");

        HBox addExistingRow = new HBox(10, directorySearch, directoryBox, addFromDirectoryBtn, refreshBtn);
        addExistingRow.setAlignment(Pos.CENTER_LEFT);

        VBox topCard = new VBox(10, header, sub, existingLbl, addExistingRow);
        topCard.getStyleClass().add("card");
        topCard.setPadding(new Insets(14));

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

        selectedName.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");
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
            refresh(appState.getSelectedProject());
        });

        membersList.getSelectionModel().selectedItemProperty().addListener((obs, oldM, newM) -> showMemberDetails(newM));
        removeBtn.setOnAction(e -> removeSelectedMember());

        // Wiring
        appState.selectedProjectProperty().addListener((obs, o, n) -> refresh(n));
        store.getProjects().addListener((ListChangeListener<Project>) c -> refresh(appState.getSelectedProject()));

        // Initial
        loadDirectory();
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

    private void refresh(Project p) {
        if (p == null) {
            sub.setText("No project selected");
            membersList.setItems(null);
            membersList.getSelectionModel().clearSelection();
            showMemberDetails(null);
            updateDirectoryPredicate();
            return;
        }

        sub.setText(p.getName() + "  •  members (admin-assigned roles)");
        membersList.setItems(p.getMembers());
        membersList.refresh();

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
