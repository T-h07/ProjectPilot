package com.projectpilot.ui.pages.admin;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.ui.dialogs.EditUserDialog;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Objects;

public final class AdminPage extends BorderPane {

    private final UserAdminService users;
    private final ObservableList<UserAdminService.UserRow> items = FXCollections.observableArrayList();

    private final InMemoryStore store;
    private final AppState appState;

    private final Label status = new Label();

    public AdminPage(DbManager db, InMemoryStore store, AppState appState) {
        this.users = new UserAdminService(db);
        this.store = Objects.requireNonNull(store);
        this.appState = Objects.requireNonNull(appState);

        setPadding(new Insets(16));

        var title = new Label("Admin");
        title.getStyleClass().add("pp-h1");

        var createBox = buildCreateUserBox();
        var table = buildUsersTable();

        var top = new VBox(10, title, status);
        setTop(top);

        var center = new VBox(14, createBox, table);
        center.setFillWidth(true);
        setCenter(center);

        reload();
    }

    private Node buildCreateUserBox() {
        TextField name = new TextField();
        name.setPromptText("Display name (optional)");

        TextField username = new TextField();
        username.setPromptText("Username");

        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        ComboBox<GlobalRole> globalRole = new ComboBox<>();
        globalRole.getItems().setAll(GlobalRole.USER, GlobalRole.ADMIN);
        globalRole.setValue(GlobalRole.USER);

        ComboBox<ProjectRole> projectRole = new ComboBox<>();
        projectRole.getItems().setAll(ProjectRole.LEADER, ProjectRole.MEMBER, ProjectRole.VIEWER);
        projectRole.setValue(ProjectRole.MEMBER);

        Label projectRoleLbl = new Label("Project role");
        var showProjectRole = Bindings.createBooleanBinding(
                () -> globalRole.getValue() == GlobalRole.USER,
                globalRole.valueProperty()
        );

        projectRoleLbl.visibleProperty().bind(showProjectRole);
        projectRoleLbl.managedProperty().bind(projectRoleLbl.visibleProperty());
        projectRole.visibleProperty().bind(showProjectRole);
        projectRole.managedProperty().bind(projectRole.visibleProperty());

        Label hint = new Label();
        hint.getStyleClass().add("muted");
        hint.textProperty().bind(Bindings.createStringBinding(() -> {
            Project p = appState.getSelectedProject();
            if (globalRole.getValue() != GlobalRole.USER) return "";
            if (p == null) return "No project selected — user will be created, but not added to a project.";
            return "User will be added to current project: " + p.getName();
        }, globalRole.valueProperty(), appState.selectedProjectProperty()));
        hint.visibleProperty().bind(showProjectRole);
        hint.managedProperty().bind(hint.visibleProperty());

        Button create = new Button("Create user");
        create.setOnAction(e -> {
            String uname = username.getText() == null ? "" : username.getText().trim();
            String disp = name.getText() == null ? "" : name.getText().trim();

            try {
                users.createUser(disp, uname, password.getText(), globalRole.getValue());
                reload();

                if (globalRole.getValue() == GlobalRole.USER) {
                    Project p = appState.getSelectedProject();
                    if (p != null) {
                        var created = items.stream()
                                .filter(r -> r.username() != null && r.username().equalsIgnoreCase(uname))
                                .findFirst()
                                .orElse(null);

                        if (created != null) {
                            boolean already = p.getMembers().stream().anyMatch(m -> created.id().equals(m.getId()));
                            if (!already) {
                                String displayName = !disp.isBlank() ? disp : uname;

                                // add to in-memory + (DbStore will persist if store is DbStore)
                                store.addMember(p, new Member(created.id(), displayName, projectRole.getValue()));

                                // also persist role explicitly (safe even if DbStore already did it)
                                users.upsertProjectRole(p.getId(), created.id(), projectRole.getValue());
                            }
                        }
                    }
                }

                name.clear();
                username.clear();
                password.clear();
                globalRole.setValue(GlobalRole.USER);
                projectRole.setValue(ProjectRole.MEMBER);

                status.setText("✅ User created.");
            } catch (Exception ex) {
                status.setText("❌ " + ex.getMessage());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        int r = 0;
        grid.addRow(r++, new Label("Name"), name);
        grid.addRow(r++, new Label("Username"), username);
        grid.addRow(r++, new Label("Password"), password);
        grid.addRow(r++, new Label("Global role"), globalRole);
        grid.addRow(r++, projectRoleLbl, projectRole);
        grid.add(hint, 1, r++);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(90);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(c1, c2);

        HBox actions = new HBox(10, create);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, new Label("Create login user"), grid, actions);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        return box;
    }

    private Node buildUsersTable() {
        TableView<UserAdminService.UserRow> table = new TableView<>(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<UserAdminService.UserRow, String> colUser = new TableColumn<>("Username");
        colUser.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().username()));

        TableColumn<UserAdminService.UserRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().name()));

