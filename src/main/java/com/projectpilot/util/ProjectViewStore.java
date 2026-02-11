package com.projectpilot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.prefs.Preferences;

public final class ProjectViewStore {

    private final Preferences prefs = Preferences.userRoot().node("projectpilot/project-views");
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<ProjectViewData> load(String userId) {
        String key = key(userId);
        String json = prefs.get(key, "[]");
        try {
            return mapper.readValue(json, new TypeReference<List<ProjectViewData>>() {});
        } catch (Exception e) {
            AppLog.warn("projectview", "Failed to load project views: " + e.getMessage());
            return List.of();
        }
    }

    public void save(String userId, List<ProjectViewData> views) {
        String key = key(userId);
        List<ProjectViewData> out = views == null ? List.of() : views;
        try {
            prefs.put(key, mapper.writeValueAsString(out));
        } catch (Exception e) {
            AppLog.warn("projectview", "Failed to save project views: " + (e == null ? "" : e.getMessage()));
        }
    }

    private String key(String userId) {
        String v = userId == null ? "local" : userId.trim();
        if (v.isEmpty()) v = "local";
        return "views." + v;
    }

    public record ProjectViewData(
            String id,
            String name,
            String query,
            String scope,
            String health
    ) {}
}
