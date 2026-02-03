package com.projectpilot.ui.dialogs;

import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.model.enums.ProjectRole;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class EditUserDialog extends Dialog<EditUserDialog.Data> {

    public record Data(
            String displayName,
            String username,
            String newPassword,     // blank => keep existing
            GlobalRole globalRole,
            boolean active,
            ProjectRole projectRole // only relevant when globalRole == USER
    ) {}

    public EditUserDialog(UserAdminService.UserRow row, String projectName, ProjectRole currentProjectRole) {
        setTitle("Edit User");

        // Kill the default header strip (the big white area)
        setHeaderText(null);
        DialogPane pane = getDialogPane();
        pane.setHeader(null);
        pane.setGraphic(null);

        // Apply your dark dialog theme
        DialogTheme.apply(this);

        // A class we can target in CSS
        pane.getStyleClass().add("pp-dialog");

        // ---- Inputs ----
        Label title = new Label("Edit: " + safe(row.username()));
        title.getStyleClass().add("pp-dialog-title");

        TextField name = new TextField(safe(row.name()));
        name.setPromptText("Display name");

        TextField username = new TextField(safe(row.username()));
        username.setPromptText("Username");

        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("New password (leave blank to keep)");

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm new password");

        ComboBox<GlobalRole> globalRole = new ComboBox<>();
        globalRole.getItems().setAll(GlobalRole.USER, GlobalRole.ADMIN);
        globalRole.setValue(row.globalRole() == null ? GlobalRole.USER : row.globalRole());

        ComboBox<ProjectRole> projectRole = new ComboBox<>();
        projectRole.getItems().setAll(ProjectRole.LEADER, ProjectRole.MEMBER, ProjectRole.VIEWER);
        projectRole.setValue(currentProjectRole == null ? ProjectRole.MEMBER : currentProjectRole);

        CheckBox active = new CheckBox("Active");
        active.setSelected(row.active());
        active.getStyleClass().add("pp-check");

        // show project role only if global role == USER
        var showProjectRole = Bindings.createBooleanBinding(
                () -> globalRole.getValue() == GlobalRole.USER,
                globalRole.valueProperty()
        );

        Label projectRoleLbl = new Label("Project role");
        projectRoleLbl.visibleProperty().bind(showProjectRole);
        projectRoleLbl.managedProperty().bind(showProjectRole);

        projectRole.visibleProperty().bind(showProjectRole);
        projectRole.managedProperty().bind(showProjectRole);

        Label hint = new Label();
        hint.getStyleClass().add("muted");
        hint.textProperty().bind(Bindings.createStringBinding(() -> {
            if (globalRole.getValue() != GlobalRole.USER) return "";
            if (projectName == null || projectName.isBlank()) return "No project selected — role will not be applied.";
            return "Role applies to project: " + projectName;
        }, globalRole.valueProperty()));

        hint.visibleProperty().bind(showProjectRole);
        hint.managedProperty().bind(showProjectRole);

        // ---- Layout ----
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        int r = 0;
        grid.addRow(r++, new Label("Name"), name);
        grid.addRow(r++, new Label("Username"), username);
        grid.addRow(r++, new Label("New password"), newPassword);
        grid.addRow(r++, new Label("Confirm"), confirm);
        grid.addRow(r++, new Label("Global role"), globalRole);
        grid.addRow(r++, projectRoleLbl, projectRole);
        grid.add(hint, 1, r++);
        grid.add(active, 1, r++);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(130);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(c1, c2);

        VBox content = new VBox(12, title, grid);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("pp-card");

        pane.setContent(content);

        pane.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);

        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        okBtn.getStyleClass().add("primary");

        // ---- Validation ----
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String u = safe(username.getText()).trim();
            if (u.isBlank()) {
                showWarn("Invalid input", "Username is required.");
                e.consume();
                return;
            }
            String pw = safe(newPassword.getText());
            String cf = safe(confirm.getText());
            if (!pw.isBlank() && !pw.equals(cf)) {
                showWarn("Invalid input", "Passwords do not match.");
                e.consume();
            }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;

            String disp = safe(name.getText()).trim();
            String u = safe(username.getText()).trim();
            String pw = safe(newPassword.getText()); // blank => keep existing

            GlobalRole gr = globalRole.getValue() == null ? GlobalRole.USER : globalRole.getValue();
            ProjectRole pr = projectRole.getValue() == null ? ProjectRole.MEMBER : projectRole.getValue();

            return new Data(disp, u, pw, gr, active.isSelected(), pr);
        });
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static void showWarn(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(msg);
        a.setContentText(null);
        DialogTheme.apply(a);
        a.showAndWait();
    }
}
