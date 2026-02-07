package com.projectpilot.chat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectpilot.lan.LanClient;
import com.projectpilot.lan.dto.ChatDirectRequest;
import com.projectpilot.lan.dto.ChatSendRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public final class LanChatService implements ChatService {

    private final LanClient client;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public LanChatService(LanClient client) {
        this.client = client;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public List<ChatThread> listThreads(String memberId) {
        HttpRequest request = baseRequest("/api/chat/threads")
                .GET()
                .build();
        return readList(request, ChatThread[].class);
    }

    @Override
    public List<ChatUser> listUsers(String excludeMemberId) {
        HttpRequest request = baseRequest("/api/chat/users")
                .GET()
                .build();
        List<ChatUser> users = readList(request, ChatUser[].class);
        if (excludeMemberId == null || excludeMemberId.isBlank()) return users;
        return users.stream().filter(u -> !excludeMemberId.equals(u.id())).toList();
    }

    @Override
    public ChatThread getOrCreateDirect(String memberId, String otherMemberId) {
        try {
            String json = mapper.writeValueAsString(new ChatDirectRequest(otherMemberId));
            HttpRequest request = baseRequest("/api/chat/direct")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return read(request, ChatThread.class);
        } catch (Exception e) {
            throw new IllegalStateException("Direct chat failed", e);
        }
    }

    @Override
    public List<ChatMessage> listMessages(String threadId, String viewerId, int limit) {
        String tid = threadId == null ? "" : threadId.trim();
        if (tid.isBlank()) return List.of();
        int lim = limit <= 0 ? 100 : limit;
        String q = "/api/chat/messages?threadId=" + url(tid) + "&limit=" + lim;
        HttpRequest request = baseRequest(q)
                .GET()
                .build();
        return readList(request, ChatMessage[].class);
    }

    @Override
    public ChatMessage sendMessage(String threadId, String senderId, String body) {
        try {
            String json = mapper.writeValueAsString(new ChatSendRequest(threadId, body));
            HttpRequest request = baseRequest("/api/chat/messages")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return read(request, ChatMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException("Send message failed", e);
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        String token = client.token();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Not logged in");
        }
        String baseUrl = client.baseUrl();
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("X-PP-Token", token);
    }

    private <T> T read(HttpRequest request, Class<T> type) {
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException(resp.body() == null ? "Request failed" : resp.body());
            }
            return mapper.readValue(resp.body(), type);
        } catch (Exception e) {
            throw new IllegalStateException("Request failed", e);
        }
    }

    private <T> List<T> readList(HttpRequest request, Class<T[]> type) {
        T[] out = read(request, type);
        return out == null ? List.of() : Arrays.asList(out);
    }

    private static String url(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
