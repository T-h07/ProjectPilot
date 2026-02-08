package com.projectpilot.ui.pages.auth;

import com.projectpilot.data.db.auth.AuthException;
import com.projectpilot.data.db.auth.AuthProvider;
import com.projectpilot.data.db.auth.UserSession;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class LoginPage extends BorderPane {

    public LoginPage(AuthProvider auth, Consumer<UserSession> onSuccess) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(24));
        card.setMaxWidth(420);
        card.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Sign in");
        title.getStyleClass().add("pp-title");

        TextField username = new TextField();
        username.setPromptText("Username");

        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        Label error = new Label();
        error.getStyleClass().add("pp-error");
        error.setWrapText(true);
        error.setVisible(false);

        Button login = new Button("Login");
        login.setDefaultButton(true);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(20, 20);
        spinner.setVisible(false);

        login.setOnAction(e -> {
            error.setVisible(false);

            Task<UserSession> t = new Task<>() {
                @Override protected UserSession call() {
                    return auth.login(username.getText(), password.getText());
                }
            };

            t.setOnRunning(ev -> {
                login.setDisable(true);
                spinner.setVisible(true);
            });

            t.setOnSucceeded(ev -> {
                login.setDisable(false);
                spinner.setVisible(false);
                onSuccess.accept(t.getValue());
            });

            t.setOnFailed(ev -> {
                login.setDisable(false);
                spinner.setVisible(false);

                Throwable ex = t.getException();
                String msg = (ex instanceof AuthException ae) ? ae.getMessage() : "Login failed.";
                error.setText(msg);
                error.setVisible(true);
            });

            new Thread(t, "auth-login").start();
        });

        card.getChildren().addAll(title, username, password, new VBox(6, login, spinner), error);

        setCenter(card);
        BorderPane.setAlignment(card, Pos.CENTER);
        setPadding(new Insets(24));
    }
}
