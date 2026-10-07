package com.campusos;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Tiny JDBC helper. Configure with env vars CAMPUSOS_DB_URL / _USER / _PASS. */
public class Db {
    static final String URL = env("CAMPUSOS_DB_URL",
        "jdbc:mysql://localhost:3306/campusos?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata");
    static final String USER = env("CAMPUSOS_DB_USER", "root");
    static final String PASS = env("CAMPUSOS_DB_PASS", "root");

    static {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) { throw new RuntimeException(e); }
    }

    static String env(String k, String d) { String v = System.getenv(k); return v == null ? d : v; }

    static Connection conn() throws SQLException { return DriverManager.getConnection(URL, USER, PASS); }

    static void bind(PreparedStatement ps, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
    }

    /** SELECT -> list of column-name -> value maps (dates become strings, decimals become doubles). */
    static List<Map<String, Object>> rows(String sql, Object... p) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof BigDecimal) v = ((BigDecimal) v).doubleValue();
                        else if (v instanceof java.util.Date || v instanceof java.time.temporal.TemporalAccessor) v = v.toString();
                        m.put(md.getColumnLabel(i), v);
                    }
                    out.add(m);
                }
                return out;
            }
        }
    }

    static Map<String, Object> one(String sql, Object... p) throws SQLException {
        List<Map<String, Object>> r = rows(sql, p);
        return r.isEmpty() ? null : r.get(0);
    }

    /** INSERT -> generated id. */
    static long insert(String sql, Object... p) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { return k.next() ? k.getLong(1) : 0; }
        }
    }

    /** UPDATE / DELETE -> affected rows. */
    static int update(String sql, Object... p) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            return ps.executeUpdate();
        }
    }
}
