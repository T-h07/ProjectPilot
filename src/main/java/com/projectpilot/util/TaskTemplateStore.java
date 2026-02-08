package com.projectpilot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public final class TaskTemplateStore {

    private final Preferences prefs = Preferences.userRoot().node("projectpilot/task-templates");
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<TaskTemplateData> load(String userId) {
        String key = key(userId);
        String json = prefs.get(key, "[]");
        try {
            return mapper.readValue(json, new TypeReference<List<TaskTemplateData>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public void save(String userId, List<TaskTemplateData> templates) {
        String key = key(userId);
        List<TaskTemplateData> out = templates == null ? List.of() : templates;
        try {
            prefs.put(key, mapper.writeValueAsString(out));
        } catch (Exception ignored) {
        }
    }

    private String key(String userId) {
        String v = userId == null ? "local" : userId.trim();
        if (v.isEmpty()) v = "local";
        return "templates." + v;
    }

    public record TaskTemplateData(
            String id,
            String name,
            String title,
            String description,
            TaskStatus status,
            Priority priority,
            Integer dueOffsetDays,
            List<ChecklistItemData> checklist
    ) {}

    public record ChecklistItemData(String text, boolean done) {
        public static List<ChecklistItemData> copyOf(List<ChecklistItemData> src) {
            if (src == null) return List.of();
            List<ChecklistItemData> out = new ArrayList<>();
            for (ChecklistItemData item : src) {
                if (item != null) out.add(new ChecklistItemData(item.text(), item.done()));
            }
            return out;
        }
    }
}
