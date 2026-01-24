package com.projectpilot.ui.dialogs;

import com.projectpilot.model.Project;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.Node;

import java.time.LocalDate;

public class CreateProjectDialog extends Dialog<Project> {

    public CreateProjectDialog() {
        setTitle("New Project");
        setHeaderText("Create a new project");

        ButtonType createBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        TextField name = new TextField();
        name.setPromptText("Project name");

        TextArea desc = new TextArea();
        desc.setPromptText("Description (optional)");
        desc.setPrefRowCount(3);

        DatePicker start = new DatePicker(LocalDate.now());
        DatePicker end = new DatePicker(LocalDate.now().plusWeeks(4));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        grid.add(new Label("Name"), 0, 0);
        grid.add(name, 1, 0);

        grid.add(new Label("Description"), 0, 1);
        grid.add(desc, 1, 1);

        grid.add(new Label("Start"), 0, 2);
        grid.add(start, 1, 2);

        grid.add(new Label("End"), 0, 3);
        grid.add(end, 1, 3);

        getDialogPane().setContent(grid);

        Node ok = getDialogPane().lookupButton(createBtn);
        ok.setDisable(true);

        name.textProperty().addListener((obs, o, n) -> ok.setDisable(n == null || n.trim().isEmpty()));

        setResultConverter(bt -> {
            if (bt != createBtn) return null;
            Project p = new Project(name.getText().trim());
            p.setDescription(desc.getText() == null ? "" : desc.getText().trim());
            p.setStartDate(start.getValue());
            p.setEndDate(end.getValue());
            return p;
        });
    }
}
