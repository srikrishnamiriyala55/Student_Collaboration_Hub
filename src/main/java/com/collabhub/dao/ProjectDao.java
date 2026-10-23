package com.collabhub.dao;

import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.*;
import java.sql.*;
import java.util.*;

public class ProjectDao {
  public static final String SELECT =
      "SELECT p.*,s.name owner_name,(SELECT COUNT(*) FROM project_members m WHERE m.project_id=p.id) member_count,(SELECT COUNT(*) FROM tasks t WHERE t.project_id=p.id) task_count,(SELECT COUNT(*) FROM tasks t WHERE t.project_id=p.id AND t.status='COMPLETED') completed_count FROM projects p JOIN students s ON s.id=p.owner_id ";

  public List<Record> search(String q, String category, String technology, String status)
      throws Exception {
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          SELECT
              + "WHERE (p.title LIKE ? OR p.description LIKE ?) AND (?='' OR p.category=?) AND p.technologies LIKE ? AND (?='' OR p.status=?) ORDER BY p.created_at DESC,p.id DESC",
          "%" + q + "%",
          "%" + q + "%",
          category,
          category,
          "%" + technology + "%",
          status,
          status);
    }
  }

  public Record get(long id) throws Exception {
    try (Connection c = Db.open()) {
      Record p = Sql.one(c, SELECT + "WHERE p.id=?", id);
      Validation.require(p != null, "Project not found.");
      return p;
    }
  }

  public static Record lock(Connection c, long id) throws SQLException {
    Record p = Sql.one(c, "SELECT * FROM projects WHERE id=? FOR UPDATE", id);
    Validation.require(p != null, "Project not found.");
    return p;
  }

  public static boolean member(Connection c, long project, long user) throws SQLException {
    return Sql.one(
            c,
            "SELECT student_id FROM project_members WHERE project_id=? AND student_id=?",
            project,
            user)
        != null;
  }

  public static void authorize(Connection c, long project, User user, boolean owner)
      throws SQLException {
    Record p = Sql.one(c, "SELECT owner_id,status FROM projects WHERE id=?", project);
    Validation.require(p != null, "Project not found.");
    Validation.require(
        user.isAdmin()
            || (owner ? p.id("owner_id") == user.getId() : member(c, project, user.getId())),
        "You do not have permission for this project.");
  }

  public static void writable(Record p) {
    Validation.require(!"ARCHIVED".equals(p.text("status")), "This project is archived.");
  }

  public List<Record> members(long project) throws Exception {
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          "SELECT s.id,s.name,s.email,m.team_role,m.joined_at FROM project_members m JOIN students s ON s.id=m.student_id WHERE m.project_id=? ORDER BY m.joined_at,s.id",
          project);
    }
  }

  public long save(
      User user,
      long id,
      String title,
      String description,
      String requirements,
      String category,
      String technologies,
      String status,
      int capacity)
      throws Exception {
    String t = Validation.text(title, "Title", 150),
        d = Validation.text(description, "Description", 10000),
        r = Validation.text(requirements, "Requirements", 5000),
        cat = Validation.text(category, "Category", 80),
        tech = Validation.text(technologies, "Technologies", 500);
    Validation.choice(status, "OPEN", "IN_PROGRESS", "COMPLETED", "ARCHIVED");
    Validation.require(capacity >= 1 && capacity <= 100, "Team size must be 1 to 100.");
    return Db.transaction(
        c -> {
          long result = id;
          if (id == 0) {
            result =
                Sql.insert(
                    c,
                    "INSERT INTO projects(owner_id,title,description,requirements,category,technologies,status,capacity) VALUES(?,?,?,?,?,?,?,?)",
                    user.getId(),
                    t,
                    d,
                    r,
                    cat,
                    tech,
                    status,
                    capacity);
            Sql.update(
                c,
                "INSERT INTO project_members(project_id,student_id,team_role) VALUES(?,?,'OWNER')",
                result,
                user.getId());
            ActivityDao.badge(c, user.getId(), "BUILDER");
          } else {
            lock(c, id);
            authorize(c, id, user, true);
            Record count =
                Sql.one(c, "SELECT COUNT(*) n FROM project_members WHERE project_id=?", id);
            Validation.require(
                capacity >= count.id("n"), "Capacity cannot be below the existing team size.");
            Sql.update(
                c,
                "UPDATE projects SET title=?,description=?,requirements=?,category=?,technologies=?,status=?,capacity=? WHERE id=?",
                t,
                d,
                r,
                cat,
                tech,
                status,
                capacity,
                id);
          }
          ActivityDao.log(c, user.getId(), id == 0 ? "Created project" : "Updated project", result);
          return result;
        });
  }

  public void archive(User user, long project) throws Exception {
    Db.transaction(
        c -> {
          lock(c, project);
          authorize(c, project, user, true);
          Sql.update(c, "UPDATE projects SET status='ARCHIVED' WHERE id=?", project);
          ActivityDao.log(c, user.getId(), "Archived project", project);
          return null;
        });
  }

  public List<Record> recommendations(long user) throws Exception {
    try (Connection c = Db.open()) {
      Record profile = Sql.one(c, "SELECT skills,interests FROM students WHERE id=?", user);
      Set<String> skills = tokens(profile.text("skills")),
          interests = tokens(profile.text("interests"));
      List<Record> rows =
          Sql.list(
              c,
              SELECT
                  + "WHERE p.status='OPEN' AND (SELECT COUNT(*) FROM project_members m WHERE m.project_id=p.id)<p.capacity AND NOT EXISTS(SELECT 1 FROM project_members m WHERE m.project_id=p.id AND m.student_id=?) ORDER BY p.created_at DESC",
              user);
      for (Record p : rows) {
        Set<String> matches = tokens(p.text("technologies"));
        matches.retainAll(skills);
        boolean interest = interests.contains(p.text("category").toLowerCase(Locale.ROOT));
        p.put("match_score", matches.size() * 3 + (interest ? 2 : 0));
        p.put(
            "reason",
            matches.isEmpty() && !interest
                ? "Open project with available team places"
                : (matches.isEmpty() ? "" : ("Matching skills: " + String.join(", ", matches)))
                    + (interest ? " · Matches your interests" : ""));
      }
      rows.sort((a, b) -> Long.compare(b.id("match_score"), a.id("match_score")));
      return rows;
    }
  }

  private static Set<String> tokens(String s) {
    Set<String> result = new TreeSet<>();
    for (String token : s.toLowerCase(Locale.ROOT).split(",")) {
      if (!token.trim().isEmpty()) result.add(token.trim());
    }
    return result;
  }
}
