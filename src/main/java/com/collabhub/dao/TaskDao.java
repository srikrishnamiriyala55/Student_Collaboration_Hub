package com.collabhub.dao;

import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.*;
import java.sql.*;
import java.util.List;

public class TaskDao {
  public List<Record> list(User user, Long project) throws Exception {
    try (Connection c = Db.open()) {
      String sql =
          "SELECT t.*,s.name assignee_name,p.title project_title,p.status project_status FROM tasks t JOIN students s ON s.id=t.assignee_id JOIN projects p ON p.id=t.project_id ";
      if (project != null) {
        ProjectDao.authorize(c, project, user, false);
        return Sql.list(
            c, sql + "WHERE t.project_id=? ORDER BY t.status,t.deadline,t.id DESC", project);
      }
      return Sql.list(
          c, sql + "WHERE t.assignee_id=? ORDER BY t.status,t.deadline,t.id DESC", user.getId());
    }
  }

  public void save(
      User user,
      long id,
      long project,
      long assignee,
      String title,
      String description,
      String priority,
      String status,
      String deadline)
      throws Exception {
    final String t = Validation.text(title, "Task title", 180),
        d = Validation.optional(description, 5000);
    Validation.choice(priority, "LOW", "MEDIUM", "HIGH");
    Validation.choice(status, "PENDING", "IN_PROGRESS", "COMPLETED");
    final Date date = Validation.date(deadline);
    Db.transaction(
        c -> {
          Record p = ProjectDao.lock(c, project);
          ProjectDao.authorize(c, project, user, false);
          ProjectDao.writable(p);
          Validation.require(
              ProjectDao.member(c, project, assignee), "Assignee must belong to the project.");
          Validation.require(
              Sql.one(c, "SELECT id FROM students WHERE id=? AND active=TRUE", assignee) != null,
              "Assignee must have an active account.");
          if (id == 0)
            Sql.insert(
                c,
                "INSERT INTO tasks(project_id,assignee_id,title,description,priority,status,deadline) VALUES(?,?,?,?,?,?,?)",
                project,
                assignee,
                t,
                d,
                priority,
                status,
                date);
          else {
            Validation.require(
                Sql.one(
                        c,
                        "SELECT id FROM tasks WHERE id=? AND project_id=? FOR UPDATE",
                        id,
                        project)
                    != null,
                "Task not found in this project.");
            Sql.update(
                c,
                "UPDATE tasks SET assignee_id=?,title=?,description=?,priority=?,status=?,deadline=? WHERE id=?",
                assignee,
                t,
                d,
                priority,
                status,
                date,
                id);
          }
          ActivityDao.notify(
              c,
              assignee,
              (id == 0 ? "New task: " : "Task updated: ") + t,
              "TASK_UPDATE",
              project,
              null);
          if ("COMPLETED".equals(status)) ActivityDao.badge(c, assignee, "FINISHER");
          ActivityDao.log(c, user.getId(), id == 0 ? "Created task" : "Updated task", project);
          return null;
        });
  }

  public void status(User user, long task, String status) throws Exception {
    Validation.choice(status, "PENDING", "IN_PROGRESS", "COMPLETED");
    Db.transaction(
        c -> {
          Record hint = Sql.one(c, "SELECT project_id FROM tasks WHERE id=?", task);
          Validation.require(hint != null, "Task not found.");
          long project = hint.id("project_id");
          Record p = ProjectDao.lock(c, project);
          ProjectDao.authorize(c, project, user, false);
          ProjectDao.writable(p);
          Record t = Sql.one(c, "SELECT * FROM tasks WHERE id=? FOR UPDATE", task);
          if (status.equals(t.text("status"))) return null;
          Sql.update(c, "UPDATE tasks SET status=? WHERE id=?", status, task);
          if ("COMPLETED".equals(status)) ActivityDao.badge(c, t.id("assignee_id"), "FINISHER");
          ActivityDao.notify(
              c,
              t.id("assignee_id"),
              "Task status: " + t.text("title") + " → " + status,
              "TASK_STATUS",
              project,
              null);
          ActivityDao.log(c, user.getId(), "Changed task status", project);
          return null;
        });
  }
}
