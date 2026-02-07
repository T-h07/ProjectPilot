package com.projectpilot.data.db;

import java.sql.Connection;

final class DbDialect {

    enum Kind { SQLITE, POSTGRES, OTHER }

    private DbDialect() {}

    static Kind from(Connection conn) {
        if (conn == null) return Kind.OTHER;
        try {
            String name = conn.getMetaData().getDatabaseProductName();
            if (name == null) return Kind.OTHER;
            String v = name.toLowerCase();
            if (v.contains("sqlite")) return Kind.SQLITE;
            if (v.contains("postgres")) return Kind.POSTGRES;
            return Kind.OTHER;
        } catch (Exception e) {
            return Kind.OTHER;
        }
    }

    static Kind fromUrl(String url) {
        if (url == null) return Kind.OTHER;
        String v = url.toLowerCase();
        if (v.startsWith("jdbc:sqlite:")) return Kind.SQLITE;
        if (v.startsWith("jdbc:postgresql:")) return Kind.POSTGRES;
        return Kind.OTHER;
    }
}
