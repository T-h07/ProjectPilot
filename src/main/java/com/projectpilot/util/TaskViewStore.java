package com.projectpilot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.prefs.Preferences;

public final class TaskViewStore {

    private final Preferences prefs = Preferences.userRoot().node("projectpilot/task-views");
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<TaskViewData> load(String userId) {
        String key = key(userId);
        String json = prefs.get(key, "[]");
        try {
            return mapper.readValue(json, new TypeReference<List<TaskViewData>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public void save(String userId, List<TaskViewData> views) {
        String key = key(userId);
        List<TaskViewData> out = views == null ? List.of() : views;
        try {
            prefs.put(key, mapper.writeValueAsString(out));
        } catch (Exception ignored) {
        }
    }

    private String key(String userId) {
        String v = userId == null ? "local" : userId.trim();
        if (v.isEmpty()) v = "local";
        return "views." + v;
    }

    public record TaskViewData(
            String id,
            String name,
            String query,
            boolean showDone,
            String status,
            String priority,
            String dueRange,
            boolean assignedToMe
    ) {}
}
