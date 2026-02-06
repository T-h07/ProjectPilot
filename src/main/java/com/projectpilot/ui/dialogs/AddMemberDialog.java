package com.projectpilot.ui.dialogs;

import com.projectpilot.model.Member;
import com.projectpilot.model.enums.ProjectRole;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.Node;

public class AddMemberDialog extends Dialog<Member> {

    public AddMemberDialog() {
        DialogTheme.apply(this);

        setTitle("Add Member");
        setHeaderText("Add a project member");

        ButtonType addBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        TextField name = new TextField();
        name.setPromptText("Full name");

        ComboBox<ProjectRole> role = new ComboBox<>();
        role.getItems().addAll(ProjectRole.values());
        role.setValue(ProjectRole.MEMBER);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        grid.add(new Label("Name"), 0, 0);
        grid.add(name, 1, 0);
        grid.add(new Label("Role"), 0, 1);
        grid.add(role, 1, 1);

        getDialogPane().setContent(grid);

        Node ok = getDialogPane().lookupButton(addBtn);
        ok.setDisable(true);

        name.textProperty().addListener((obs, o, n) -> ok.setDisable(n == null || n.trim().isEmpty()));

        setResultConverter(bt -> {
            if (bt != addBtn) return null;
            return new Member(name.getText().trim(), role.getValue());
        });
    }
}
