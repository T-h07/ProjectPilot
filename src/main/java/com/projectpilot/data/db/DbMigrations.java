package com.projectpilot.data.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class DbMigrations {

    private static final int LATEST = 1;

    private DbMigrations() {}

    static void migrate(Connection conn) throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            int version = getUserVersion(conn);

            if (version == 0) {
                SqlScriptRunner.run(conn, SchemaSql.v1());
                setUserVersion(conn, 1);
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
