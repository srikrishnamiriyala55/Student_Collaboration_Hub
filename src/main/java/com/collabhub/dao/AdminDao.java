package com.collabhub.dao;

import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.*;
import java.sql.*;
import java.util.List;

public class AdminDao {
  private void check(User user) {
    Validation.require(user.isAdmin(), "Administrator access required.");
  }

  public Record stats(User user) throws Exception {
    check(user);
    try (Connection c = Db.open()) {
      return Sql.one(
          c,
          "SELECT (SELECT COUNT(*) FROM students) students,(SELECT COUNT(*) FROM projects) projects,(SELECT COUNT(*) FROM project_members) memberships,(SELECT COUNT(*) FROM tasks) tasks,(SELECT COUNT(*) FROM team_requests WHERE status='PENDING') requests");
    }
  }

  public List<Record> students(User user, String q) throws Exception {
    check(user);
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          "SELECT id,name,email,role,active,created_at FROM students WHERE name LIKE ? OR email LIKE ? ORDER BY id DESC",
          "%" + q + "%",
          "%" + q + "%");
    }
  }

  public List<Record> activity(User user) throws Exception {
    check(user);
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          "SELECT a.*,s.name,p.title project_title FROM activity_logs a JOIN students s ON s.id=a.student_id LEFT JOIN projects p ON p.id=a.project_id ORDER BY a.id DESC LIMIT 100");
    }
  }

  public void active(User user, long student, boolean active) throws Exception {
    check(user);
    Validation.require(student != user.getId(), "You cannot deactivate your own account.");
    Db.transaction(
        c -> {
          Record s = Sql.one(c, "SELECT role FROM students WHERE id=? FOR UPDATE", student);
          Validation.require(s != null, "Student not found.");
          Validation.require(
              !"ADMIN".equals(s.text("role")),
              "Admin accounts are managed through database administration.");
          Sql.update(c, "UPDATE students SET active=? WHERE id=?", active, student);
          ActivityDao.log(
              c,
              user.getId(),
              active ? "Activated student " + student : "Deactivated student " + student,
              0);
          return null;
        });
  }

  public void announce(User user, String message) throws Exception {
    check(user);
    String text = Validation.text(message, "Announcement", 1000);
    Db.transaction(
        c -> {
          Sql.update(
              c, "INSERT INTO announcements(author_id,message) VALUES(?,?)", user.getId(), text);
          for (Record s : Sql.list(c, "SELECT id FROM students WHERE active=TRUE"))
            ActivityDao.notify(c, s.id("id"), text, "ANNOUNCEMENT", 0, null);
          ActivityDao.log(c, user.getId(), "Published announcement", 0);
          return null;
        });
  }
}
