package com.projectpilot.util;

import com.projectpilot.data.db.auth.UserSession;

public final class OwnerProfile {

    private static final String OWNER_KEY = "taulanthaxhiu";

    private OwnerProfile() {}

    public static boolean isOwnerUser(UserSession session) {
        if (session == null) return false;
        if (matchesName(session.username())) return true;
        return matchesName(session.displayName());
    }

    public static boolean matchesName(String name) {
        if (name == null) return false;
        String n = normalize(name);
        return !n.isBlank() && n.equalsIgnoreCase(OWNER_KEY);
    }

    private static String normalize(String value) {
        String v = value.trim();
        v = v.replace(" ", "").replace("_", "").replace("-", "");
        return v.toLowerCase();
    }
}
