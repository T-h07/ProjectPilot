package com.projectpilot.data.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class SchemaSql {

    private SchemaSql() {}

    static String v1(java.sql.Connection conn) { return readResource("/db/schema_v1.sql"); }
    static String v2(java.sql.Connection conn) { return readResource("/db/schema_v2.sql"); }
    static String v3(java.sql.Connection conn) { return readResource(path(conn, "/db/schema_v3.sql", "/db/schema_pg_v3.sql")); }
    static String v5(java.sql.Connection conn) { return readResource(path(conn, "/db/schema_v5.sql", "/db/schema_pg_v5.sql")); }
    static String v6(java.sql.Connection conn) { return readResource(path(conn, "/db/schema_v6.sql", "/db/schema_pg_v6.sql")); }
    static String v8(java.sql.Connection conn) { return readResource(path(conn, "/db/schema_v8.sql", "/db/schema_pg_v8.sql")); }

    private static String readResource(String path) {
        try (InputStream in = SchemaSql.class.getResourceAsStream(path)) {
            if (in == null) throw new DbException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DbException("Failed to read resource: " + path, e);
        }
    }

    private static String path(java.sql.Connection conn, String sqlitePath, String pgPath) {
        DbDialect.Kind kind = DbDialect.from(conn);
        if (kind == DbDialect.Kind.POSTGRES) return pgPath;
        return sqlitePath;
    }
}
