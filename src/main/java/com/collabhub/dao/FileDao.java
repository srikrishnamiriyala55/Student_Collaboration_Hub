package com.collabhub.dao;

import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.*;
import java.sql.*;
import java.util.List;

public class FileDao {
  public List<Record> list(User user, long project) throws Exception {
    try (Connection c = Db.open()) {
      ProjectDao.authorize(c, project, user, false);
      return Sql.list(
          c,
          "SELECT f.*,s.name uploader_name FROM project_files f JOIN students s ON s.id=f.uploader_id WHERE f.project_id=? ORDER BY f.id DESC",
          project);
    }
  }

  public Record get(User user, long id) throws Exception {
    try (Connection c = Db.open()) {
      Record f = Sql.one(c, "SELECT * FROM project_files WHERE id=?", id);
      Validation.require(f != null, "File not found.");
      ProjectDao.authorize(c, f.id("project_id"), user, false);
      return f;
    }
  }

  public void add(User user, long project, String name, String stored, String type, long size)
      throws Exception {
    Db.transaction(
        c -> {
          Record p = ProjectDao.lock(c, project);
          ProjectDao.authorize(c, project, user, false);
          ProjectDao.writable(p);
          Validation.require(
              ProjectDao.member(c, project, user.getId()), "Team membership required to upload.");
          Sql.update(
              c,
              "INSERT INTO project_files(project_id,uploader_id,original_name,stored_name,content_type,size_bytes) VALUES(?,?,?,?,?,?)",
              project,
              user.getId(),
              name,
              stored,
              type,
              size);
          ActivityDao.log(c, user.getId(), "Uploaded file", project);
          return null;
        });
  }
}
