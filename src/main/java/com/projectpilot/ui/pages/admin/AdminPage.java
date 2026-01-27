package com.projectpilot.ui.pages.admin;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.model.Project;
import com.projectpilot.model.enums.ProjectRole;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;                 // ✅ missing before
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public class AdminPage extends BorderPane {

    private final InMemoryStore store;
    private final AppState appState;
    private final UserAdminService admin;

    private final TableView<UserAdminService.UserRow> usersTable = new TableView<>();
    private final Label status = new Label();

    private final TextField newName = new TextField();
    private final TextField newUsername = new TextField();
    private final PasswordField newPassword = new PasswordField();
    private final ComboBox<GlobalRole> newGlobalRole = new ComboBox<>();

    private final ComboBox<Project> projectPick = new ComboBox<>();
    private final ComboBox<ProjectRole> projectRolePick = new ComboBox<>();
    private final TableView<RoleRow> rolesTable = new TableView<>();

    public AdminPage(DbManager db, InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;
        this.admin = new UserAdminService(db);

        setPadding(new Insets(16));

        setLeft(buildUsersPane());
        setCenter(buildManagePane());

        status.getStyleClass().add("muted");
        setBottom(status);
        BorderPane.setMargin(status, new Insets(10, 0, 0, 0));

        refreshUsers();
        refreshProjectsList();
    }

    private Node buildUsersPane() {
        VBox box = new VBox(10);
        box.setPrefWidth(420);

        Label title = new Label("Admin");
        title.getStyleClass().add("page-title");

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary");
        refresh.setOnAction(e -> refreshUsers());

        HBox header = new HBox(10, title, new Region(), refresh);
        HBox.setHgrow(header.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);

        TableColumn<UserAdminService.UserRow, String> cUser = col("Username", UserAdminService.UserRow::username);
        TableColumn<UserAdminService.UserRow, String> cName = col("Name", UserAdminService.UserRow::name);
        TableColumn<UserAdminService.UserRow, String> cRole = col("Global", r -> r.globalRole().name());
        TableColumn<UserAdminService.UserRow, String> cActive = col("Active", r -> r.active() ? "YES" : "NO");

        usersTable.getColumns().setAll(cUser, cName, cRole, cActive);

        // ✅ most compatible across JavaFX versions
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, old, cur) -> {
            if (cur != null) loadUserRoles(cur);
        });

        VBox card = new VBox(10, header, usersTable);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12));
        VBox.setVgrow(usersTable, javafx.scene.layout.Priority.ALWAYS);

        box.getChildren().add(card);
        return box;
    }

    private Node buildManagePane() {
        VBox wrap = new VBox(16);
        wrap.setPadding(new Insets(0, 0, 0, 16));

        wrap.getChildren().addAll(
                buildCreateUserCard(),
                buildUserActionsCard(),
                buildProjectRolesCard()
        );

        ScrollPane sp = new ScrollPane(wrap);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setStyle("-fx-background-color: transparent;");
        return sp;
    }

    private Node buildCreateUserCard() {
        VBox card = card("Create User");

        newName.setPromptText("Display name (e.g. Ardi)");
        newUsername.setPromptText("Username (login)");
        newPassword.setPromptText("Password");

        newGlobalRole.setItems(FXCollections.observableArrayList(GlobalRole.USER, GlobalRole.ADMIN));
        newGlobalRole.getSelectionModel().select(GlobalRole.USER);

        Button create = new Button("Create");
        create.getStyleClass().add("primary");
        create.setOnAction(e -> runAsync("Create user", () -> {
            admin.createUser(
                    newName.getText(),
                    newUsername.getText(),
                    newPassword.getText(),
                    newGlobalRole.getValue()
            );
            return null;
        }, v -> {
            newName.clear();
            newUsername.clear();
            newPassword.clear();
            refreshUsers();
            setStatus("User created.");
        }));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("Name"), 0, 0);
        grid.add(newName, 1, 0);

        grid.add(new Label("Username"), 0, 1);
        grid.add(newUsername, 1, 1);

        grid.add(new Label("Password"), 0, 2);
        grid.add(newPassword, 1, 2);

        grid.add(new Label("Global role"), 0, 3);
        grid.add(newGlobalRole, 1, 3);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(90);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().setAll(c0, c1);

        card.getChildren().addAll(grid, create);
        return card;
    }

    private Node buildUserActionsCard() {
        VBox card = card("Selected User Actions");

        PasswordField resetPw = new PasswordField();
        resetPw.setPromptText("New password");

        Button reset = new Button("Reset password");
        reset.getStyleClass().add("secondary");
        reset.setOnAction(e -> {
            var u = selectedUser();
            if (u == null) { setStatus("Select a user first."); return; }
            runAsync("Reset password", () -> {
                admin.resetPassword(u.id(), resetPw.getText());
                return null;
            }, v -> {
                resetPw.clear();
                setStatus("Password reset for " + u.username());
            });
        });

        Button toggleActive = new Button("Enable/Disable");
        toggleActive.getStyleClass().add("secondary");
        toggleActive.setOnAction(e -> {
            var u = selectedUser();
            if (u == null) { setStatus("Select a user first."); return; }
            boolean next = !u.active();
            runAsync("Toggle active", () -> {
                admin.setUserActive(u.id(), next);
                return null;
            }, v -> refreshUsers());
        });

        VBox row = new VBox(10, resetPw, new HBox(10, reset, toggleActive));
        card.getChildren().add(row);
        return card;
    }

    private Node buildProjectRolesCard() {
        VBox card = card("Project Roles");

        projectRolePick.setItems(FXCollections.observableArrayList(
                ProjectRole.LEADER, ProjectRole.MEMBER, ProjectRole.VIEWER
        ));
        projectRolePick.getSelectionModel().select(ProjectRole.MEMBER);

        projectPick.setPromptText("Pick a project…");

        Button setRole = new Button("Add/Update role");
        setRole.getStyleClass().add("primary");
        setRole.setOnAction(e -> {
            var u = selectedUser();
            var p = projectPick.getValue();
            var role = projectRolePick.getValue();

            if (u == null) { setStatus("Select a user first."); return; }
            if (p == null) { setStatus("Select a project."); return; }

            runAsync("Set project role", () -> {
                admin.upsertProjectRole(p.getId(), u.id(), role);
                return null;
            }, v -> loadUserRoles(u));
        });

        Button remove = new Button("Remove from project");
        remove.getStyleClass().add("danger-outline");
        remove.setOnAction(e -> {
            var u = selectedUser();
            var p = projectPick.getValue();

            if (u == null) { setStatus("Select a user first."); return; }
            if (p == null) { setStatus("Select a project."); return; }

            runAsync("Remove from project", () -> {
                admin.removeFromProject(p.getId(), u.id());
                return null;
            }, v -> loadUserRoles(u));
        });

        HBox top = new HBox(10, projectPick, projectRolePick, setRole, remove);
        top.setAlignment(Pos.CENTER_LEFT);

        TableColumn<RoleRow, String> cProj = col("Project", RoleRow::projectName);
        TableColumn<RoleRow, String> cR = col("Role", r -> r.role().name());
        rolesTable.getColumns().setAll(cProj, cR);
        rolesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        card.getChildren().addAll(top, rolesTable);
        return card;
    }

    private void refreshUsers() {
        runAsync("Load users", admin::listLoginUsers, users -> {
            usersTable.setItems(FXCollections.observableArrayList(users));
            if (!users.isEmpty()) usersTable.getSelectionModel().select(0);
            setStatus("Users loaded: " + users.size());
        });
    }

    private void refreshProjectsList() {
        List<Project> all = new ArrayList<>();
        all.addAll(store.getProjects());
        all.addAll(store.getHistoryProjects());

        Map<String, Project> map = new LinkedHashMap<>();
        for (Project p : all) map.put(p.getId(), p);

        projectPick.setItems(FXCollections.observableArrayList(map.values()));
        if (!projectPick.getItems().isEmpty()) projectPick.getSelectionModel().select(0);
    }

    private void loadUserRoles(UserAdminService.UserRow u) {
        runAsync("Load roles", () -> admin.rolesForUser(u.id()), map -> {
            Map<String, Project> byId = new HashMap<>();
            for (Project p : store.getProjects()) byId.put(p.getId(), p);
            for (Project p : store.getHistoryProjects()) byId.put(p.getId(), p);

            List<RoleRow> rows = new ArrayList<>();
            for (var e : map.entrySet()) {
                Project p = byId.get(e.getKey());
                String name = (p != null) ? p.getName() : ("(deleted) " + e.getKey());
                rows.add(new RoleRow(e.getKey(), name, e.getValue()));
            }
            rows.sort(Comparator.comparing(RoleRow::projectName, String.CASE_INSENSITIVE_ORDER));
            rolesTable.setItems(FXCollections.observableArrayList(rows));
        });
    }

    private UserAdminService.UserRow selectedUser() {
        return usersTable.getSelectionModel().getSelectedItem();
    }

    private void setStatus(String msg) {
        status.setText(msg == null ? "" : msg);
    }

    // ✅ FIX: use Consumer<T> so lambdas can be "void"
    private <T> void runAsync(String label, Callable<T> work, Consumer<T> onOk) {
        Task<T> t = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        t.setOnSucceeded(e -> onOk.accept(t.getValue()));
        t.setOnFailed(e -> {
            Throwable ex = t.getException();
            setStatus(label + " failed: " + (ex == null ? "unknown" : ex.getMessage()));
        });

        Thread th = new Thread(t, "admin-" + label.replace(' ', '-').toLowerCase());
        th.setDaemon(true);
        th.start();
    }

    private static VBox card(String title) {
        Label t = new Label(title);
        t.getStyleClass().add("section-title");

        VBox v = new VBox(10, t);
        v.getStyleClass().add("card");
        v.setPadding(new Insets(14));
        return v;
    }

    private static <S> TableColumn<S, String> col(String title, Function<S, String> f) {
        TableColumn<S, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                Optional.ofNullable(f.apply(cd.getValue())).orElse("")
        ));
        return c;
    }

    private record RoleRow(String projectId, String projectName, ProjectRole role) {}
}
