package com.collabhub.util;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;
import org.junit.Assume;
import java.sql.*;

public class DatabaseInitializerTest {
  @Test
  public void stripsDatabaseButPreservesConnectionOptions() {
    String[] target = DatabaseInitializer.target(
        "jdbc:mysql://localhost:3307/student_hub?useSSL=false&serverTimezone=UTC");
    assertEquals("jdbc:mysql://localhost:3307/?useSSL=false&serverTimezone=UTC", target[0]);
    assertEquals("student_hub", target[1]);
  }

  @Test
  public void packagedSchemaCreatesAllTablesWithoutDestructiveStatements() throws Exception {
    List<String> statements = DatabaseInitializer.statements("friend_hub");
    assertTrue(statements.get(0).startsWith("CREATE DATABASE IF NOT EXISTS `friend_hub`"));
    assertEquals("USE `friend_hub`", statements.get(1));
    assertEquals(12, statements.stream().filter(s -> s.startsWith("CREATE TABLE IF NOT EXISTS")).count());
    assertTrue(statements.stream().anyMatch(s -> s.startsWith("INSERT IGNORE INTO badges")));
    assertFalse(statements.stream().anyMatch(s -> s.matches("(?is)^(DROP|DELETE|TRUNCATE|UPDATE)\\b.*")));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsUnsafeDatabaseNames() {
    DatabaseInitializer.target("jdbc:mysql://localhost:3306/student_hub;DROP DATABASE example");
  }

  @Test
  public void firstRunCreatesSchemaAndSecondRunPreservesData() throws Exception {
    Assume.assumeTrue("true".equals(System.getenv("COLLABHUB_BOOTSTRAP_CHECK")));
    String original = Db.setting("DB_URL", "db.url");
    String[] server = DatabaseInitializer.target(original);
    String database = "collabhub_setup_check_" + java.util.UUID.randomUUID().toString().replace("-", "");
    int query = server[0].indexOf('?');
    String url = (query < 0 ? server[0] + database
        : server[0].substring(0, query) + database + server[0].substring(query));
    try {
      DatabaseInitializer.initialize(url);
      try (Connection connection = Db.connect(url); Statement sql = connection.createStatement()) {
        try (ResultSet rows = sql.executeQuery("SHOW TABLES")) {
          int count = 0;
          while (rows.next()) count++;
          assertEquals(12, count);
        }
        sql.executeUpdate("INSERT INTO students(name,email,password_hash) VALUES('Setup verification','setup@example.test','verification-only-not-a-login')");
      }
      DatabaseInitializer.initialize(url);
      try (Connection connection = Db.connect(url); Statement sql = connection.createStatement()) {
        try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM students")) {
          assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
        try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM badges")) {
          assertTrue(rows.next()); assertEquals(4, rows.getInt(1));
        }
      }
    } finally {
      // This UUID-named schema belongs only to this check; never drop an application database.
      try (Connection connection = Db.connect(server[0]); Statement sql = connection.createStatement()) {
        sql.execute("DROP DATABASE IF EXISTS `" + database + "`");
      }
    }
  }
}
