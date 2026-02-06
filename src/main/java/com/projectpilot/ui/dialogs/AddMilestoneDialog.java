package com.projectpilot.ui.dialogs;

import com.projectpilot.model.Milestone;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;

public class AddMilestoneDialog extends Dialog<Milestone> {

    public AddMilestoneDialog() {
        DialogTheme.apply(this);

        setTitle("Add Milestone");
        setHeaderText("Create a milestone");

        ButtonType addBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        TextField name = new TextField();
        name.setPromptText("Milestone name");

        DatePicker due = new DatePicker(LocalDate.now().plusWeeks(2));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        grid.add(new Label("Name"), 0, 0);
        grid.add(name, 1, 0);
        grid.add(new Label("Due date"), 0, 1);
        grid.add(due, 1, 1);

        getDialogPane().setContent(grid);

        Node ok = getDialogPane().lookupButton(addBtn);
        ok.setDisable(true);
        name.textProperty().addListener((obs, o, n) -> ok.setDisable(n == null || n.trim().isEmpty()));

        setResultConverter(bt -> {
            if (bt != addBtn) return null;
            Milestone m = new Milestone(name.getText().trim());
            m.dueDateProperty().set(due.getValue());
            return m;
        });
    }
}
