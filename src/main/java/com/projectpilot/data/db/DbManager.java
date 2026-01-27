package com.projectpilot.data.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;
import java.util.function.Function;


public final class DbManager {

    private static final String ENV_DB_PATH = "PROJECTPILOT_DB_PATH";
    private static final String PROP_DB_PATH = "projectpilot.db.path";

    private final Path dbFile;
    private final String jdbcUrl;
    private volatile boolean initialized;

    private DbManager(Path dbFile) {
        this.dbFile = dbFile.toAbsolutePath().normalize();
        this.jdbcUrl = toJdbcUrl(this.dbFile);
    }
    public <T> T tx(Function<Connection, T> work) {
        try (Connection conn = openConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T out = work.apply(conn);
                conn.commit();
                return out;
            } catch (RuntimeException ex) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw ex;
            } catch (Exception ex) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw new DbException("DB transaction failed", ex);
            } finally {
                try { conn.setAutoCommit(prevAutoCommit); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            throw new DbException("Failed to open DB connection for transaction", e);
        }
    }

    public void tx(Consumer<Connection> work) {
        tx(conn -> { work.accept(conn); return null; });
    }


    public static DbManager defaultManager() {
        String override = System.getProperty(PROP_DB_PATH);
        if (override == null || override.isBlank()) {
            override = System.getenv(ENV_DB_PATH);
        }

        Path file = (override != null && !override.isBlank())
                ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".projectpilot", "projectpilot.db");

        return new DbManager(file);
    }

    public Path dbFile() {
        return dbFile;
    }

    public synchronized void init() {
        if (initialized) return;

        ensureParentDirExists();

        try (Connection conn = openConnection()) {
            DbMigrations.migrate(conn);
            initialized = true;
        } catch (SQLException e) {
            throw new DbException("Failed to initialize database at: " + dbFile, e);
        }
    }

    public Connection openConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);

        // Connection-scoped settings (apply on every new connection).
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA synchronous = NORMAL");
            st.execute("PRAGMA busy_timeout = 5000");
        }

        return conn;
    }

    private void ensureParentDirExists() {
        Path dir = dbFile.getParent();
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new DbException("Failed to create DB directory: " + dir, e);
        }
    }

    private static String toJdbcUrl(Path dbFile) {
        // sqlite-jdbc accepts either absolute paths or :memory:
        String p = dbFile.toString();
        if (":memory:".equals(p)) return "jdbc:sqlite::memory:";
        return "jdbc:sqlite:" + p;
    }
}
