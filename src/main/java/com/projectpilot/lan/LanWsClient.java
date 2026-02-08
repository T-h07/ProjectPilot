package com.projectpilot.lan;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.lan.dto.WsMessage;
import com.projectpilot.util.SslUtil;
import com.projectpilot.util.AppLog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LanWsClient implements WebSocket.Listener {

    private final String wsUrl;
    private final Runnable onRefresh;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService reconnectExec;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private WebSocket socket;
    private volatile String token;
    private volatile boolean closed;
    private int failures;

    public LanWsClient(String wsUrl, Runnable onRefresh) {
        this.wsUrl = wsUrl;
        this.onRefresh = onRefresh;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3));
        var ssl = SslUtil.trustAllContextIfEnabled();
        if (ssl != null) builder.sslContext(ssl);
        this.http = builder.build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.reconnectExec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pp-lan-ws-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    public void connect(String token) {
        if (wsUrl == null || wsUrl.isBlank()) return;
        if (token == null || token.isBlank()) return;
        this.token = token;
        this.closed = false;
        this.failures = 0;
        openSocket();
    }

    public void close() {
        closed = true;
        token = null;
        if (socket != null) {
            socket.abort();
            socket = null;
        }
        reconnectExec.shutdownNow();
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        failures = 0;
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        if (last) {
            handleMessage(data == null ? "" : data.toString());
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        AppLog.warn("lan-ws", "WebSocket error: " + shortError(error));
        scheduleReconnect();
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        scheduleReconnect();
        return null;
    }

    private void handleMessage(String payload) {
        if (payload == null || payload.isBlank()) return;
        try {
            WsMessage msg = mapper.readValue(payload, WsMessage.class);
            if (msg != null && "refresh".equalsIgnoreCase(msg.type())) {
                if (onRefresh != null) onRefresh.run();
            }
        } catch (Exception e) {
            if (payload.contains("refresh") && onRefresh != null) onRefresh.run();
        }
    }

    private void openSocket() {
        if (closed) return;
        if (token == null || token.isBlank()) return;
        String url = wsUrl + (wsUrl.contains("?") ? "&" : "?") + "token=" + token;
        http.newWebSocketBuilder()
                .buildAsync(URI.create(url), this)
                .thenAccept(ws -> {
                    this.socket = ws;
                    ws.request(1);
                })
                .exceptionally(ex -> {
                    AppLog.warn("lan-ws", "WebSocket connect failed: " + shortError(ex));
                    scheduleReconnect();
                    return null;
                });
    }

    private void scheduleReconnect() {
        if (closed) return;
        if (!reconnectScheduled.compareAndSet(false, true)) return;
        failures++;
        long delay = computeBackoffMs();
        reconnectExec.schedule(() -> {
            reconnectScheduled.set(false);
            openSocket();
        }, delay, TimeUnit.MILLISECONDS);
    }

    private long computeBackoffMs() {
        int attempts = Math.min(failures, 5);
        long base = 1000L;
        long backoff = base * (1L << attempts);
        long max = 15000L;
        return Math.min(backoff, max);
    }

    private static String shortError(Throwable e) {
        if (e == null) return "unknown";
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return e.getClass().getSimpleName();
        String trimmed = msg.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 117) + "..." : trimmed;
    }
}
