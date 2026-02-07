package com.projectpilot.lan;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.lan.dto.WsMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

public final class LanWsClient implements WebSocket.Listener {

    private final String wsUrl;
    private final Runnable onRefresh;
    private final HttpClient http;
    private final ObjectMapper mapper;

    private WebSocket socket;

    public LanWsClient(String wsUrl, Runnable onRefresh) {
        this.wsUrl = wsUrl;
        this.onRefresh = onRefresh;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public void connect(String token) {
        if (wsUrl == null || wsUrl.isBlank()) return;
        if (token == null || token.isBlank()) return;
        String url = wsUrl + (wsUrl.contains("?") ? "&" : "?") + "token=" + token;
        http.newWebSocketBuilder()
                .buildAsync(URI.create(url), this)
                .thenAccept(ws -> {
                    this.socket = ws;
                    ws.request(1);
                })
                .exceptionally(ex -> {
                    System.err.println("[LAN] WS connect failed: " + ex.getMessage());
                    return null;
                });
    }

    public void close() {
        if (socket != null) {
            socket.abort();
            socket = null;
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
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
        System.err.println("[LAN] WS error: " + error.getMessage());
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
}
