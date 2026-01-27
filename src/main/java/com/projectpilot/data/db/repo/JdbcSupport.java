package com.projectpilot.data.db.repo;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

final class JdbcSupport {

    private JdbcSupport() {}

    static void bindNullableLong(PreparedStatement ps, int idx, Long value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.INTEGER);
        else ps.setLong(idx, value);
    }

    static Long readNullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    static int boolToInt(boolean b) {
        return b ? 1 : 0;
    }

    static boolean intToBool(int v) {
        return v != 0;
    }
}
