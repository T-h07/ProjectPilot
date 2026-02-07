package com.projectpilot.data.db;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

final class DbMigrations {

    private static final int LATEST = 8;

    private DbMigrations() {}

    static void migrate(Connection conn) throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            int version = getUserVersion(conn);

            if (version == 0) {
                SqlScriptRunner.run(conn, SchemaSql.v3(conn));
                setUserVersion(conn, 3);
                version = 3;
            }

            if (version == 1) {
                migrate1to2(conn);
                setUserVersion(conn, 2);
                version = 2;
            }

            if (version == 2) {
                migrate2to3(conn);
                setUserVersion(conn, 3);
                version = 3;
            }

            if (version == 3 || version == 4) {
                // repair step (covers early v3 DBs / stale schema_v3.sql)
                migrate2to3(conn); // idempotent: CREATE TABLE IF NOT EXISTS + ensureColumn
                migrate3to5(conn);
                migrate5to6(conn);
                migrate6to7(conn);
                migrate7to8(conn);
                setUserVersion(conn, 8);
                version = 8;
            }

            if (version == 5) {
                migrate5to6(conn);
                migrate6to7(conn);
                migrate7to8(conn);
                setUserVersion(conn, 8);
                version = 8;
            }

            if (version == 6) {
                migrate6to7(conn);
                migrate7to8(conn);
                setUserVersion(conn, 8);
                version = 8;
            }

            if (version == 7) {
                migrate7to8(conn);
                setUserVersion(conn, 8);
                version = 8;
            }

            if (version > LATEST) {
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


    // ---------------- v1 -> v2 ----------------

    private static void migrate1to2(Connection conn) throws SQLException {
        ensureColumn(conn, "projects", "phase_template", "TEXT NOT NULL DEFAULT 'EMPTY'");
        ensureColumn(conn, "projects", "start_date", "INTEGER");
        ensureColumn(conn, "projects", "end_date", "INTEGER");
        ensureColumn(conn, "projects", "health", "TEXT NOT NULL DEFAULT 'ON_TRACK'");
        ensureColumn(conn, "projects", "completed_date", "INTEGER");

        ensureColumn(conn, "tasks", "priority", "TEXT NOT NULL DEFAULT 'MEDIUM'");

        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_phases_project_sort ON phases(project_id, sort_index)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks(project_id, status)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tasks_phase_sort ON tasks(phase_id, sort_index)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_activity_at ON activity_log(at DESC)");
        }
    }

    // ---------------- v2 -> v3 ----------------

    private static void migrate2to3(Connection conn) throws SQLException {
        // 1) activity_log.actor_member_id (safe add)
        ensureColumn(conn, "activity_log", "actor_member_id", "TEXT");

        // 2) auth_users table
        try (Statement st = conn.createStatement()) {
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS auth_users (
                        member_id     TEXT PRIMARY KEY,
                        username      TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL,
                        salt          TEXT NOT NULL DEFAULT '',
                        global_role   TEXT NOT NULL DEFAULT 'USER' CHECK (global_role IN ('ADMIN','USER')),
                        is_active     INTEGER NOT NULL DEFAULT 1,
                        created_at    INTEGER NOT NULL,
                        updated_at    INTEGER NOT NULL,
                        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
                    )
                    """
            );


            st.execute("CREATE INDEX IF NOT EXISTS idx_auth_users_username ON auth_users(username)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_activity_actor_member ON activity_log(actor_member_id)");
        }
    }

    // ---------------- v3 -> v5 ----------------

    private static void migrate3to5(Connection conn) throws SQLException {
        SqlScriptRunner.run(conn, SchemaSql.v5(conn));
    }

    // ---------------- v5 -> v6 ----------------

    private static void migrate5to6(Connection conn) throws SQLException {
        SqlScriptRunner.run(conn, SchemaSql.v6(conn));
    }

    // ---------------- v6 -> v7 ----------------

    private static void migrate6to7(Connection conn) throws SQLException {
        ensureColumn(conn, "auth_users", "last_online_at", "INTEGER");
    }

    // ---------------- v7 -> v8 ----------------

    private static void migrate7to8(Connection conn) throws SQLException {
        SqlScriptRunner.run(conn, SchemaSql.v8(conn));
    }

    // ---------------- helpers ----------------

    private static void ensureColumn(Connection conn, String table, String col, String ddl) throws SQLException {
        Set<String> cols = tableColumns(conn, table);
        if (cols.contains(col.toLowerCase())) return;

        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + ddl);
        }
    }

    private static Set<String> tableColumns(Connection conn, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        String schema = null;
        try {
            schema = conn.getSchema();
        } catch (Exception ignored) {
        }

        java.sql.DatabaseMetaData meta = conn.getMetaData();
        String tableName = table == null ? null : table.toLowerCase();
        try (ResultSet rs = meta.getColumns(null, schema, tableName, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (name != null) cols.add(name.toLowerCase());
            }
        }

        if (cols.isEmpty()) {
            try (ResultSet rs = meta.getColumns(null, "public", tableName, null)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null) cols.add(name.toLowerCase());
                }
            }
        }
        return cols;
    }

    private static int getUserVersion(Connection conn) throws SQLException {
        DbDialect.Kind kind = DbDialect.from(conn);
        if (kind == DbDialect.Kind.SQLITE) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        ensureSchemaVersionTable(conn);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void setUserVersion(Connection conn, int version) throws SQLException {
        DbDialect.Kind kind = DbDialect.from(conn);
        if (kind == DbDialect.Kind.SQLITE) {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA user_version = " + version);
            }
            return;
        }

        ensureSchemaVersionTable(conn);
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM schema_version");
            st.execute("INSERT INTO schema_version(version) VALUES (" + version + ")");
        }
    }

    private static void ensureSchemaVersionTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
    }
}
