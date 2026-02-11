package com.projectpilot.lan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.lan.dto.MeetingSignal;
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
    private final Map<WebSocket, MeetingPeer> meetingPeers = new ConcurrentHashMap<>();
    private final Map<String, HostInfo> meetingHosts = new ConcurrentHashMap<>();

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
        handleMeetingLeave(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        MeetingSignal signal = parseMeetingSignal(message);
        if (signal == null || signal.type() == null) return;
        if (!signal.type().startsWith("meet-")) return;
        handleMeetingSignal(conn, signal);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            authed.remove(conn);
            handleMeetingLeave(conn);
        }
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
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("lan-ws", "broadcast refresh failed: " + (e == null ? "" : e.getMessage())); }
    }

    public int connectedCount() {
        return authed.size();
    }

    public int port() {
        return port;
    }

    private MeetingSignal parseMeetingSignal(String message) {
        if (message == null || message.isBlank()) return null;
        try {
            return mapper.readValue(message, MeetingSignal.class);
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("lan-ws", "Failed to parse meeting signal: " + (e == null ? "" : e.getMessage()));
            return null;
        }
    }

    private void handleMeetingSignal(WebSocket conn, MeetingSignal signal) {
        UserSession session = authed.get(conn);
        if (session == null) return;

        String room = safe(signal.room());
        MeetingPeer existing = meetingPeers.get(conn);
        if (room.isBlank() && existing != null) room = existing.room();
        if (room.isBlank()) return;

        String from = safe(signal.from());
        if (from.isBlank()) from = safe(session.id());
        String name = safe(signal.name());
        if (name.isBlank()) name = displayName(session);

        switch (signal.type()) {
            case "meet-join" -> {
                boolean host = "host".equalsIgnoreCase(safe(signal.payload()));
                meetingPeers.put(conn, new MeetingPeer(room, from, name));
                if (host && !meetingHosts.containsKey(room)) {
                    meetingHosts.put(room, new HostInfo(from, name));
                }
                MeetingSignal join = new MeetingSignal("meet-join", room, from, null, name, host ? "host" : null);
                broadcastToRoom(room, conn, join);
                HostInfo hostInfo = meetingHosts.get(room);
                if (hostInfo != null && !hostInfo.userId().equals(from)) {
                    send(conn, new MeetingSignal("meet-host", room, hostInfo.userId(), null, hostInfo.name(), null));
                }
            }
            case "meet-chat" -> {
                MeetingSignal chat = new MeetingSignal("meet-chat", room, from, null, name, signal.payload());
                broadcastToRoom(room, conn, chat);
            }
            case "meet-end" -> {
                HostInfo hostInfo = meetingHosts.get(room);
                if (hostInfo != null && hostInfo.userId().equals(from)) {
                    endMeeting(room, hostInfo);
                }
            }
            case "meet-leave" -> handleMeetingLeave(conn);
            default -> forwardMeetingSignal(room, conn, signal, from, name);
        }
    }

    private void forwardMeetingSignal(String room, WebSocket source, MeetingSignal signal, String from, String name) {
        String to = safe(signal.to());
        if (to.isBlank()) return;

        MeetingSignal out = new MeetingSignal(signal.type(), room, from, to, name, signal.payload());
        for (Map.Entry<WebSocket, MeetingPeer> entry : meetingPeers.entrySet()) {
            MeetingPeer peer = entry.getValue();
            if (peer == null) continue;
            if (!room.equals(peer.room())) continue;
            if (!to.equals(peer.userId())) continue;
            send(entry.getKey(), out);
        }
    }

    private void handleMeetingLeave(WebSocket conn) {
        MeetingPeer peer = meetingPeers.remove(conn);
        if (peer == null) return;
        HostInfo hostInfo = meetingHosts.get(peer.room());
        if (hostInfo != null && hostInfo.userId().equals(peer.userId())) {
            endMeeting(peer.room(), hostInfo);
            return;
        }
        MeetingSignal leave = new MeetingSignal("meet-leave", peer.room(), peer.userId(), null, peer.name(), null);
        broadcastToRoom(peer.room(), conn, leave);
    }

    private void broadcastToRoom(String room, WebSocket source, MeetingSignal signal) {
        if (room == null || room.isBlank()) return;
        for (Map.Entry<WebSocket, MeetingPeer> entry : meetingPeers.entrySet()) {
            if (source != null && entry.getKey() == source) continue;
            MeetingPeer peer = entry.getValue();
            if (peer == null || !room.equals(peer.room())) continue;
            send(entry.getKey(), signal);
        }
    }

    private void endMeeting(String room, HostInfo hostInfo) {
        if (room == null || room.isBlank()) return;
        MeetingSignal end = new MeetingSignal("meet-end", room, hostInfo.userId(), null, hostInfo.name(), null);
        broadcastToRoom(room, null, end);
        meetingHosts.remove(room);
        meetingPeers.entrySet().removeIf(entry -> {
            MeetingPeer peer = entry.getValue();
            return peer != null && room.equals(peer.room());
        });
    }

    private void send(WebSocket conn, MeetingSignal signal) {
        if (conn == null || signal == null || !conn.isOpen()) return;
        try {
            conn.send(mapper.writeValueAsString(signal));
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("lan-ws", "Failed to send meeting signal: " + (e == null ? "" : e.getMessage())); }
    }

    private static String displayName(UserSession session) {
        if (session == null) return "User";
        String dn = safe(session.displayName());
        if (!dn.isBlank()) return dn;
        String un = safe(session.username());
        return un.isBlank() ? "User" : un;
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private record MeetingPeer(String room, String userId, String name) {}
    private record HostInfo(String userId, String name) {}

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
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("lan-ws", "Failed to extract token: " + (e == null ? "" : e.getMessage())); }
        return null;
    }
}
