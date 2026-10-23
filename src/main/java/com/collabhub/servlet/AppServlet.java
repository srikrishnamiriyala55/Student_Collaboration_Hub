package com.collabhub.servlet;

import com.collabhub.dao.*;
import com.collabhub.filter.SecurityFilter;
import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.*;
import java.io.IOException;
import java.sql.Connection;
import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet(
    urlPatterns = {
      "/home",
      "/login",
      "/register",
      "/logout",
      "/dashboard",
      "/projects",
      "/project-details",
      "/project-create",
      "/project-update",
      "/project-delete",
      "/students",
      "/team-request",
      "/team-request-accept",
      "/team-request-reject",
      "/notifications",
      "/notification-read",
      "/tasks",
      "/task-create",
      "/task-update",
      "/task-status",
      "/discussions",
      "/profile",
      "/recommendations",
      "/leaderboard",
      "/admin/dashboard",
      "/admin/student",
      "/admin/announcement"
    })
public class AppServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private final UserDao users = new UserDao();
  private final ProjectDao projects = new ProjectDao();
  private final TeamRequestDao requests = new TeamRequestDao();
  private final CommunityDao community = new CommunityDao();
  private final TaskDao tasks = new TaskDao();
  private final AdminDao admin = new AdminDao();

  private User user(HttpServletRequest r) {
    return (User) r.getSession().getAttribute("user");
  }

  private String value(HttpServletRequest r, String key) {
    String s = r.getParameter(key);
    return s == null ? "" : s.trim();
  }

  private long id(HttpServletRequest r, String key) {
    return Validation.id(r.getParameter(key));
  }

  private void redirect(HttpServletRequest r, HttpServletResponse s, String path)
      throws IOException {
    s.sendRedirect(r.getContextPath() + path);
  }

  private void view(HttpServletRequest r, HttpServletResponse s, String page)
      throws ServletException, IOException {
    r.setAttribute("page", page);
    r.getRequestDispatcher("/WEB-INF/views/layout.jsp").forward(r, s);
  }

  protected void doGet(HttpServletRequest r, HttpServletResponse s)
      throws ServletException, IOException {
    String path = r.getServletPath();
    User u = user(r);
    try {
      if (u != null) r.setAttribute("unread", community.unread(u.getId()));
      switch (path) {
        case "/home":
          view(r, s, "home");
          break;
        case "/login":
        case "/register":
          if (u != null) redirect(r, s, "/dashboard");
          else view(r, s, path.substring(1));
          break;
        case "/dashboard":
          r.setAttribute("myProjects", community.myProjects(u.getId()));
          r.setAttribute("tasks", tasks.list(u, null));
          r.setAttribute("announcements", community.announcements());
          r.setAttribute("badges", community.badges(u.getId()));
          view(r, s, "dashboard");
          break;
        case "/projects":
          r.setAttribute(
              "projects",
              projects.search(
                  value(r, "q"), value(r, "category"), value(r, "technology"), value(r, "status")));
          view(r, s, "projects");
          break;
        case "/project-details":
          {
            long p = id(r, "id");
            Record project = projects.get(p);
            r.setAttribute("project", project);
            r.setAttribute("members", projects.members(p));
            boolean member;
            try (Connection c = Db.open()) {
              member = ProjectDao.member(c, p, u.getId());
            }
            r.setAttribute("canManage", u.isAdmin() || project.id("owner_id") == u.getId());
            r.setAttribute("isMember", member);
            r.setAttribute("canCollaborate", member || u.isAdmin());
            if (member || u.isAdmin()) {
              r.setAttribute("tasks", tasks.list(u, p));
              r.setAttribute("messages", community.discussions(u, p));
              r.setAttribute("files", new FileDao().list(u, p));
            }
            view(r, s, "project-details");
            break;
          }
        case "/project-create":
          view(r, s, "project-form");
          break;
        case "/project-update":
          {
            long p = id(r, "id");
            try (Connection c = Db.open()) {
              ProjectDao.authorize(c, p, u, true);
            }
            r.setAttribute("project", projects.get(p));
            view(r, s, "project-form");
            break;
          }
        case "/students":
          r.setAttribute("students", community.students(value(r, "q")));
          r.setAttribute("ownedProjects", community.ownedProjects(u));
          view(r, s, "students");
          break;
        case "/notifications":
          r.setAttribute("notifications", community.notifications(u.getId()));
          r.setAttribute("requests", requests.incoming(u.getId()));
          view(r, s, "notifications");
          break;
        case "/tasks":
          r.setAttribute("tasks", tasks.list(u, null));
          view(r, s, "tasks");
          break;
        case "/profile":
          r.setAttribute("profile", users.profile(u.getId()));
          r.setAttribute("badges", community.badges(u.getId()));
          view(r, s, "profile");
          break;
        case "/recommendations":
          r.setAttribute("projects", projects.recommendations(u.getId()));
          view(r, s, "recommendations");
          break;
        case "/leaderboard":
          r.setAttribute("leaders", community.leaderboard());
          view(r, s, "leaderboard");
          break;
        case "/admin/dashboard":
          r.setAttribute("stats", admin.stats(u));
          r.setAttribute("students", admin.students(u, value(r, "q")));
          r.setAttribute("projects", projects.search("", "", "", ""));
          r.setAttribute("activity", admin.activity(u));
          view(r, s, "admin");
          break;
        default:
          s.sendError(405);
      }
    } catch (IllegalArgumentException e) {
      s.setStatus(400);
      r.setAttribute("error", e.getMessage());
      view(r, s, "error");
    } catch (Exception e) {
      getServletContext().log("Page load failed: " + path, e);
      s.setStatus(503);
      r.setAttribute(
          "error",
          "Unable to load this page. Verify MySQL is running and the database is initialized.");
      view(r, s, "error");
    }
  }

  protected void doPost(HttpServletRequest r, HttpServletResponse s)
      throws ServletException, IOException {
    String path = r.getServletPath(), next = "/dashboard", message = "Changes saved.";
    User u = user(r);
    try {
      switch (path) {
        case "/register":
          users.register(
              r.getParameter("name"),
              r.getParameter("email"),
              r.getParameter("password"),
              r.getParameter("confirmPassword"));
          next = "/login";
          message = "Account created. Sign in to get started.";
          break;
        case "/login":
          {
            User signed = users.login(r.getParameter("email"), r.getParameter("password"));
            r.changeSessionId();
            r.getSession().setAttribute("user", signed);
            r.getSession().setAttribute("csrf", SecurityFilter.token());
            message = "Welcome back, " + signed.getName() + ".";
            break;
          }
        case "/logout":
          r.getSession().invalidate();
          redirect(r, s, "/login");
          return;
        case "/project-create":
        case "/project-update":
          {
            long p =
                projects.save(
                    u,
                    path.equals("/project-create") ? 0 : id(r, "id"),
                    r.getParameter("title"),
                    r.getParameter("description"),
                    r.getParameter("requirements"),
                    r.getParameter("category"),
                    r.getParameter("technologies"),
                    r.getParameter("status"),
                    Validation.capacity(value(r, "capacity")));
            next = "/project-details?id=" + p;
            break;
          }
        case "/project-delete":
          projects.archive(u, id(r, "id"));
          next = "/projects";
          message = "Project archived. Its team and history are preserved.";
          break;
        case "/team-request":
          requests.send(u, id(r, "projectId"), id(r, "recipientId"));
          next = "/students";
          message = "Invitation sent. Your teammate can respond in Notifications.";
          break;
        case "/team-request-accept":
        case "/team-request-reject":
          requests.respond(u, id(r, "requestId"), path.endsWith("accept"));
          next = "/notifications";
          message =
              path.endsWith("accept")
                  ? "Invitation accepted. You now appear in the project team."
                  : "Invitation declined.";
          break;
        case "/notification-read":
          community.read(u.getId(), id(r, "id"));
          next = "/notifications";
          break;
        case "/task-create":
        case "/task-update":
          {
            long p = id(r, "projectId");
            tasks.save(
                u,
                path.equals("/task-create") ? 0 : id(r, "id"),
                p,
                id(r, "assigneeId"),
                r.getParameter("title"),
                r.getParameter("description"),
                r.getParameter("priority"),
                r.getParameter("status"),
                r.getParameter("deadline"));
            next = "/project-details?id=" + p;
            break;
          }
        case "/task-status":
          tasks.status(u, id(r, "id"), r.getParameter("status"));
          next = "/tasks";
          break;
        case "/discussions":
          {
            long p = id(r, "projectId");
            community.discuss(u, p, r.getParameter("message"));
            next = "/project-details?id=" + p;
            break;
          }
        case "/profile":
          users.profile(
              u.getId(),
              r.getParameter("name"),
              r.getParameter("skills"),
              r.getParameter("interests"),
              r.getParameter("bio"));
          next = "/profile";
          break;
        case "/admin/student":
          admin.active(
              u,
              id(r, "id"),
              "true".equals(Validation.choice(r.getParameter("active"), "true", "false")));
          next = "/admin/dashboard";
          break;
        case "/admin/announcement":
          admin.announce(u, r.getParameter("message"));
          next = "/admin/dashboard";
          break;
        default:
          s.sendError(405);
          return;
      }
      r.getSession().setAttribute("success", message);
      redirect(r, s, next);
    } catch (IllegalArgumentException e) {
      r.getSession().setAttribute("error", e.getMessage());
      redirect(r, s, errorTarget(path));
    } catch (Exception e) {
      getServletContext().log("Action failed: " + path, e);
      r.getSession()
          .setAttribute(
              "error",
              "Unable to complete the action. Reload the page to check its current state before trying again.");
      redirect(r, s, errorTarget(path));
    }
  }

  private String errorTarget(String path) {
    switch (path) {
      case "/login":
      case "/register":
      case "/profile":
      case "/project-create":
        return path;
      case "/project-update":
        return "/projects";
      case "/team-request":
        return "/students";
      case "/team-request-accept":
      case "/team-request-reject":
      case "/notification-read":
        return "/notifications";
      case "/admin/student":
      case "/admin/announcement":
        return "/admin/dashboard";
      default:
        return "/dashboard";
    }
  }
}
