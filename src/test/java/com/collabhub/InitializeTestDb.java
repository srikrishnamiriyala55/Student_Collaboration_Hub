package com.collabhub;

import java.nio.file.*;
import java.sql.*;

public final class InitializeTestDb {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    String sql =
        new String(
                Files.readAllBytes(Paths.get("database/schema.sql")),
                java.nio.charset.StandardCharsets.UTF_8)
            .replace("student_hub", "student_hub_test");
    try (Connection c =
            DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "root",
                System.getenv("DB_PASSWORD"));
        Statement s = c.createStatement()) {
      for (String statement : sql.split(";")) {
        if (!statement.trim().isEmpty()) s.execute(statement);
      }
      System.out.println("Initialized isolated student_hub_test schema without dropping data.");
    }
  }
}
