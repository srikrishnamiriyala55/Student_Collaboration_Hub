package com.collabhub;

import static org.junit.Assert.*;

import com.collabhub.dao.*;
import com.collabhub.model.User;
import com.collabhub.util.Db;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.*;
import java.util.regex.*;
import org.apache.catalina.startup.Tomcat;
import org.junit.*;

public class WebTest {
  private static final String PASSWORD = "WebTestPassword123!";

  private static class Client {
    String cookie = "", csrf = "", body = "";
    int status;
    String location = "";

    void call(String method, String path, String data) throws Exception {
      call(
          method,
          path,
          data == null ? null : data.getBytes(StandardCharsets.UTF_8),
          "application/x-www-form-urlencoded");
    }

    void call(String method, String path, byte[] data, String type) throws Exception {
      HttpURLConnection c =
          (HttpURLConnection)
              new URL("http://127.0.0.1:18089/Student_Collaboration_Hub" + path).openConnection();
      c.setInstanceFollowRedirects(false);
      c.setRequestMethod(method);
      c.setConnectTimeout(5000);
      c.setReadTimeout(60000);
      if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
      if (data != null) {
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", type);
        try (OutputStream out = c.getOutputStream()) {
          out.write(data);
        }
      }
      status = c.getResponseCode();
      location = c.getHeaderField("Location");
      String set = c.getHeaderField("Set-Cookie");
      if (set != null && set.startsWith("JSESSIONID=")) cookie = set.split(";")[0];
      InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (in != null) {
        try (InputStream source = in) {
          byte[] b = new byte[4096];
          int n;
          while ((n = source.read(b)) != -1) out.write(b, 0, n);
        }
      }
      body = new String(out.toByteArray(), StandardCharsets.UTF_8);
      Matcher m = Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(body);
      if (m.find()) csrf = m.group(1);
      c.disconnect();
    }

    void get(String path) throws Exception {
      call("GET", path, (String) null);
    }

    void post(String path, String... values) throws Exception {
      StringBuilder b = new StringBuilder("csrf=" + enc(csrf));
      for (int i = 0; i < values.length; i += 2)
        b.append('&').append(enc(values[i])).append('=').append(enc(values[i + 1]));
      call("POST", path, b.toString());
    }

    void login(String email) throws Exception {
      get("/login");
      assertEquals(body, 200, status);
      post("/login", "email", email, "password", PASSWORD);
      assertEquals(body, 302, status);
      get("/dashboard");
      assertEquals(body, 200, status);
    }

    private static String enc(String s) throws Exception {
      return URLEncoder.encode(s, "UTF-8");
    }
  }

