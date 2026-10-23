package com.collabhub.dao;

import com.collabhub.model.Record;
import java.sql.*;
import java.util.*;

public final class Sql {
  private Sql() {}

  private static void bind(PreparedStatement p, Object... args) throws SQLException {
    for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
  }

  public static List<Record> list(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement p = c.prepareStatement(sql)) {
      bind(p, args);
      try (ResultSet rs = p.executeQuery()) {
        List<Record> rows = new ArrayList<>();
        ResultSetMetaData m = rs.getMetaData();
        while (rs.next()) {
          Record row = new Record();
          for (int i = 1; i <= m.getColumnCount(); i++)
            row.put(m.getColumnLabel(i), rs.getObject(i));
          rows.add(row);
        }
        return rows;
      }
    }
  }

  public static Record one(Connection c, String sql, Object... args) throws SQLException {
    List<Record> rows = list(c, sql, args);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public static int update(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement p = c.prepareStatement(sql)) {
      bind(p, args);
      return p.executeUpdate();
    }
  }

  public static long insert(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      bind(p, args);
      p.executeUpdate();
      try (ResultSet rs = p.getGeneratedKeys()) {
        if (!rs.next()) throw new SQLException("Missing generated key");
        return rs.getLong(1);
      }
    }
  }
}
