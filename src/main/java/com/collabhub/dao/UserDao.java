package com.collabhub.dao;

import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.Db;
import com.collabhub.util.Validation;
import java.sql.*;
import java.util.Locale;
import org.mindrot.jbcrypt.BCrypt;

public class UserDao {
  public long register(String name, String email, String password, String confirmation)
      throws Exception {
    final String n = Validation.text(name, "Name", 100),
        e = Validation.text(email, "Email", 190).toLowerCase(Locale.ROOT);
    Validation.require(e.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"), "Enter a valid email address.");
    Validation.require(
        Validation.validPasswordLength(password),
        "Password must be at least 8 characters and at most 72 UTF-8 bytes.");
    Validation.require(password.equals(confirmation), "Passwords do not match.");
    String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
    try (Connection c = Db.open()) {
      return Sql.insert(
          c, "INSERT INTO students(name,email,password_hash) VALUES(?,?,?)", n, e, hash);
    } catch (SQLIntegrityConstraintViolationException ex) {
      throw new IllegalArgumentException("That email is already registered.");
    }
  }

  public User login(String email, String password) throws Exception {
    Validation.require(
        Validation.validPasswordLength(password),
        "Email or password is incorrect, or the account is inactive.");
    try (Connection c = Db.open()) {
      Record r =
          Sql.one(
              c,
              "SELECT * FROM students WHERE email=?",
              email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
      if (r == null
          || !r.flag("active")
          || password == null
          || !BCrypt.checkpw(password, r.text("password_hash")))
        throw new IllegalArgumentException(
            "Email or password is incorrect, or the account is inactive.");
      return new User(r.id("id"), r.text("name"), r.text("role"));
    }
  }

  public Record profile(long id) throws Exception {
    try (Connection c = Db.open()) {
      return Sql.one(
          c,
          "SELECT id,name,email,skills,interests,bio,role,active,created_at FROM students WHERE id=?",
          id);
    }
  }

  public void profile(long id, String name, String skills, String interests, String bio)
      throws Exception {
    try (Connection c = Db.open()) {
      Sql.update(
          c,
          "UPDATE students SET name=?,skills=?,interests=?,bio=? WHERE id=?",
          Validation.text(name, "Name", 100),
          Validation.optional(skills, 500),
          Validation.optional(interests, 500),
          Validation.optional(bio, 1000),
          id);
    }
  }
}
