package com.projectpilot.ui.pages;

import com.projectpilot.chat.ChatMessage;
import com.projectpilot.chat.ChatService;
import com.projectpilot.chat.ChatThread;
import com.projectpilot.chat.ChatType;
import com.projectpilot.chat.ChatUser;
import com.projectpilot.core.AppState;
import com.projectpilot.ui.dialogs.DialogTheme;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class MessagesPage extends BorderPane {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ChatService chat;
    private final AppState appState;

    private final ObservableList<ChatThread> threads = FXCollections.observableArrayList();
    private final FilteredList<ChatThread> filteredThreads = new FilteredList<>(threads, t -> true);
    private final ObservableList<ChatMessage> messages = FXCollections.observableArrayList();

    private final ListView<ChatThread> threadList = new ListView<>(filteredThreads);
    private final ListView<ChatMessage> messageList = new ListView<>(messages);

    private final Label chatTitle = new Label("Select a chat");
    private final Label chatSubtitle = new Label("");
    private final Label status = new Label("");

    private final TextArea input = new TextArea();
    private final Button sendBtn = new Button("Send");

    private ChatThread selected;
    private final Timeline poller;
    private boolean refreshingThreads;

    public MessagesPage(ChatService chat, AppState appState) {
        this.chat = chat;
        this.appState = appState;

        setPadding(new Insets(16));

        Label title = new Label("Messages");
        title.getStyleClass().add("page-title");

        status.getStyleClass().add("muted");

        VBox header = new VBox(6, title, status);
        setTop(header);

        TextField search = new TextField();
        search.setPromptText("Search chats...");
        search.textProperty().addListener((obs, o, n) -> applyFilter(n));

        Button newChat = new Button("New message");
        newChat.getStyleClass().add("primary");
        newChat.setOnAction(e -> startDirectChat());

        HBox leftActions = new HBox(8, newChat);
        leftActions.setAlignment(Pos.CENTER_LEFT);

        VBox left = new VBox(10, search, leftActions, threadList);
        left.getStyleClass().add("chat-sidebar");
        left.setPadding(new Insets(12));
        left.setPrefWidth(280);
        VBox.setVgrow(threadList, Priority.ALWAYS);

        chatTitle.getStyleClass().add("chat-title");
        chatSubtitle.getStyleClass().add("muted");

        VBox chatHeader = new VBox(4, chatTitle, chatSubtitle);
        chatHeader.getStyleClass().add("chat-header");
        chatHeader.setPadding(new Insets(10));

        messageList.setPlaceholder(new Label("No messages yet"));
        messageList.getStyleClass().add("chat-messages");
        messageList.setCellFactory(lv -> new MessageCell());

        input.setPromptText("Write a message...");
        input.setWrapText(true);
        input.setPrefRowCount(2);

        sendBtn.getStyleClass().add("primary");
        sendBtn.setOnAction(e -> sendMessage());

        HBox composer = new HBox(10, input, sendBtn);
        composer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);
        composer.getStyleClass().add("chat-composer");
        composer.setPadding(new Insets(10, 0, 0, 0));

        VBox right = new VBox(10, chatHeader, messageList, composer);
        right.getStyleClass().add("chat-panel");
        VBox.setVgrow(messageList, Priority.ALWAYS);

        HBox content = new HBox(14, left, right);
        HBox.setHgrow(right, Priority.ALWAYS);
        setCenter(content);

        threadList.setCellFactory(lv -> new ThreadCell());
        threadList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (refreshingThreads) return;
            selectThread(n);
        });

        input.setDisable(true);
        sendBtn.setDisable(true);

        poller = new Timeline(new KeyFrame(Duration.seconds(3), e -> refreshQuiet()));
        poller.setCycleCount(Timeline.INDEFINITE);

        sceneProperty().addListener((obs, o, n) -> {
            if (n == null) poller.stop();
            else poller.play();
        });

        appState.sessionProperty().addListener((obs, o, n) -> refreshThreads(true));

        refreshThreads(true);
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        filteredThreads.setPredicate(t -> {
            if (t == null) return false;
            if (q.isBlank()) return true;
            String title = safe(t.title()).toLowerCase();
            String sub = safe(t.subtitle()).toLowerCase();
            return title.contains(q) || sub.contains(q);
        });
    }

    private void refreshThreads(boolean keepSelection) {
        try {
            String me = currentUserId();
            List<ChatThread> next = chat.listThreads(me);
            String selectedId = selected == null ? null : selected.id();
            boolean inputFocused = input.isFocused();

            refreshingThreads = true;
            threads.setAll(next);
            refreshingThreads = false;
            status.setText("");

            if (keepSelection && selectedId != null) {
                ChatThread match = null;
                for (ChatThread t : threads) {
                    if (selectedId.equals(t.id())) {
                        match = t;
                        break;
                    }
                }
                if (match != null) {
                    threadList.getSelectionModel().select(match);
                } else {
                    threadList.getSelectionModel().clearSelection();
                    selectThread(null);
                }
            }

            if (inputFocused && !input.isDisabled()) {
                input.requestFocus();
            }
        } catch (Exception ex) {
            status.setText("Failed to load chats: " + ex.getMessage());
        }
    }

    private void refreshMessages() {
        if (selected == null) return;
        try {
            String me = currentUserId();
            List<ChatMessage> next = chat.listMessages(selected.id(), me, 200);
            messages.setAll(next);
            status.setText("");
            if (!messages.isEmpty()) {
                messageList.scrollTo(messages.size() - 1);
            }
        } catch (Exception ex) {
            status.setText("Failed to load messages: " + ex.getMessage());
        }
    }

    private void refreshQuiet() {
        refreshThreads(true);
        refreshMessages();
    }

    private void selectThread(ChatThread thread) {
        selected = thread;
        if (thread == null) {
            chatTitle.setText("Select a chat");
            chatSubtitle.setText("");
            messages.clear();
            input.setDisable(true);
            sendBtn.setDisable(true);
            return;
        }

        chatTitle.setText(displayTitle(thread));
        chatSubtitle.setText(thread.subtitle() == null ? "" : thread.subtitle());
        input.setDisable(false);
        sendBtn.setDisable(false);

        refreshMessages();
    }

    private void startDirectChat() {
        try {
            String me = currentUserId();
            List<ChatUser> users = chat.listUsers(me);
            if (users.isEmpty()) {
                status.setText("No users available.");
                return;
            }

            ChoiceDialog<ChatUser> dlg = new ChoiceDialog<>(null, users);
            dlg.setTitle("New message");
            dlg.setHeaderText("Start a direct chat");
            dlg.setContentText("User");
            DialogTheme.apply(dlg);

            dlg.showAndWait().ifPresent(u -> {
                try {
                    ChatThread thread = chat.getOrCreateDirect(me, u.id());
                    refreshThreads(false);
                    threadList.getSelectionModel().select(thread);
                } catch (Exception ex) {
                    status.setText("Failed to start chat: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            status.setText("Failed to load users: " + ex.getMessage());
        }
    }

    private void sendMessage() {
        if (selected == null) return;
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isBlank()) return;

        try {
            chat.sendMessage(selected.id(), currentUserId(), text);
            input.clear();
            refreshThreads(false);
            refreshMessages();
        } catch (Exception ex) {
            status.setText("Send failed: " + ex.getMessage());
        }
    }

    private String currentUserId() {
        if (appState == null || appState.getSession() == null) return "";
        String id = appState.getSession().id();
        return id == null ? "" : id.trim();
    }

    private static String displayTitle(ChatThread t) {
        if (t == null) return "";
        String base = safe(t.title());
        if (t.type() == ChatType.TEAM) return base.isBlank() ? "Team chat" : ("Team - " + base);
        if (t.type() == ChatType.DIRECT) return base.isBlank() ? "Direct message" : base;
        return base.isBlank() ? "Group chat" : base;
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private final class ThreadCell extends ListCell<ChatThread> {
        @Override
        protected void updateItem(ChatThread item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label title = new Label(displayTitle(item));
            title.getStyleClass().add("chat-thread-title");

            Label sub = new Label(item.subtitle() == null ? "" : item.subtitle());
            sub.getStyleClass().add("chat-thread-sub");

            VBox box = new VBox(2, title, sub);
            setGraphic(box);
        }
    }

    private final class MessageCell extends ListCell<ChatMessage> {
        @Override
        protected void updateItem(ChatMessage item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            String me = currentUserId();
            boolean self = me != null && me.equals(item.senderId());

            String sender = self ? "You" : safe(item.senderName());
            String time = formatTime(item.createdAt());

            Label meta = new Label(sender + " - " + time);
            meta.getStyleClass().add("chat-meta");

            Label bubble = new Label(item.body());
            bubble.setWrapText(true);
            bubble.setMaxWidth(420);
            bubble.getStyleClass().addAll("chat-bubble", self ? "chat-bubble-self" : "chat-bubble-other");

            VBox stack = new VBox(2, meta, bubble);
            HBox row = new HBox(stack);
            row.setAlignment(self ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            row.setFillHeight(true);

            setGraphic(row);
        }
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0) return "";
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(TIME_FMT);
    }
}