        TableColumn<UserAdminService.UserRow, String> colRole = new TableColumn<>("Role");
        colRole.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().globalRole().name()));

        TableColumn<UserAdminService.UserRow, Boolean> colActive = new TableColumn<>("Active");
        colActive.setCellValueFactory(cd -> new ReadOnlyBooleanWrapper(cd.getValue().active()));
        colActive.setCellFactory(tc -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            private boolean internal;

            {
                cb.selectedProperty().addListener((obs, old, val) -> {
                    if (internal) return;
                    var row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;

                    try {
                        users.setUserActive(row.id(), val);
                        status.setText("✅ Updated active for " + row.username());
                        reload();
                    } catch (Exception ex) {
                        status.setText("❌ " + ex.getMessage());
                        reload();
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                internal = true;
                cb.setSelected(Boolean.TRUE.equals(item));
                internal = false;
                setGraphic(cb);
            }
        });

        TableColumn<UserAdminService.UserRow, Void> colActions = new TableColumn<>("Actions");
        colActions.setMinWidth(240);
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button edit = new Button("Edit");
            private final Button delete = new Button("Delete");
            private final HBox box = new HBox(10, edit, delete);

            {
                box.setAlignment(Pos.CENTER_LEFT);

                edit.setOnAction(e -> {
                    var row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;

                    Project p = appState.getSelectedProject();
                    String projectName = (p == null) ? "" : p.getName();

                    ProjectRole currentRole = ProjectRole.MEMBER;
                    if (p != null) {
                        try {
                            currentRole = users.rolesForUser(row.id()).getOrDefault(p.getId(), ProjectRole.MEMBER);
                        } catch (Exception ignored) {
                            currentRole = ProjectRole.MEMBER;
                        }
                    }

                    var dlg = new EditUserDialog(row, projectName, currentRole);
                    var res = dlg.showAndWait();
                    if (res.isEmpty()) return;

                    try {
                        var data = res.get();

                        users.updateUser(
                                row.id(),
                                data.displayName(),
                                data.username(),
                                data.newPassword(),
                                data.globalRole(),
                                data.active()
                        );

                        ProjectRole roleFinal = (data.projectRole() == null) ? currentRole : data.projectRole();

                        if (p != null) {
                            users.upsertProjectRole(p.getId(), row.id(), roleFinal);

                            // ✅ IMPORTANT: update existing Member object (don't replace)
                            for (Member m : p.getMembers()) {
                                if (row.id().equals(m.getId())) {
                                    String newName = (data.displayName() == null || data.displayName().isBlank())
                                            ? data.username()
                                            : data.displayName();

                                    m.nameProperty().set(newName);
                                    m.roleProperty().set(roleFinal);
                                    break;
                                }
                            }
                        }

                        status.setText("✅ Updated " + data.username());
                        reload();
                    } catch (Exception ex) {
                        status.setText("❌ " + ex.getMessage());
                        reload();
                    }
                });

                delete.setOnAction(e -> {
                    var row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Delete account");
                    confirm.setHeaderText("Delete login account: " + row.username() + "?");
                    confirm.setContentText("This removes the login account. Member + project data is kept.");
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

                    try {
                        users.deleteUser(row.id());
                        status.setText("✅ Deleted account for " + row.username());
                        reload();
                    } catch (Exception ex) {
                        status.setText("❌ " + ex.getMessage());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().setAll(colUser, colName, colRole, colActive, colActions);

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        VBox box = new VBox(10, new HBox(10, new Label("Login users"), refresh), table);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private void reload() {
        try {
            items.setAll(users.listLoginUsers());
        } catch (Exception ex) {
            status.setText("❌ Failed to load users: " + ex.getMessage());
        }
    }
}
