package com.collabhub.dao;

import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.*;
import java.sql.*;
import java.util.List;

public class CommunityDao {
  public List<Record> notifications(long user) throws Exception {
    try (Connection c = Db.open()) {
      return Sql.list(c, "SELECT * FROM notifications WHERE recipient_id=? ORDER BY id DESC", user);
    }
  }

  public long unread(long user) throws Exception {
    try (Connection c = Db.open()) {
      return Sql.one(
              c,
              "SELECT COUNT(*) n FROM notifications WHERE recipient_id=? AND is_read=FALSE",
              user)
          .id("n");
    }
  }

  public void read(long user, long notification) throws Exception {
    try (Connection c = Db.open()) {
      Validation.require(
          Sql.update(
                  c,
                  "UPDATE notifications SET is_read=TRUE WHERE id=? AND recipient_id=?",
                  notification,
                  user)
              == 1,
          "Notification not found.");
    }
  }

  public List<Record> students(String q) throws Exception {
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          "SELECT id,name,skills,interests,bio FROM students WHERE active=TRUE AND (name LIKE ? OR skills LIKE ?) ORDER BY name",
          "%" + q + "%",
          "%" + q + "%");
    }
  }

  public List<Record> myProjects(long user) throws Exception {
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          ProjectDao.SELECT
              + "JOIN project_members mine ON mine.project_id=p.id WHERE mine.student_id=? ORDER BY p.id DESC",
          user);
    }
  }

  public List<Record> ownedProjects(User user) throws Exception {
    try (Connection c = Db.open()) {
      return user.isAdmin()
          ? Sql.list(
              c,
              "SELECT id,title FROM projects WHERE status IN ('OPEN','IN_PROGRESS') ORDER BY title")
          : Sql.list(
              c,
              "SELECT id,title FROM projects WHERE owner_id=? AND status IN ('OPEN','IN_PROGRESS') ORDER BY title",
              user.getId());
    }
  }

  public List<Record> discussions(User user, long project) throws Exception {
    try (Connection c = Db.open()) {
      ProjectDao.authorize(c, project, user, false);
      return Sql.list(
          c,
          "SELECT d.*,s.name author_name FROM discussions d JOIN students s ON s.id=d.author_id WHERE project_id=? ORDER BY d.id DESC",
          project);
    }
  }

  public void discuss(User user, long project, String message) throws Exception {
    String text = Validation.text(message, "Message", 5000);
    Db.transaction(
        c -> {
          Record p = ProjectDao.lock(c, project);
          ProjectDao.authorize(c, project, user, false);
          ProjectDao.writable(p);
          // Admins may moderate/read; posting requires actual team membership.
          Validation.require(
              ProjectDao.member(c, project, user.getId()), "Join the team before posting.");
          Sql.update(
              c,
              "INSERT INTO discussions(project_id,author_id,message) VALUES(?,?,?)",
              project,
              user.getId(),
              text);
          for (Record m :
              Sql.list(
                  c,
                  "SELECT student_id FROM project_members WHERE project_id=? AND student_id<>?",
                  project,
                  user.getId()))
            ActivityDao.notify(
                c,
                m.id("student_id"),
                user.getName() + " posted in " + p.text("title"),
                "DISCUSSION",
                project,
                null);
          ActivityDao.badge(c, user.getId(), "CONTRIBUTOR");
          ActivityDao.log(c, user.getId(), "Posted discussion", project);
          return null;
        });
  }

  public List<Record> badges(long user) throws Exception {
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          "SELECT b.*,a.earned_at FROM student_achievements a JOIN badges b ON b.id=a.badge_id WHERE a.student_id=? ORDER BY a.earned_at DESC",
          user);
    }
  }

  public List<Record> leaderboard() throws Exception {
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          "SELECT s.id,s.name,COALESCE((SELECT SUM(b.points) FROM student_achievements a JOIN badges b ON b.id=a.badge_id WHERE a.student_id=s.id),0)+10*(SELECT COUNT(*) FROM tasks t WHERE t.assignee_id=s.id AND t.status='COMPLETED')+5*(SELECT COUNT(*) FROM projects p WHERE p.owner_id=s.id AND p.status='COMPLETED') score,(SELECT COUNT(*) FROM student_achievements a WHERE a.student_id=s.id) badge_count FROM students s WHERE s.active=TRUE ORDER BY score DESC,s.name,s.id");
    }
  }

  public List<Record> announcements() throws Exception {
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          "SELECT a.*,s.name author_name FROM announcements a JOIN students s ON s.id=a.author_id ORDER BY a.id DESC LIMIT 20");
    }
  }
}
