package com.collabhub.dao;

import java.sql.*;

public final class ActivityDao {
  private ActivityDao() {}

  public static void log(Connection c, long user, String action, long project) throws SQLException {
    Sql.update(
        c,
        "INSERT INTO activity_logs(student_id,action,project_id) VALUES(?,?,?)",
        user,
        action,
        project == 0 ? null : project);
  }

  public static void notify(
      Connection c, long user, String message, String type, long project, Long request)
      throws SQLException {
    Sql.update(
        c,
        "INSERT INTO notifications(recipient_id,message,type,project_id,request_id) VALUES(?,?,?,?,?)",
        user,
        message,
        type,
        project == 0 ? null : project,
        request);
  }

  public static void badge(Connection c, long user, String code) throws SQLException {
    Sql.update(
        c,
        "INSERT IGNORE INTO student_achievements(student_id,badge_id) SELECT ?,id FROM badges WHERE code=?",
        user,
        code);
  }
}
