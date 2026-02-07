package com.projectpilot.ui.dialogs;

import com.projectpilot.data.db.TeamService;
import com.projectpilot.model.Member;
import com.projectpilot.model.enums.ProjectRole;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CreateTeamDialog extends Dialog<CreateTeamDialog.Result> {

    public record Result(String name, String leaderId, List<TeamService.TeamMemberSpec> members) {}

    private static final class MemberRow {
        private final ComboBox<Member> member = new ComboBox<>();
        private final ComboBox<ProjectRole> role = new ComboBox<>();
        private final Button remove = new Button("Remove");
        private final HBox node;

        private MemberRow(List<Member> directory) {
            member.getItems().setAll(directory);
            member.setPromptText("Select member");
            member.setPrefWidth(260);
            member.setCellFactory(cb -> new ListCell<>() {
                @Override protected void updateItem(Member item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : memberLabel(item));
                }
            });
            member.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(Member item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "Select member" : memberLabel(item));
                }
            });

            role.getItems().setAll(ProjectRole.MEMBER, ProjectRole.VIEWER);
            role.setValue(ProjectRole.MEMBER);
            role.setPrefWidth(130);

            remove.getStyleClass().addAll("secondary", "sm");

            node = new HBox(8, member, role, remove);
            node.setAlignment(Pos.CENTER_LEFT);
        }
    }

    private final List<MemberRow> rows = new ArrayList<>();

    public CreateTeamDialog(List<Member> directory) {
        DialogTheme.apply(this);

        setTitle("Create Team");
        setHeaderText(null);

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("pp-dialog");

        TextField teamName = new TextField();
        teamName.setPromptText("Team name");

        ComboBox<Member> leaderBox = new ComboBox<>();
        leaderBox.getItems().setAll(directory);
        leaderBox.setPromptText("Select leader");
        leaderBox.setPrefWidth(320);
        leaderBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : memberLabel(item));
            }
        });
        leaderBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select leader" : memberLabel(item));
            }
        });

        VBox membersBox = new VBox(8);
        for (int i = 0; i < 3; i++) addMemberRow(membersBox, directory);

        Button addRow = new Button("+ Add member");
        addRow.getStyleClass().add("secondary");
        addRow.setOnAction(e -> addMemberRow(membersBox, directory));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        int r = 0;
        grid.addRow(r++, new Label("Team name"), teamName);
        grid.addRow(r++, new Label("Leader"), leaderBox);
        grid.addRow(r++, new Label("Members"), membersBox);
        grid.add(addRow, 1, r++);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(110);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(c1, c2);

        VBox content = new VBox(12, new Label("Create team"), grid);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("pp-card");

        pane.setContent(content);

        pane.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        okBtn.getStyleClass().add("primary");

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String nm = safe(teamName.getText());
            if (nm.isBlank()) {
                showWarn("Invalid input", "Team name is required.");
                e.consume();
                return;
            }
            Member leader = leaderBox.getValue();
            if (leader == null || safe(leader.getId()).isBlank()) {
                showWarn("Invalid input", "Leader is required.");
                e.consume();
                return;
            }

            Set<String> ids = new HashSet<>();
            ids.add(leader.getId());
            for (MemberRow row : rows) {
                Member m = row.member.getValue();
                if (m == null || safe(m.getId()).isBlank()) continue;
                if (!ids.add(m.getId())) {
                    showWarn("Invalid input", "Duplicate user detected: " + memberLabel(m));
                    e.consume();
                    return;
                }
            }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;

            String nm = safe(teamName.getText());
            Member leader = leaderBox.getValue();
            if (leader == null) return null;

            List<TeamService.TeamMemberSpec> members = new ArrayList<>();
            for (MemberRow row : rows) {
                Member m = row.member.getValue();
                if (m == null || safe(m.getId()).isBlank()) continue;
                ProjectRole role = row.role.getValue() == null ? ProjectRole.MEMBER : row.role.getValue();
                members.add(new TeamService.TeamMemberSpec(m.getId(), role));
            }

            return new Result(nm, leader.getId(), members);
        });
    }

    private void addMemberRow(VBox membersBox, List<Member> directory) {
        MemberRow row = new MemberRow(directory);
        row.remove.setOnAction(e -> {
            membersBox.getChildren().remove(row.node);
            rows.remove(row);
        });
        rows.add(row);
        membersBox.getChildren().add(row.node);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String memberLabel(Member m) {
        if (m == null) return "";
        String name = safe(m.getName());
        String id = safe(m.getId());
        if (name.isBlank()) return id;
        if (id.isBlank()) return name;
        return name + " - " + shortId(id);
    }

    private static String shortId(String id) {
        if (id == null) return "";
        String s = id.trim();
        if (s.length() <= 10) return s;
        return s.substring(0, 6) + "..." + s.substring(s.length() - 4);
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
