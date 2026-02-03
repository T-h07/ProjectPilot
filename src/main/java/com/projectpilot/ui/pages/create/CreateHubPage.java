package com.projectpilot.ui.pages.create;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.model.Project;
import com.projectpilot.ui.dialogs.CreateProjectDialog;
import com.projectpilot.ui.dialogs.CreateUserDialog;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.lang.reflect.Method;
import java.util.Optional;

public final class CreateHubPage extends BorderPane {

    private final DbManager db;
    private final InMemoryStore store;
    private final AppState appState;

    private final Label status = new Label();

    public CreateHubPage(DbManager db, InMemoryStore store, AppState appState) {
        this.db = db;
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));

        Label title = new Label("Create");
        title.getStyleClass().add("pp-h1");

        status.getStyleClass().add("muted");

        VBox header = new VBox(6, title, status);
        setTop(header);

        VBox cardProject = buildCreateProjectCard();
        VBox cardUser = buildCreateUserCard();

        HBox row = new HBox(14, cardProject, cardUser);
        row.setFillHeight(true);

        HBox.setHgrow(cardProject, Priority.ALWAYS);
        HBox.setHgrow(cardUser, Priority.ALWAYS);

        setCenter(row);
    }

    private VBox buildCreateProjectCard() {
        Label h = new Label("New Project");
        h.getStyleClass().add("section-title");

        Label d = new Label("Create a new project and start planning tasks, phases, and milestones.");
        d.getStyleClass().add("muted");
        d.setWrapText(true);

        Button btn = new Button("Create Project");
        btn.getStyleClass().add("primary");
        btn.setOnAction(e -> openCreateProject());

        VBox box = new VBox(10, h, d, btn);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        return box;
    }

    private VBox buildCreateUserCard() {
        Label h = new Label("New User");
        h.getStyleClass().add("section-title");

        Label d = new Label("Create a login account for a team member (ADMIN only).");
        d.getStyleClass().add("muted");
        d.setWrapText(true);

        Button btn = new Button("Create User");
        btn.getStyleClass().add("primary");

        if (!appState.isAdmin()) {
            btn.setDisable(true);
            btn.setTooltip(new Tooltip("Only admins can create users."));
        } else {
            btn.setOnAction(e -> openCreateUser());
        }

        VBox box = new VBox(10, h, d, btn);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        return box;
    }

    private void openCreateProject() {
        CreateProjectDialog dlg = new CreateProjectDialog();
        Optional<Project> res = dlg.showAndWait();
        if (res.isEmpty()) return;

        Project p = res.get();
        try {
            addProjectToStore(p);
            appState.setSelectedProject(p);
            status.setText("✅ Project created: " + p.getName());
            appState.setCurrentPage(PageId.PROJECT_OVERVIEW);
        } catch (Exception ex) {
            status.setText("❌ Create project failed: " + ex.getMessage());
        }
    }

    private void openCreateUser() {
        UserAdminService users = new UserAdminService(db);

        CreateUserDialog dlg = new CreateUserDialog(true);
        var res = dlg.showAndWait();
        if (res.isEmpty()) return;

        CreateUserDialog.Result r = res.get();
        try {
            users.createUserWithEmail(
                    r.firstName(),
                    r.lastName(),
                    r.email(),
                    r.password(),
                    r.role()
            );
            status.setText("✅ User created: " + r.email());
        } catch (Exception ex) {
            status.setText("❌ Create user failed: " + ex.getMessage());
        }
    }

    /**
     * We don't know your exact InMemoryStore API, so we support multiple method names safely.
     */
    private void addProjectToStore(Project p) {
        // try common method names
        if (invokeStore("addProject", p)) return;
        if (invokeStore("createProject", p)) return;
        if (invokeStore("saveProject", p)) return;
        if (invokeStore("upsertProject", p)) return;

        throw new IllegalStateException("InMemoryStore has no method to add a Project (expected addProject/createProject/saveProject/upsertProject)");
    }

    private boolean invokeStore(String methodName, Project p) {
        try {
            Method m = store.getClass().getMethod(methodName, Project.class);
            m.invoke(store, p);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
