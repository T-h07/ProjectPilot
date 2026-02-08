package com.projectpilot.ui.dialogs;

import com.projectpilot.model.Phase;
import com.projectpilot.model.PhaseTemplate;
import com.projectpilot.model.Project;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.lang.reflect.Method;
import java.time.LocalDate;
import com.projectpilot.ui.dialogs.DialogTheme;

public final class CreateProjectDialog extends Dialog<Project> {

    public CreateProjectDialog() {
        DialogTheme.apply(this);

        setTitle("Create Project");
        setHeaderText("Enter project details");

        ButtonType createBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Project name");

        TextArea descArea = new TextArea();
        descArea.setPromptText("Short description (what is this project?)");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true); // ✅ word wrap

        DatePicker startPicker = new DatePicker(LocalDate.now());
        DatePicker endPicker = new DatePicker(LocalDate.now().plusWeeks(4));

        ComboBox<PhaseTemplate> templateBox = new ComboBox<>();
        templateBox.getItems().addAll(PhaseTemplate.values());
        templateBox.setValue(PhaseTemplate.SOFTWARE);

        TextArea stakeholdersArea = new TextArea();
        stakeholdersArea.setPromptText("""
Stakeholders (freeform for now)
Example:
- Sponsor: John Doe (john@company.com)
- Client: ACME Corp
- Reviewer: Prof. X
""");
        stakeholdersArea.setPrefRowCount(4);
        stakeholdersArea.setWrapText(true); // ✅ word wrap

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        int r = 0;
        grid.add(new Label("Name"), 0, r);
        grid.add(nameField, 1, r++);

        grid.add(new Label("Description"), 0, r);
        grid.add(descArea, 1, r++);

        grid.add(new Label("Start date"), 0, r);
        grid.add(startPicker, 1, r++);

        grid.add(new Label("End date"), 0, r);
        grid.add(endPicker, 1, r++);

        grid.add(new Label("Phase template"), 0, r);
        grid.add(templateBox, 1, r++);

        grid.add(new Label("Stakeholders"), 0, r);
        grid.add(stakeholdersArea, 1, r++);

        getDialogPane().setContent(grid);

        // basic validation: require name
        var okNode = getDialogPane().lookupButton(createBtn);
        okNode.disableProperty().bind(nameField.textProperty().isEmpty());

        setResultConverter(btn -> {
            if (btn != createBtn) return null;

            Project p = new Project(nameField.getText().trim());
            p.setDescription(descArea.getText() == null ? "" : descArea.getText().trim());
            p.setStartDate(startPicker.getValue());
            p.setEndDate(endPicker.getValue());
            p.setStakeholders(stakeholdersArea.getText() == null ? "" : stakeholdersArea.getText().trim());

            PhaseTemplate tpl = templateBox.getValue() == null ? PhaseTemplate.EMPTY : templateBox.getValue();
            p.setPhaseTemplate(tpl.name());

            // ✅ Populate phases immediately
            p.getPhases().clear();
            for (String phName : tpl.phases()) {
                Phase ph = createPhase(phName);
                if (ph != null) p.getPhases().add(ph);
            }

            return p;
        });
    }

    /**
     * Create Phase in a way that works with your model:
     * - try new Phase(String)
     * - else new Phase() + nameProperty().set(...)
     */
    private static Phase createPhase(String name) {
        if (name == null) name = "";

        try {
            return Phase.class.getConstructor(String.class).newInstance(name);
        } catch (Exception ignored) {}

        try {
            Phase ph = Phase.class.getConstructor().newInstance();
            trySetNameProperty(ph, name);
            return ph;
        } catch (Exception ignored) {}

        return null;
    }

    private static void trySetNameProperty(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod("nameProperty");
            Object prop = m.invoke(obj);
            Method set = prop.getClass().getMethod("set", String.class);
            set.invoke(prop, name);
        } catch (Exception ignored) {}
    }
}
