package com.projectpilot.ui.dialogs;

import com.projectpilot.data.db.auth.GlobalRole;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Locale;

public final class CreateUserDialog extends Dialog<CreateUserDialog.Result> {

    public record Result(String firstName, String lastName, String email, String password, GlobalRole role) {}

    public CreateUserDialog(boolean allowAdminRole) {
        DialogTheme.apply(this);

        setTitle("Create User");
        setHeaderText("Create a new login account");

        ButtonType createBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        TextField firstName = new TextField();
        firstName.setPromptText("Name");

        TextField lastName = new TextField();
        lastName.setPromptText("Surname");

        TextField email = new TextField();
        email.setPromptText("Email (also used as username)");

        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        ComboBox<GlobalRole> role = new ComboBox<>();
        role.getItems().setAll(GlobalRole.USER, GlobalRole.ADMIN);
        role.setValue(GlobalRole.USER);

        if (!allowAdminRole) {
            role.setDisable(true);
            role.setValue(GlobalRole.USER);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        int r = 0;
        grid.add(new Label("Name"), 0, r);
        grid.add(firstName, 1, r++);

        grid.add(new Label("Surname"), 0, r);
        grid.add(lastName, 1, r++);

        grid.add(new Label("Email"), 0, r);
        grid.add(email, 1, r++);

        grid.add(new Label("Password"), 0, r);
        grid.add(password, 1, r++);

        grid.add(new Label("Role"), 0, r);
        grid.add(role, 1, r++);

        getDialogPane().setContent(grid);

        var okNode = getDialogPane().lookupButton(createBtn);

        okNode.disableProperty().bind(
                email.textProperty().isEmpty()
                        .or(password.textProperty().isEmpty())
        );

        setResultConverter(btn -> {
            if (btn != createBtn) return null;

            String fn = safe(firstName.getText());
            String ln = safe(lastName.getText());
            String em = safe(email.getText()).toLowerCase(Locale.ROOT);
            String pw = password.getText() == null ? "" : password.getText();

            if (!em.contains("@") || em.length() < 5) {
                throw new IllegalArgumentException("Please enter a valid email.");
            }
            if (pw.isBlank() || pw.length() < 6) {
                throw new IllegalArgumentException("Password must be at least 6 characters.");
            }

            return new Result(fn, ln, em, pw, role.getValue() == null ? GlobalRole.USER : role.getValue());
        });
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
