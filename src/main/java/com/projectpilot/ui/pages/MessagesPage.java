package com.projectpilot.ui.pages;

import com.projectpilot.chat.ChatMessage;
import com.projectpilot.chat.ChatService;
import com.projectpilot.chat.ChatThread;
import com.projectpilot.chat.ChatType;
import com.projectpilot.chat.ChatUser;
import com.projectpilot.core.AppState;
import com.projectpilot.ui.dialogs.DialogTheme;
import javafx.application.Platform;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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
    private final ExecutorService ioExec;
    private final AtomicBoolean threadsLoading = new AtomicBoolean(false);
    private final AtomicBoolean messagesLoading = new AtomicBoolean(false);

    public MessagesPage(ChatService chat, AppState appState) {
        this.chat = chat;
        this.appState = appState;
        this.ioExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pp-chat-io");
            t.setDaemon(true);
            return t;
        });

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
            if (n == null) {
                poller.stop();
                ioExec.shutdownNow();
            } else {
                poller.play();
            }
        });

        appState.sessionProperty().addListener((obs, o, n) -> refreshThreadsAsync(true, null));

        refreshThreadsAsync(true, null);
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

    private void refreshThreadsAsync(boolean keepSelection, String forceSelectId) {
        if (ioExec.isShutdown()) return;
        if (!threadsLoading.compareAndSet(false, true)) return;
        String me = currentUserId();
        String selectedId = selected == null ? null : selected.id();
        boolean inputFocused = input.isFocused();

        runIo(() -> chat.listThreads(me), next -> {
            refreshingThreads = true;
            threads.setAll(next);
            refreshingThreads = false;
            status.setText("");

            String targetId = forceSelectId != null ? forceSelectId : (keepSelection ? selectedId : null);
            if (targetId != null) {
                selectThreadById(targetId);
            } else if (!keepSelection) {
                threadList.getSelectionModel().clearSelection();
                selectThread(null);
            }

            if (inputFocused && !input.isDisabled()) {
                input.requestFocus();
            }
            threadsLoading.set(false);
        }, ex -> {
            threadsLoading.set(false);
            status.setText("Failed to load chats: " + ex.getMessage());
        });
    }

    private void refreshMessagesAsync() {
        if (selected == null) return;
        if (ioExec.isShutdown()) return;
        if (!messagesLoading.compareAndSet(false, true)) return;

        String me = currentUserId();
        String threadId = selected.id();
        runIo(() -> chat.listMessages(threadId, me, 200), next -> {
            messagesLoading.set(false);
            if (selected == null || !threadId.equals(selected.id())) return;
            messages.setAll(next);
            status.setText("");
            if (!messages.isEmpty()) {
                messageList.scrollTo(messages.size() - 1);
            }
        }, ex -> {
            messagesLoading.set(false);
            status.setText("Failed to load messages: " + ex.getMessage());
        });
    }

    private void refreshQuiet() {
        boolean typing = input.isFocused() && input.getText() != null && !input.getText().isBlank();
        if (!typing) {
            refreshThreadsAsync(true, null);
        }
        refreshMessagesAsync();
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

        refreshMessagesAsync();
    }

    private void startDirectChat() {
        String me = currentUserId();
        runIo(() -> chat.listUsers(me), users -> {
            if (users == null || users.isEmpty()) {
                status.setText("No users available.");
                return;
            }

            ChoiceDialog<ChatUser> dlg = new ChoiceDialog<>(null, users);
            dlg.setTitle("New message");
            dlg.setHeaderText("Start a direct chat");
            dlg.setContentText("User");
            DialogTheme.apply(dlg);

            dlg.showAndWait().ifPresent(u -> {
                runIo(() -> chat.getOrCreateDirect(me, u.id()), thread -> {
                    if (thread == null) {
                        status.setText("Failed to start chat.");
                        return;
                    }
                    refreshThreadsAsync(false, thread.id());
                }, ex -> status.setText("Failed to start chat: " + ex.getMessage()));
            });
        }, ex -> status.setText("Failed to load users: " + ex.getMessage()));
    }

    private void sendMessage() {
        if (selected == null) return;
        if (ioExec.isShutdown()) return;
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isBlank()) return;

        sendBtn.setDisable(true);
        String threadId = selected.id();
        String me = currentUserId();

        runIo(() -> {
            chat.sendMessage(threadId, me, text);
            return null;
        }, unused -> {
            sendBtn.setDisable(false);
            input.clear();
            refreshThreadsAsync(true, null);
            refreshMessagesAsync();
        }, ex -> {
            sendBtn.setDisable(false);
            status.setText("Send failed: " + ex.getMessage());
        });
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

    private void selectThreadById(String id) {
        if (id == null) return;
        for (ChatThread t : threads) {
            if (id.equals(t.id())) {
                threadList.getSelectionModel().select(t);
                return;
            }
        }
    }

    private <T> void runIo(Supplier<T> task, java.util.function.Consumer<T> onSuccess, java.util.function.Consumer<Exception> onError) {
        if (ioExec.isShutdown()) return;
        ioExec.submit(() -> {
            try {
                T out = task.get();
                Platform.runLater(() -> onSuccess.accept(out));
            } catch (Exception ex) {
                Platform.runLater(() -> onError.accept(ex));
            }
        });
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
