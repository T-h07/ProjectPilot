package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActivityTimelinePage extends VBox {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM dd, HH:mm");
    private static final Pattern MENTION = Pattern.compile("@[A-Za-z0-9_.-]+");

    private final InMemoryStore store;
    private final AppState appState;

    private final Label header = new Label("Activity");
    private final TextField searchField = new TextField();
    private final CheckBox currentProjectOnly = new CheckBox("Current project only");

    private final FilteredList<ActivityItem> filtered;
    private final ListView<ActivityItem> list = new ListView<>();

    public ActivityTimelinePage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));
        setSpacing(12);

        header.getStyleClass().add("page-title");

        searchField.setPromptText("Search activity...");
        searchField.setPrefWidth(280);

        HBox toolbar = new HBox(10, searchField, currentProjectOnly);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        filtered = new FilteredList<>(store.getActivity(), a -> true);
        list.setItems(filtered);
        list.getStyleClass().add("timeline-list");
        list.setCellFactory(lv -> new TimelineCell(appState, store));

        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(header, toolbar, list);

        searchField.textProperty().addListener((obs, ov, nv) -> applyFilter());
        currentProjectOnly.selectedProperty().addListener((obs, ov, nv) -> applyFilter());
        appState.selectedProjectProperty().addListener((obs, ov, nv) -> applyFilter());
        store.getActivity().addListener((ListChangeListener<ActivityItem>) c -> applyFilter());

        applyFilter();
    }

    private void applyFilter() {
        String raw = searchField.getText();
        String query = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        Project current = appState.getSelectedProject();
        boolean filterProject = currentProjectOnly.isSelected() && current != null;

        filtered.setPredicate(item -> {
            if (item == null) return false;
            if (filterProject) {
                String pid = item.getProjectId();
                if (pid == null || !pid.equals(current.getId())) return false;
            }
            if (query.isEmpty()) return true;
            String msg = safe(item.getMessage());
            String proj = safe(item.getProjectName());
            String action = safe(item.getAction());
            return msg.contains(query) || proj.contains(query) || action.contains(query);
        });
    }

    private static String safe(String v) {
        return v == null ? "" : v.toLowerCase(Locale.ROOT);
    }

    private static final class TimelineCell extends ListCell<ActivityItem> {
        private final AppState appState;
        private final InMemoryStore store;

        private TimelineCell(AppState appState, InMemoryStore store) {
            this.appState = appState;
            this.store = store;
        }

        @Override
        protected void updateItem(ActivityItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            setGraphic(buildRow(item));
        }

        private Node buildRow(ActivityItem item) {
            Region dot = new Region();
            dot.getStyleClass().add("timeline-dot");
            addTypeClass(dot, item.getEntityType());

            Label time = new Label(formatTime(item.getTime()));
            time.getStyleClass().add("timeline-time");

            Label project = new Label(item.getProjectName() == null ? "-" : item.getProjectName());
            project.getStyleClass().add("timeline-project");

            String actionText = item.getAction() == null || item.getAction().isBlank()
                    ? "Update"
                    : item.getAction();
            Label action = new Label(actionText);
            action.getStyleClass().add("timeline-action");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Hyperlink jump = new Hyperlink(jumpLabel(item.getEntityType()));
            jump.getStyleClass().add("timeline-link");
            jump.setVisible(jumpLabel(item.getEntityType()) != null);
            jump.setManaged(jump.isVisible());
            jump.setOnAction(e -> jumpTo(item));

            HBox meta = new HBox(10, time, project, action, spacer, jump);
            meta.setAlignment(Pos.CENTER_LEFT);

            TextFlow message = buildMessageFlow(item.getMessage());
            message.getStyleClass().add("timeline-message");

            HBox reactions = new HBox(8,
                    reactionButton(item, "like", "Like"),
                    reactionButton(item, "celebrate", "Celebrate"),
                    reactionButton(item, "insight", "Insight"),
                    reactionButton(item, "thanks", "Thanks")
            );
            reactions.getStyleClass().add("timeline-reactions");

            VBox content = new VBox(6, meta, message, reactions);
            content.getStyleClass().add("timeline-card");

            HBox row = new HBox(12, dot, content);
            row.setAlignment(Pos.TOP_LEFT);
            return row;
        }

        private TextFlow buildMessageFlow(String text) {
            String msg = text == null ? "" : text;
            TextFlow flow = new TextFlow();
            Matcher matcher = MENTION.matcher(msg);
            int last = 0;
            while (matcher.find()) {
                if (matcher.start() > last) {
                    Text normal = new Text(msg.substring(last, matcher.start()));
                    normal.getStyleClass().add("timeline-text");
                    flow.getChildren().add(normal);
                }
                Text mention = new Text(matcher.group());
                mention.getStyleClass().addAll("timeline-text", "mention");
                flow.getChildren().add(mention);
                last = matcher.end();
            }
            if (last < msg.length()) {
                Text normal = new Text(msg.substring(last));
                normal.getStyleClass().add("timeline-text");
                flow.getChildren().add(normal);
            }
            return flow;
        }

        private Button reactionButton(ActivityItem item, String key, String label) {
            Button b = new Button();
            b.getStyleClass().add("reaction-pill");
            b.textProperty().bind(Bindings.createStringBinding(() -> {
                int count = item.getReactions().getOrDefault(key, 0);
                return count <= 0 ? label : label + " " + count;
            }, item.reactionsProperty()));
            b.setOnAction(e -> item.addReaction(key));
            return b;
        }

        private void jumpTo(ActivityItem item) {
            if (item == null) return;
            String type = item.getEntityType();
            if (type == null || type.isBlank()) return;

            Project project = findProject(item.getProjectId(), item.getProjectName());
            if (project != null) appState.setSelectedProject(project);

            switch (type) {
                case "TASK" -> {
                    Task t = project == null ? null : findTask(project, item.getEntityId());
                    if (t != null) appState.setSelectedTask(t);
                    appState.setCurrentPage(PageId.TASKS);
                }
                case "PROJECT" -> appState.setCurrentPage(PageId.PROJECT_OVERVIEW);
                case "MILESTONE", "PHASE" -> appState.setCurrentPage(PageId.PROJECT_OVERVIEW);
                case "MEMBER" -> appState.setCurrentPage(PageId.TEAM);
                default -> appState.setCurrentPage(PageId.DASHBOARD);
            }
        }

        private Project findProject(String id, String name) {
            if (id != null) {
                for (Project p : store.getProjects()) {
                    if (p != null && id.equals(p.getId())) return p;
                }
            }
            if (name != null) {
                for (Project p : store.getProjects()) {
                    if (p != null && name.equals(p.getName())) return p;
                }
            }
            return null;
        }

        private Task findTask(Project p, String taskId) {
            if (p == null || taskId == null) return null;
            for (Task t : p.getTasks()) {
                if (t != null && taskId.equals(t.getId())) return t;
            }
            return null;
        }

        private String formatTime(LocalDateTime time) {
            if (time == null) return "-";
            return TIME_FMT.format(time);
        }

        private String jumpLabel(String type) {
            if (type == null) return null;
            return switch (type) {
                case "TASK" -> "Open task";
                case "PROJECT" -> "Open project";
                case "MILESTONE" -> "Open milestones";
                case "PHASE" -> "Open phases";
                case "MEMBER" -> "Open team";
                default -> null;
            };
        }

        private void addTypeClass(Region dot, String type) {
            if (dot == null || type == null) return;
            String cls = switch (type) {
                case "TASK" -> "timeline-dot-task";
                case "PROJECT" -> "timeline-dot-project";
                case "MILESTONE" -> "timeline-dot-milestone";
                case "PHASE" -> "timeline-dot-phase";
                case "MEMBER" -> "timeline-dot-member";
                default -> null;
            };
            if (cls != null) dot.getStyleClass().add(cls);
        }
    }
}
