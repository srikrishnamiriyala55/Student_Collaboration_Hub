package com.collabhub.dao;

import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.*;
import java.sql.*;
import java.util.List;

public class TeamRequestDao {
  public long send(User sender, long project, long recipient) throws Exception {
    Validation.require(sender.getId() != recipient, "You cannot invite yourself.");
    return Db.transaction(
        c -> {
          Record p = ProjectDao.lock(c, project);
          ProjectDao.authorize(c, project, sender, true);
          ProjectDao.writable(p);
          Validation.require(
              "OPEN".equals(p.text("status")) || "IN_PROGRESS".equals(p.text("status")),
              "This project is not accepting teammates.");
          Record student =
              Sql.one(c, "SELECT id FROM students WHERE id=? AND active=TRUE", recipient);
          Validation.require(student != null, "Student not found or inactive.");
          Validation.require(
              !ProjectDao.member(c, project, recipient), "This student is already a teammate.");
          Validation.require(
              Sql.one(
                      c,
                      "SELECT id FROM team_requests WHERE project_id=? AND sender_id=? AND recipient_id=? AND status IN ('PENDING','ACCEPTED')",
                      project,
                      sender.getId(),
                      recipient)
                  == null,
              "An invitation is already pending or accepted.");
          Validation.require(
              Sql.one(c, "SELECT COUNT(*) n FROM project_members WHERE project_id=?", project)
                      .id("n")
                  < p.id("capacity"),
              "The project team is full.");
          long id =
              Sql.insert(
                  c,
                  "INSERT INTO team_requests(project_id,sender_id,recipient_id) VALUES(?,?,?)",
                  project,
                  sender.getId(),
                  recipient);
          ActivityDao.notify(
              c,
              recipient,
              sender.getName() + " invited you to " + p.text("title"),
              "TEAM_REQUEST",
              project,
              id);
          ActivityDao.log(c, sender.getId(), "Invited teammate", project);
          return id;
        });
  }

  public void respond(User recipient, long request, boolean accept) throws Exception {
    Db.transaction(
        c -> {
          // Always lock project before request, consistent with invitation creation.
          Record hint =
              Sql.one(
                  c,
                  "SELECT project_id FROM team_requests WHERE id=? AND recipient_id=?",
                  request,
                  recipient.getId());
          Validation.require(hint != null, "Invitation not found or not addressed to you.");
          long project = hint.id("project_id");
          Record p = ProjectDao.lock(c, project);
          Record r = Sql.one(c, "SELECT * FROM team_requests WHERE id=? FOR UPDATE", request);
          Validation.require(
              r != null && r.id("recipient_id") == recipient.getId(),
              "This invitation is private.");
          Validation.require(
              "PENDING".equals(r.text("status")), "This invitation has already been processed.");
          if (accept) {
            ProjectDao.writable(p);
            Validation.require(!"COMPLETED".equals(p.text("status")), "This project is completed.");
            Validation.require(
                Sql.one(c, "SELECT id FROM students WHERE id=? AND active=TRUE", recipient.getId())
                    != null,
                "Your account is inactive.");
            if (!ProjectDao.member(c, project, recipient.getId())) {
              Validation.require(
                  Sql.one(c, "SELECT COUNT(*) n FROM project_members WHERE project_id=?", project)
                          .id("n")
                      < p.id("capacity"),
                  "The project team is full.");
              Sql.update(
                  c,
                  "INSERT INTO project_members(project_id,student_id) VALUES(?,?)",
                  project,
                  recipient.getId());
            }
            ActivityDao.badge(c, recipient.getId(), "TEAM_PLAYER");
          }
          Sql.update(
              c,
              "UPDATE team_requests SET status=?,responded_at=CURRENT_TIMESTAMP WHERE id=?",
              accept ? "ACCEPTED" : "REJECTED",
              request);
          ActivityDao.notify(
              c,
              r.id("sender_id"),
              recipient.getName()
                  + (accept ? " accepted" : " declined")
                  + " your invitation to "
                  + p.text("title"),
              accept ? "REQUEST_ACCEPTED" : "REQUEST_REJECTED",
              project,
              request);
          ActivityDao.log(
              c,
              recipient.getId(),
              accept ? "Accepted invitation" : "Declined invitation",
              project);
          return null;
        });
  }

  public List<Record> incoming(long user) throws Exception {
    try (Connection c = Db.open()) {
      return Sql.list(
          c,
          "SELECT r.*,s.name sender_name,p.title project_title FROM team_requests r JOIN students s ON s.id=r.sender_id JOIN projects p ON p.id=r.project_id WHERE r.recipient_id=? ORDER BY r.created_at DESC,r.id DESC",
          user);
    }
  }
}
