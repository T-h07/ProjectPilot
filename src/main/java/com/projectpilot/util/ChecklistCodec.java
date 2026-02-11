package com.projectpilot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectpilot.model.ChecklistItem;

import java.util.ArrayList;
import java.util.List;

public final class ChecklistCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ChecklistCodec() {}

    public static String encode(List<ChecklistItem> items) {
        List<ChecklistItemData> out = new ArrayList<>();
        if (items != null) {
            for (ChecklistItem item : items) {
                if (item == null) continue;
                out.add(new ChecklistItemData(item.getId(), item.getText(), item.isDone()));
            }
        }
        try {
            return MAPPER.writeValueAsString(out);
        } catch (Exception e) {
            AppLog.warn("checklist", "Failed to encode checklist: " + e.getMessage());
            return "[]";
        }
    }

    public static List<ChecklistItem> decode(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<ChecklistItemData> list = MAPPER.readValue(json, new TypeReference<List<ChecklistItemData>>() {});
            List<ChecklistItem> out = new ArrayList<>();
            for (ChecklistItemData data : list) {
                if (data == null) continue;
                out.add(new ChecklistItem(data.id(), data.text(), data.done()));
            }
            return out;
        } catch (Exception e) {
            AppLog.warn("checklist", "Failed to decode checklist: " + e.getMessage());
            return List.of();
        }
    }

    private record ChecklistItemData(String id, String text, boolean done) {}
}
