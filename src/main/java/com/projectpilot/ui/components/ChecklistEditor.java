package com.projectpilot.ui.components;

import com.projectpilot.model.ChecklistItem;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ChecklistEditor extends VBox {

    private final ListView<ChecklistItem> listView = new ListView<>();
    private final TextField newItemField = new TextField();
    private final Button addButton = new Button("Add");
    private final BooleanProperty editable = new SimpleBooleanProperty(true);
    private ObservableList<ChecklistItem> items = FXCollections.observableArrayList();

    public ChecklistEditor() {
        setSpacing(8);

        listView.getStyleClass().add("checklist-list");
        listView.setCellFactory(lv -> new ChecklistCell(editable));
        listView.setItems(items);
        listView.setPrefHeight(160);

        newItemField.setPromptText("New checklist item");
        HBox addRow = new HBox(8, newItemField, addButton);
        addRow.getStyleClass().add("checklist-add-row");
        HBox.setHgrow(newItemField, Priority.ALWAYS);

        addButton.setOnAction(e -> addFromField());
        newItemField.setOnAction(e -> addFromField());

        newItemField.disableProperty().bind(editable.not());
        addButton.disableProperty().bind(editable.not());

        getChildren().addAll(listView, addRow);
    }

    public void setItems(ObservableList<ChecklistItem> items) {
        this.items = items == null ? FXCollections.observableArrayList() : items;
        listView.setItems(this.items);
    }

    public BooleanProperty editableProperty() { return editable; }
    public void setEditable(boolean v) { editable.set(v); }
    public boolean isEditable() { return editable.get(); }

    private void addFromField() {
        if (!isEditable()) return;
        String text = newItemField.getText();
        if (text == null || text.trim().isEmpty()) return;
        items.add(new ChecklistItem(text.trim()));
        newItemField.clear();
    }

    private static class ChecklistCell extends ListCell<ChecklistItem> {
        private final BooleanProperty editable;
        private final CheckBox done = new CheckBox();
        private final TextField text = new TextField();
        private final Button remove = new Button("Remove");
        private final HBox row = new HBox(8, done, text, remove);
        private ChecklistItem bound;

        ChecklistCell(BooleanProperty editable) {
            this.editable = editable;
            row.getStyleClass().add("checklist-row");
            text.getStyleClass().add("checklist-text");
            remove.getStyleClass().addAll("ghost", "checklist-remove");
            HBox.setHgrow(text, Priority.ALWAYS);
            row.setPadding(new Insets(2, 0, 2, 0));

            done.disableProperty().bind(editable.not());
            text.disableProperty().bind(editable.not());
            remove.disableProperty().bind(editable.not());
        }

        @Override
        protected void updateItem(ChecklistItem item, boolean empty) {
            super.updateItem(item, empty);
            if (bound != null) {
                done.selectedProperty().unbindBidirectional(bound.doneProperty());
                text.textProperty().unbindBidirectional(bound.textProperty());
            }

            if (empty || item == null) {
                bound = null;
                setGraphic(null);
                return;
            }

            bound = item;
            done.selectedProperty().bindBidirectional(item.doneProperty());
            text.textProperty().bindBidirectional(item.textProperty());
            remove.setOnAction(e -> {
                if (getListView() != null) getListView().getItems().remove(item);
            });
            setGraphic(row);
        }
    }
}
