package com.collabhub.util;

import com.collabhub.dao.Sql;
import java.io.Console;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import org.mindrot.jbcrypt.BCrypt;

/** Run from a trusted local terminal, never through an HTTP endpoint. */
public final class AdminBootstrap {
  public static void main(String[] args) throws Exception {
    Console console = System.console();
    if (console == null)
      throw new IllegalStateException(
          "Run from an interactive terminal so the password can be entered privately.");
    Db.initialize();
    String name = Validation.text(console.readLine("Administrator name: "), "Name", 100),
        email =
            Validation.text(console.readLine("Administrator email: "), "Email", 190)
                .toLowerCase(java.util.Locale.ROOT);
    Validation.require(email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"), "Invalid email.");
    char[] password = console.readPassword("New password (at least 12 characters): ");
    if (password == null) {
      console.printf("Administrator setup cancelled.%n");
      return;
    }
    try {
      String p = new String(password);
      Validation.require(
          p.length() >= 12 && p.getBytes("UTF-8").length <= 72,
          "Use 12+ characters and at most 72 UTF-8 bytes.");
      String hash = BCrypt.hashpw(p, BCrypt.gensalt(12));
      try (Connection guard = Db.open()) {
        String lock =
            "collabhub:admin:"
                + java.util.UUID.nameUUIDFromBytes(
                    guard.getCatalog().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (PreparedStatement acquire = guard.prepareStatement("SELECT GET_LOCK(?,30)")) {
          acquire.setString(1, lock);
          try (ResultSet result = acquire.executeQuery()) {
            Validation.require(
                result.next() && result.getInt(1) == 1,
                "Another administrator setup is running. Please retry.");
          }
        }
        try {
          Db.transaction(
              c -> {
                Validation.require(
                    Sql.one(c, "SELECT id FROM students WHERE role='ADMIN' FOR UPDATE") == null,
                    "An administrator already exists. Manage additional administrators through trusted database administration.");
                Sql.update(
                    c,
                    "INSERT INTO students(name,email,password_hash,role) VALUES(?,?,?,'ADMIN')",
                    name,
                    email,
                    hash);
                return null;
              });
        } finally {
          try (PreparedStatement release = guard.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            release.setString(1, lock);
            release.execute();
          }
        }
      }
      console.printf("Administrator created. Sign in through /login.%n");
    } finally {
      Arrays.fill(password, '\0');
    }
  }
}
