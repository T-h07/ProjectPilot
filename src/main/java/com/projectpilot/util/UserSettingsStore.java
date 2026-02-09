package com.projectpilot.util;

import java.util.prefs.Preferences;

public final class UserSettingsStore {

    private final Preferences prefs = Preferences.userRoot().node("projectpilot/user-settings");

    public Settings load(String userId) {
        String key = key(userId);
        String theme = prefs.get(key + ".theme", "default");
        String density = prefs.get(key + ".density", "comfortable");
        String ownerMessage = prefs.get(key + ".ownerMessage", "none");
        return new Settings(theme, density, ownerMessage);
    }

    public void save(String userId, Settings settings) {
        if (settings == null) return;
        String key = key(userId);
        prefs.put(key + ".theme", safe(settings.theme(), "default"));
        prefs.put(key + ".density", safe(settings.density(), "comfortable"));
        prefs.put(key + ".ownerMessage", safe(settings.ownerMessageStyle(), "none"));
    }

    private static String key(String userId) {
        String v = userId == null ? "local" : userId.trim();
        if (v.isEmpty()) v = "local";
        return "user." + v;
    }

    private static String safe(String value, String fallback) {
        if (value == null) return fallback;
        String v = value.trim();
        return v.isEmpty() ? fallback : v;
    }

    public record Settings(String theme, String density, String ownerMessageStyle) {}
}
