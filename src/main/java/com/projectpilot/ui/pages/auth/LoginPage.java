package com.projectpilot.ui.pages.auth;

import com.projectpilot.data.db.auth.AuthException;
import com.projectpilot.data.db.auth.AuthProvider;
import com.projectpilot.data.db.auth.UserSession;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class LoginPage extends BorderPane {

    public LoginPage(AuthProvider auth, Consumer<UserSession> onSuccess) {
        getStyleClass().add("auth-root");

        VBox hero = new VBox(10);
        hero.getStyleClass().add("auth-hero");
        hero.setAlignment(Pos.TOP_LEFT);

        Label brand = new Label("ProjectPilot");
        brand.getStyleClass().add("auth-brand");

        Label tagline = new Label("Plan. Track. Deliver.");
        tagline.getStyleClass().add("auth-tagline");

        Label heroSub = new Label("One workspace for tasks, schedules, and team delivery.");
        heroSub.getStyleClass().add("auth-sub");
        heroSub.setWrapText(true);

        VBox bullets = new VBox(6,
                bullet("Live progress across teams"),
                bullet("Gantt + Calendar views"),
                bullet("Files and links per task")
        );
        bullets.getStyleClass().add("auth-bullets");

        hero.getChildren().addAll(brand, tagline, heroSub, bullets);

        VBox form = new VBox(10);
        form.getStyleClass().add("auth-form");
        form.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Sign in");
        title.getStyleClass().add("auth-title");

        Label sub = new Label("Use your ProjectPilot account to continue.");
        sub.getStyleClass().add("auth-sub");
        sub.setWrapText(true);

        Label userLabel = new Label("Username");
        userLabel.getStyleClass().add("field-label");
        TextField username = new TextField();
        username.setPromptText("Enter your username");

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("field-label");
        PasswordField password = new PasswordField();
        password.setPromptText("Enter your password");

        Label error = new Label();
        error.getStyleClass().add("pp-error");
        error.setWrapText(true);
        error.setVisible(false);

        Button login = new Button("Login");
        login.setDefaultButton(true);
        login.getStyleClass().add("primary");

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

        HBox actions = new HBox(8, login, spinner);
        actions.getStyleClass().add("auth-actions");
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(login, Priority.ALWAYS);

        Label hint = new Label("Tip: Press Enter to sign in.");
        hint.getStyleClass().add("auth-footnote");

        form.getChildren().addAll(
                title,
                sub,
                userLabel,
                username,
                passLabel,
                password,
                error,
                actions,
                hint
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox shell = new HBox(hero, form);
        shell.getStyleClass().add("auth-shell");
        shell.setAlignment(Pos.CENTER);

        StackPane surface = new StackPane(shell);
        surface.setPadding(new Insets(24));
        surface.setAlignment(Pos.CENTER);

        setCenter(surface);
        BorderPane.setAlignment(surface, Pos.CENTER);
        setPadding(new Insets(24));
    }

    private static Label bullet(String text) {
        Label label = new Label("- " + (text == null ? "" : text));
        label.getStyleClass().add("auth-bullet");
        label.setWrapText(true);
        return label;
    }
}
