package com.projectpilot.lan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.lan.dto.WsMessage;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;

public final class LanWsServer extends WebSocketServer {

    private final LanSessionRegistry sessions;
    private final ObjectMapper mapper;
    private final int port;
    private final Map<WebSocket, UserSession> authed = new ConcurrentHashMap<>();

    public LanWsServer(int port, LanSessionRegistry sessions) {
        this(port, sessions, null);
    }

    public LanWsServer(int port, LanSessionRegistry sessions, SSLContext sslContext) {
        super(new InetSocketAddress(port));
        this.port = port;
        this.sessions = sessions;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        if (sslContext != null) {
            setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String token = extractToken(conn);
        UserSession session = sessions.get(token);
        if (session == null) {
            conn.close(1008, "Unauthorized");
            return;
        }
        authed.put(conn, session);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        authed.remove(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // server is push-only for now
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) authed.remove(conn);
    }

    @Override
    public void onStart() {
        // no-op
    }

    public void broadcastRefresh() {
        if (authed.isEmpty()) return;
        try {
            String payload = mapper.writeValueAsString(WsMessage.refresh());
            for (WebSocket conn : authed.keySet()) {
                if (conn != null && conn.isOpen()) conn.send(payload);
            }
        } catch (Exception ignored) {
        }
    }

    public int connectedCount() {
        return authed.size();
    }

    public int port() {
        return port;
    }

    private String extractToken(WebSocket conn) {
        try {
            String desc = conn.getResourceDescriptor();
            if (desc == null || desc.isBlank()) return null;
            URI uri = new URI(desc);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) return null;
            for (String part : query.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) return kv[1];
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
