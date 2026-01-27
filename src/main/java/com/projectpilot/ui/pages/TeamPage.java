package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Objects;

public class TeamPage extends VBox {

    private final InMemoryStore store;
    private final AppState appState;

    private final Label header = new Label("Team");
    private final Label sub = new Label("");

    private final TextField nameField = new TextField();
    private final ComboBox<ProjectRole> roleBox = new ComboBox<>();
    private final Button addBtn = new Button("Add member");
    private final Button refreshBtn = new Button("Refresh");

    private final ListView<Member> membersList = new ListView<>();

    private final Label selectedName = new Label("-");
    private final ComboBox<ProjectRole> editRoleBox = new ComboBox<>();
    private final Label openTasksLabel = new Label("-");
    private final Button removeBtn = new Button("Remove member");

    private final BooleanBinding canEdit;

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

        nameField.setPromptText("Member name…");
        nameField.setPrefWidth(280);

        roleBox.getItems().setAll(ProjectRole.values());
        roleBox.getSelectionModel().selectFirst();
        roleBox.setPrefWidth(180);

        addBtn.getStyleClass().add("primary");
        refreshBtn.getStyleClass().add("secondary");

        // Workers: read-only (disable inputs + buttons)
        nameField.disableProperty().bind(canEdit.not());
        roleBox.disableProperty().bind(canEdit.not());
        addBtn.disableProperty().bind(canEdit.not());
        removeBtn.disableProperty().bind(canEdit.not());
        editRoleBox.disableProperty().bind(canEdit.not());

        HBox addRow = new HBox(10, nameField, roleBox, addBtn, refreshBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);

        VBox topCard = new VBox(10, header, sub, addRow);
        topCard.getStyleClass().add("card");
        topCard.setPadding(new Insets(14));

        Label membersTitle = new Label("Members");
        membersTitle.getStyleClass().add("section-title");

        membersList.setPrefWidth(360);
        membersList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                Project p = appState.getSelectedProject();
                long open = (p == null) ? 0 : openTasksFor(p, item);
                String role = (item.getRole() == null) ? "-" : item.getRole().toString();
                setText(item.getName() + "  •  " + role + "  •  open: " + open);
            }
        });

        VBox membersCard = new VBox(10, membersTitle, membersList);
        membersCard.getStyleClass().add("card");
        membersCard.setPadding(new Insets(12));
        VBox.setVgrow(membersList, Priority.ALWAYS);

        Label detailsTitle = new Label("Member Details");
        detailsTitle.getStyleClass().add("section-title");

        selectedName.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");

        editRoleBox.getItems().setAll(ProjectRole.values());
        editRoleBox.setPrefWidth(220);

        removeBtn.getStyleClass().add("secondary");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);

        form.add(new Label("Name"), 0, 0);
        form.add(selectedName, 1, 0);

        form.add(new Label("Role"), 0, 1);
        form.add(editRoleBox, 1, 1);

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

        addBtn.setOnAction(e -> addMember());
        refreshBtn.setOnAction(e -> refresh(appState.getSelectedProject()));

        membersList.getSelectionModel().selectedItemProperty().addListener((obs, oldM, newM) -> showMemberDetails(newM));

        editRoleBox.setOnAction(e -> {
            if (!canEdit.get()) return;

            Member m = membersList.getSelectionModel().getSelectedItem();
            if (m == null) return;

            ProjectRole role = editRoleBox.getValue();
            if (role == null) return;

            m.setRole(role);
            membersList.refresh();
        });

        removeBtn.setOnAction(e -> removeSelectedMember());

        appState.selectedProjectProperty().addListener((obs, o, n) -> refresh(n));

        store.getProjects().addListener((ListChangeListener<Project>) c -> refresh(appState.getSelectedProject()));

        appState.selectedProjectProperty().addListener((obs, oldP, newP) -> {
            if (oldP != null) oldP.getTasks().removeListener(tasksListener);
            if (newP != null) newP.getTasks().addListener(tasksListener);
        });
        if (appState.getSelectedProject() != null) {
            appState.getSelectedProject().getTasks().addListener(tasksListener);
        }

        refresh(appState.getSelectedProject());
    }

    private final ListChangeListener<Task> tasksListener = c -> {
        membersList.refresh();
        Member m = membersList.getSelectionModel().getSelectedItem();
        if (m != null) showMemberDetails(m);
    };

    private void refresh(Project p) {
        if (p == null) {
            sub.setText("No project selected");
            membersList.setItems(null);
            membersList.getSelectionModel().clearSelection();
            showMemberDetails(null);
            refreshBtn.setDisable(true);
            return;
        }

        sub.setText(p.getName() + "  •  manage members and roles");
        refreshBtn.setDisable(false);

        membersList.setItems(p.getMembers());
        membersList.refresh();

        if (!p.getMembers().isEmpty() && membersList.getSelectionModel().getSelectedItem() == null) {
            membersList.getSelectionModel().selectFirst();
        } else {
            showMemberDetails(membersList.getSelectionModel().getSelectedItem());
        }
    }

    private void addMember() {
        if (!canEdit.get()) return;

        Project p = appState.getSelectedProject();
        if (p == null) return;

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            alertInfo("Missing name", "Enter a member name.");
            return;
        }

        ProjectRole role = roleBox.getValue();
        if (role == null) role = ProjectRole.MEMBER;

        boolean exists = p.getMembers().stream().anyMatch(m -> name.equalsIgnoreCase(m.getName()));
        if (exists) {
            alertInfo("Already exists", "A member with that name already exists.");
            return;
        }

        Member m = new Member(name, role);
        store.addMember(p, m);

        nameField.clear();
        membersList.getSelectionModel().select(m);
        membersList.refresh();
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

        for (Task t : p.getTasks()) {
            if (Objects.equals(t.getAssignee(), m)) {
                t.setAssignee(null);
            }
        }

        store.removeMember(p, m);

        membersList.getSelectionModel().clearSelection();
        membersList.refresh();

        if (!p.getMembers().isEmpty()) {
            membersList.getSelectionModel().selectFirst();
        } else {
            showMemberDetails(null);
        }
    }

    private void showMemberDetails(Member m) {
        Project p = appState.getSelectedProject();

        if (m == null || p == null) {
            selectedName.setText("-");
            editRoleBox.getSelectionModel().clearSelection();
            openTasksLabel.setText("-");
            return;
        }

        selectedName.setText(m.getName());

        if (m.getRole() != null) editRoleBox.getSelectionModel().select(m.getRole());
        else editRoleBox.getSelectionModel().selectFirst();

        openTasksLabel.setText(String.valueOf(openTasksFor(p, m)));
    }

    private long openTasksFor(Project p, Member m) {
        return p.getTasks().stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .filter(t -> Objects.equals(t.getAssignee(), m))
                .count();
    }

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(text);
        a.showAndWait();
    }
}
