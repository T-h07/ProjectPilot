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
    private static final String ENV_DB_URL = "PROJECTPILOT_DB_URL";
    private static final String PROP_DB_URL = "projectpilot.db.url";
    private static final String ENV_DB_USER = "PROJECTPILOT_DB_USER";
    private static final String PROP_DB_USER = "projectpilot.db.user";
    private static final String ENV_DB_PASSWORD = "PROJECTPILOT_DB_PASSWORD";
    private static final String PROP_DB_PASSWORD = "projectpilot.db.password";

    private final Path dbFile;
    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;
    private final DbDialect.Kind kind;
    private volatile boolean initialized;

    private DbManager(Path dbFile) {
        this.dbFile = dbFile.toAbsolutePath().normalize();
        this.jdbcUrl = toJdbcUrl(this.dbFile);
        this.jdbcUser = null;
        this.jdbcPassword = null;
        this.kind = DbDialect.fromUrl(this.jdbcUrl);
    }

    private DbManager(String jdbcUrl, String user, String password) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) throw new DbException("Missing JDBC URL");
        this.dbFile = null;
        this.jdbcUrl = jdbcUrl.trim();
        this.jdbcUser = (user == null || user.isBlank()) ? null : user.trim();
        this.jdbcPassword = (password == null || password.isBlank()) ? null : password;
        this.kind = DbDialect.fromUrl(this.jdbcUrl);
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
                    try { conn.rollback(); } catch (SQLException rbEx) { com.projectpilot.util.AppLog.warn("db", "Rollback failed: " + (rbEx == null ? "" : rbEx.getMessage())); }
                throw ex;
            } catch (Exception ex) {
                try { conn.rollback(); } catch (SQLException rbEx) { com.projectpilot.util.AppLog.warn("db", "Rollback failed: " + (rbEx == null ? "" : rbEx.getMessage())); }
                throw new DbException("DB transaction failed", ex);
            } finally {
                try { conn.setAutoCommit(prevAutoCommit); } catch (SQLException rbEx) { com.projectpilot.util.AppLog.warn("db", "Failed to restore autoCommit: " + (rbEx == null ? "" : rbEx.getMessage())); }
            }
        } catch (SQLException e) {
            throw new DbException("Failed to open DB connection for transaction", e);
        }
    }

    public void tx(Consumer<Connection> work) {
        tx(conn -> { work.accept(conn); return null; });
    }


    public static DbManager defaultManager() {
        String url = sys(PROP_DB_URL, ENV_DB_URL);
        if (url != null && !url.isBlank()) {
            String user = sys(PROP_DB_USER, ENV_DB_USER);
            String pass = sys(PROP_DB_PASSWORD, ENV_DB_PASSWORD);
            return new DbManager(url, user, pass);
        }

        String override = sys(PROP_DB_PATH, ENV_DB_PATH);

        Path file = (override != null && !override.isBlank())
                ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".projectpilot", "projectpilot.db");

        return new DbManager(file);
    }

    public Path dbFile() {
        return dbFile;
    }

    public String describe() {
        if (dbFile != null) return dbFile.toString();
        return jdbcUrl;
    }

    public synchronized void init() {
        if (initialized) return;

        ensureParentDirExists();

        try (Connection conn = openConnection()) {
            DbMigrations.migrate(conn);
            initialized = true;
        } catch (SQLException e) {
            String where = (dbFile != null) ? dbFile.toString() : jdbcUrl;
            throw new DbException("Failed to initialize database at: " + where, e);
        }
    }

    public Connection openConnection() throws SQLException {
        Connection conn = (jdbcUser == null)
                ? DriverManager.getConnection(jdbcUrl)
                : DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);

        // Connection-scoped settings (apply on every new connection).
        if (kind == DbDialect.Kind.SQLITE) {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA synchronous = NORMAL");
                st.execute("PRAGMA busy_timeout = 5000");
            }
        }

        return conn;
    }

    private void ensureParentDirExists() {
        if (dbFile == null) return;
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

    private static String sys(String propKey, String envKey) {
        String v = System.getProperty(propKey);
        if (v == null || v.isBlank()) v = System.getenv(envKey);
        return v;
    }
}
