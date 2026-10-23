package com.collabhub.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Non-destructive first-run setup using the same SQL script as manual installation. */
public final class DatabaseInitializer {
  private static final Pattern URL =
      Pattern.compile("^(jdbc:mysql://[^/?#]+/)([A-Za-z0-9_]+)(\\?[^#]*)?$");

  private DatabaseInitializer() {}

  static String[] target(String url) {
    Matcher match = URL.matcher(url == null ? "" : url);
    Validation.require(match.matches(),
        "DB_URL must use jdbc:mysql://host:port/database with a simple database name.");
    Validation.require(match.group(2).length() <= 64, "Database names must be at most 64 characters.");
    return new String[] {
      match.group(1) + (match.group(3) == null ? "" : match.group(3)), match.group(2)
    };
  }

  static List<String> statements(String database) throws Exception {
    Validation.require(database.matches("[A-Za-z0-9_]+"), "Invalid database name.");
    String script;
    try (InputStream in = DatabaseInitializer.class.getResourceAsStream("/database/schema.sql")) {
      if (in == null) throw new IllegalStateException("Packaged database/schema.sql is missing.");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      script = new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    List<String> result = new ArrayList<>();
    // This initialization script contains only simple statements, with no routines or delimiters.
    for (String sql : script.split(";")) {
      String statement = sql.trim();
      if (statement.isEmpty()) continue;
      if (statement.startsWith("CREATE DATABASE IF NOT EXISTS student_hub ")) {
        statement = statement.replace("EXISTS student_hub ", "EXISTS `" + database + "` ");
      } else if (statement.equals("USE student_hub")) {
        statement = "USE `" + database + "`";
      }
      result.add(statement);
    }
    return result;
  }

  public static synchronized void initialize(String url) throws Exception {
    String[] target = target(url);
    String lock = "collabhub:init:" + java.util.UUID.nameUUIDFromBytes(
        target[1].getBytes(StandardCharsets.UTF_8));
    // Connect without a database so first-run setup also works when it does not yet exist.
    try (Connection connection = Db.connect(target[0]);
        PreparedStatement acquire = connection.prepareStatement("SELECT GET_LOCK(?,30)")) {
      acquire.setString(1, lock);
      try (ResultSet result = acquire.executeQuery()) {
        if (!result.next() || result.getInt(1) != 1) {
          throw new IllegalStateException("Another database initializer is busy. Retry startup.");
        }
      }
      try {
        for (String sql : statements(target[1])) {
          try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
          }
        }
      } finally {
        try (PreparedStatement release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
          release.setString(1, lock);
          release.execute();
        }
      }
    }
  }
}
