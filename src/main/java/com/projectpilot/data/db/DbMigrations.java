package com.projectpilot.data.db;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

final class DbMigrations {

    private static final int LATEST = 2;

    private DbMigrations() {}

    static void migrate(Connection conn) throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            int version = getUserVersion(conn);

            if (version == 0) {
                SqlScriptRunner.run(conn, SchemaSql.v2());
                setUserVersion(conn, 2);
            } else if (version == 1) {
                migrate1to2(conn);
                setUserVersion(conn, 2);
            } else if (version > LATEST) {
                throw new SQLException("Database version (" + version + ") is newer than app supports (" + LATEST + ")");
            }

            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    private static void migrate1to2(Connection conn) throws SQLException {
        // Ensure new columns exist (only add if missing)
        ensureColumn(conn, "projects", "phase_template", "TEXT NOT NULL DEFAULT 'EMPTY'");
        ensureColumn(conn, "projects", "start_date", "INTEGER");
        ensureColumn(conn, "projects", "end_date", "INTEGER");
        ensureColumn(conn, "projects", "health", "TEXT NOT NULL DEFAULT 'ON_TRACK'");
        ensureColumn(conn, "projects", "completed_date", "INTEGER");

        ensureColumn(conn, "tasks", "priority", "TEXT NOT NULL DEFAULT 'MEDIUM'");

        // indices are safe with IF NOT EXISTS
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_phases_project_sort ON phases(project_id, sort_index)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks(project_id, status)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tasks_phase_sort ON tasks(phase_id, sort_index)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_activity_at ON activity_log(at DESC)");
        }
    }

    private static void ensureColumn(Connection conn, String table, String col, String ddl) throws SQLException {
        Set<String> cols = tableColumns(conn, table);
        if (cols.contains(col.toLowerCase())) return;

        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + ddl);
        }
    }

    private static Set<String> tableColumns(Connection conn, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cols.add(rs.getString("name").toLowerCase());
            }
        }
        return cols;
    }

    private static int getUserVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void setUserVersion(Connection conn, int version) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA user_version = " + version);
        }
    }
}
