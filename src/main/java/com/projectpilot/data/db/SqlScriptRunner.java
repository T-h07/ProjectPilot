package com.projectpilot.data.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SqlScriptRunner {

    private SqlScriptRunner() {}

    static void run(Connection conn, String script) throws SQLException {
        if (script == null || script.isBlank()) return;

        String s = script.replace("\r\n", "\n").replace('\r', '\n');

        StringBuilder stmt = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            // Handle line comments: -- comment
            if (!inSingle && !inDouble && c == '-' && i + 1 < s.length() && s.charAt(i + 1) == '-') {
                // skip to end of line
                while (i < s.length() && s.charAt(i) != '\n') i++;
                stmt.append('\n');
                continue;
            }

            if (c == '\'' && !inDouble) inSingle = !inSingle;
            if (c == '"'  && !inSingle) inDouble = !inDouble;

            if (c == ';' && !inSingle && !inDouble) {
                execIfNotBlank(conn, stmt.toString());
                stmt.setLength(0);
                continue;
            }

            stmt.append(c);
        }

        execIfNotBlank(conn, stmt.toString());
    }

    private static void execIfNotBlank(Connection conn, String sql) throws SQLException {
        String trimmed = sql.trim();
        if (trimmed.isEmpty()) return;
        try (Statement st = conn.createStatement()) {
            st.execute(trimmed);
        }
    }
}
