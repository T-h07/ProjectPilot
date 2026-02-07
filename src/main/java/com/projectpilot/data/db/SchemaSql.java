package com.projectpilot.data.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class SchemaSql {

    private SchemaSql() {}

    static String v1() { return readResource("/db/schema_v1.sql"); }
    static String v2() { return readResource("/db/schema_v2.sql"); }
    static String v3() { return readResource("/db/schema_v3.sql"); }
    static String v5() { return readResource("/db/schema_v5.sql"); }
    static String v6() { return readResource("/db/schema_v6.sql"); }

    private static String readResource(String path) {
        try (InputStream in = SchemaSql.class.getResourceAsStream(path)) {
            if (in == null) throw new DbException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DbException("Failed to read resource: " + path, e);
        }
    }
}
