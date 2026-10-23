package com.collabhub.util;

import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

public final class Db {
  private static final Properties CONFIG = new Properties();

  static {
    try {
      Class.forName("com.mysql.cj.jdbc.Driver");
      for (String file : new String[] {"db.properties", "db-local.properties"}) {
        try (InputStream in = Db.class.getClassLoader().getResourceAsStream(file)) {
          if (in != null) CONFIG.load(in);
        }
      }
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private Db() {}

  static String setting(String env, String key) {
    String s = System.getenv(env);
    return s == null ? CONFIG.getProperty(key) : s;
  }

  static Connection connect(String url) throws SQLException {
    return DriverManager.getConnection(
        url, setting("DB_USER", "db.user"), setting("DB_PASSWORD", "db.password"));
  }

  public static void initialize() throws Exception {
    if (!"false".equalsIgnoreCase(setting("DB_INITIALIZE", "db.initialize"))) {
      DatabaseInitializer.initialize(setting("DB_URL", "db.url"));
    }
  }

  public static Connection open() throws SQLException {
    return connect(setting("DB_URL", "db.url"));
  }

  public interface Work<T> {
    T run(Connection c) throws Exception;
  }

  public static <T> T transaction(Work<T> work) throws Exception {
    try (Connection c = open()) {
      c.setAutoCommit(false);
      try {
        T result = work.run(c);
        c.commit();
        return result;
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }
}