  @Test
  public void httpWorkflowAndEveryPage() throws Exception {
    Assume.assumeTrue("true".equals(System.getenv("COLLABHUB_INTEGRATION")));
    com.collabhub.util.TestDatabase.requireIsolated();
    String suffix = UUID.randomUUID().toString(),
        emailA = "web-a" + suffix + "@example.test",
        emailB = "web-b" + suffix + "@example.test",
        emailC = "web-c" + suffix + "@example.test";
    UserDao dao = new UserDao();
    long a = dao.register("Web Owner", emailA, PASSWORD, PASSWORD),
        b = dao.register("Web Member", emailB, PASSWORD, PASSWORD),
        outsider = dao.register("Web Outsider", emailC, PASSWORD, PASSWORD);
    Tomcat server = DevServer.start(18089, "runtime/test-tomcat");
    try {
      Client ca = new Client(), cb = new Client(), cc = new Client();
      ca.get("/home");
      assertEquals(ca.body, 200, ca.status);
      ca.get("/");
      assertEquals(ca.body, 200, ca.status);
      ca.get("/register");
      assertEquals(ca.body, 200, ca.status);
      ca.get("/dashboard");
      assertEquals(302, ca.status);
      ca.call("POST", "/register", "name=forged");
      assertEquals(403, ca.status);
      Client registration = new Client();
      registration.get("/register");
      String registeredEmail = "http-registration-" + suffix + "@example.test";
      registration.post(
          "/register",
          "name",
          "HTTP Registration",
          "email",
          registeredEmail,
          "password",
          PASSWORD,
          "confirmPassword",
          PASSWORD);
      assertEquals(302, registration.status);
      registration.login(registeredEmail);
      registration.get("/profile");
      assertTrue(registration.body.contains(registeredEmail));
      ca.login(emailA);
      cb.login(emailB);
      cc.login(emailC);
      ca.post(
          "/project-create",
          "title",
          "Web Integration Project " + suffix,
          "description",
          "A persisted <script>unsafe</script> description",
          "requirements",
          "Java students",
          "category",
          "Education",
          "technologies",
          "Java, MySQL",
          "status",
          "OPEN",
          "capacity",
          "4");
      assertEquals(ca.body, 302, ca.status);
      assertTrue(ca.location.contains("/project-details?id="));
      long project = Long.parseLong(ca.location.substring(ca.location.lastIndexOf('=') + 1));
      ca.get("/project-details?id=" + project);
      assertEquals(ca.body, 200, ca.status);
      assertTrue(ca.body.contains("&lt;script&gt;unsafe&lt;/script&gt;"));
      assertFalse(ca.body.contains("<script>unsafe"));
      ca.post("/team-request", "projectId", project + "", "recipientId", b + "");
      assertEquals(302, ca.status);
      cb.get("/notifications");
      assertEquals(cb.body, 200, cb.status);
      assertTrue(cb.body.contains("Accept invitation"));
      long request = new TeamRequestDao().incoming(b).get(0).id("id");
      cc.post("/team-request-accept", "requestId", request + "");
      assertEquals("PENDING", new TeamRequestDao().incoming(b).get(0).text("status"));
      cb.post("/team-request-accept", "requestId", request + "");
      cb.get("/project-details?id=" + project);
      assertEquals(cb.body, 200, cb.status);
      assertTrue(cb.body.contains("Web Member"));
      assertTrue(cb.body.contains(emailB));
      ca.post(
          "/task-create",
          "projectId",
          project + "",
          "assigneeId",
          b + "",
          "title",
          "Web task",
          "description",
          "Integration assignment",
          "priority",
          "HIGH",
          "status",
          "PENDING",
          "deadline",
          "2026-12-31");
      assertEquals(302, ca.status);
      cb.get("/tasks");
      assertEquals(cb.body, 200, cb.status);
      assertTrue(cb.body.contains("Web task"));
      cb.post("/discussions", "projectId", project + "", "message", "Discussion from HTTP");
      cb.get("/project-details?id=" + project);
      assertTrue(cb.body.contains("Discussion from HTTP"));
      // Multipart upload is CSRF protected and downloads require team membership.
      String boundary = "CollabHubBoundary",
          payload =
              "--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"csrf\"\r\n\r\n"
                  + cb.csrf
                  + "\r\n--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"projectId\"\r\n\r\n"
                  + project
                  + "\r\n--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"verification.txt\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nVerified private file\r\n--"
                  + boundary
                  + "--\r\n";
      cb.call(
          "POST",
          "/files",
          payload.getBytes(StandardCharsets.UTF_8),
          "multipart/form-data; boundary=" + boundary);
      assertEquals(cb.body, 302, cb.status);
      long file = new FileDao().list(new User(b, "Web Member", "STUDENT"), project).get(0).id("id");
      cb.get("/files?id=" + file);
      assertEquals(200, cb.status);
      assertTrue(cb.body.contains("Verified private file"));
      cc.get("/files?id=" + file);
      assertEquals(403, cc.status);
      for (String route :
          new String[] {
            "/dashboard",
            "/projects",
            "/projects?q=Web&category=Education&technology=Java&status=OPEN",
            "/project-create",
            "/project-update?id=" + project,
            "/students",
            "/notifications",
            "/tasks",
            "/profile",
            "/recommendations",
            "/leaderboard"
          }) {
        ca.get(route);
        assertEquals(route + "\n" + ca.body, 200, ca.status);
      }
      cc.get("/admin/dashboard");
      assertEquals(403, cc.status);
      try (Connection con = Db.open()) {
        Sql.update(con, "UPDATE students SET role='ADMIN' WHERE id=?", a);
      }
      ca.get("/admin/dashboard");
      assertEquals(ca.body, 200, ca.status);
      ca.post("/admin/announcement", "message", String.join("", Collections.nCopies(1000, "a")));
      ca.get("/dashboard");
      assertTrue(ca.body.contains(String.join("", Collections.nCopies(1000, "a"))));
      ca.post("/admin/student", "id", outsider + "", "active", "false");
      cc.get("/dashboard");
      assertEquals(302, cc.status);
      cb.get("/dashboard");
      cb.post("/logout");
      cb.get("/dashboard");
      assertEquals(302, cb.status);
      cb.login(emailB);
      cb.get("/project-details?id=" + project);
      assertEquals(200, cb.status);
      assertTrue(cb.body.contains(emailB));
      ca.post("/project-delete", "id", project + "");
      cb.get("/tasks");
      assertEquals(200, cb.status);
      assertTrue(cb.body.contains("Tasks are read-only"));
      assertFalse(cb.body.contains("action=\"/Student_Collaboration_Hub/task-status\""));
    } finally {
      server.stop();
      server.destroy();
    }
  }
}
