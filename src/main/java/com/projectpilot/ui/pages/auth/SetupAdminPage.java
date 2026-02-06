package com.projectpilot.ui.pages.auth;

import com.projectpilot.data.db.auth.AuthException;
import com.projectpilot.data.db.auth.AuthService;
import com.projectpilot.data.db.auth.UserSession;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class SetupAdminPage extends BorderPane {

    public SetupAdminPage(AuthService auth, Consumer<UserSession> onDone) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(24));
        card.setMaxWidth(520);
        card.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Create Admin Account");
        title.getStyleClass().add("pp-title");

        Label sub = new Label("First run setup. Create the admin user for this database.");
        sub.setWrapText(true);

        TextField name = new TextField();
        name.setPromptText("Display name (e.g., Taulant)");

        TextField username = new TextField();
        username.setPromptText("Username (e.g., admin)");

        TextField email = new TextField();
        email.setPromptText("Email (Gmail)");

        PasswordField password = new PasswordField();
        password.setPromptText("Password (min 6 chars)");

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm password");

        Label error = new Label();
        error.getStyleClass().add("pp-error");
        error.setWrapText(true);
        error.setVisible(false);

        Button create = new Button("Create Admin");
        create.setDefaultButton(true);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(20, 20);
        spinner.setVisible(false);

        create.setOnAction(e -> {
            error.setVisible(false);

            if (!String.valueOf(password.getText()).equals(String.valueOf(confirm.getText()))) {
                error.setText("Passwords do not match.");
                error.setVisible(true);
                return;
            }
            String em = email.getText() == null ? "" : email.getText().trim();
            if (em.isBlank() || !em.contains("@")) {
                error.setText("Enter a valid email.");
                error.setVisible(true);
                return;
            }

            Task<UserSession> t = new Task<>() {
                @Override protected UserSession call() {
                    return auth.createInitialAdmin(name.getText(), username.getText(), email.getText(), password.getText());
                }
            };

            t.setOnRunning(ev -> {
                create.setDisable(true);
                spinner.setVisible(true);
            });

            t.setOnSucceeded(ev -> {
                create.setDisable(false);
                spinner.setVisible(false);
                onDone.accept(t.getValue());
            });

            t.setOnFailed(ev -> {
                create.setDisable(false);
                spinner.setVisible(false);

                Throwable ex = t.getException();
                String msg = (ex instanceof AuthException ae) ? ae.getMessage() : "Setup failed.";
                error.setText(msg);
                error.setVisible(true);
            });

            new Thread(t, "auth-setup-admin").start();
        });

        card.getChildren().addAll(title, sub, name, username, email, password, confirm, new VBox(6, create, spinner), error);

        setCenter(card);
        BorderPane.setAlignment(card, Pos.CENTER);
        setPadding(new Insets(32));
    }
}
