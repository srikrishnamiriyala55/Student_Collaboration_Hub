package com.collabhub;

import static org.junit.Assert.*;

import com.collabhub.dao.*;
import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;

/** Opt-in integration tests use ONLY the dedicated student_hub_test database. */
public class WorkflowTest {
  private User a, b, c;
  private long project;
  private final UserDao users = new UserDao();
  private final ProjectDao projects = new ProjectDao();
  private final TeamRequestDao requests = new TeamRequestDao();

  @Before
  public void setup() throws Exception {
    Assume.assumeTrue(
        "Set COLLABHUB_INTEGRATION=true to run MySQL integration tests",
        "true".equals(System.getenv("COLLABHUB_INTEGRATION")));
    String url = System.getenv("DB_URL");
    assertNotNull(url);
    assertTrue(
        "Tests require the isolated student_hub_test database", url.contains("/student_hub_test?"));
    String suffix = UUID.randomUUID().toString();
    a = student("Owner " + suffix, "a" + suffix);
    b = student("Member " + suffix, "b" + suffix);
    c = student("Outsider " + suffix, "c" + suffix);
    project =
        projects.save(
            a,
            0,
            "Integration project " + suffix,
            "Database workflow verification",
            "Java developer",
            "Education",
            "Java, MySQL",
            "OPEN",
            5);
  }

  private User student(String name, String email) throws Exception {
    long id = users.register(name, email + "@example.test", "TestPassword123!", "TestPassword123!");
    return new User(id, name, "STUDENT");
  }

  private void fails(Checked action) throws Exception {
    try {
      action.run();
      fail("Expected validation failure");
    } catch (IllegalArgumentException expected) {
    }
  }

  private interface Checked {
    void run() throws Exception;
  }

  @Test
  public void invitationsTasksAndPrivacy() throws Exception {
    fails(
        () ->
            users.register(
                "Duplicate",
                users.profile(a.getId()).text("email"),
                "TestPassword123!",
                "TestPassword123!"));
    fails(() -> users.login(users.profile(a.getId()).text("email"), "wrong"));
    assertEquals(
        a.getId(), users.login(users.profile(a.getId()).text("email"), "TestPassword123!").getId());
    fails(() -> requests.send(a, project, a.getId()));
    fails(() -> requests.send(c, project, b.getId()));
    fails(() -> requests.send(a, Long.MAX_VALUE, b.getId()));
    fails(() -> requests.send(a, project, Long.MAX_VALUE));
    long request = requests.send(a, project, b.getId());
    fails(() -> requests.send(a, project, b.getId()));
    fails(() -> requests.respond(c, request, true));
    assertEquals("PENDING", requests.incoming(b.getId()).get(0).text("status"));
    assertFalse(new CommunityDao().notifications(b.getId()).isEmpty());
    requests.respond(b, request, true);
    fails(() -> requests.respond(b, request, true));
    fails(() -> requests.send(a, project, b.getId()));
    assertTrue(projects.members(project).stream().anyMatch(r -> r.id("id") == b.getId()));
    // Fresh authentication and a fresh DAO query must still return the accepted member.
    User fresh = users.login(users.profile(b.getId()).text("email"), "TestPassword123!");
    assertTrue(
        new ProjectDao().members(project).stream().anyMatch(r -> r.id("id") == fresh.getId()));
    long rejected = requests.send(a, project, c.getId());
    requests.respond(c, rejected, false);
    fails(() -> requests.respond(c, rejected, false));
    assertFalse(projects.members(project).stream().anyMatch(r -> r.id("id") == c.getId()));
    TaskDao tasks = new TaskDao();
    fails(() -> tasks.save(a, 0, project, c.getId(), "Invalid task", "", "HIGH", "PENDING", ""));
    tasks.save(
        a,
        0,
        project,
        b.getId(),
        "Complete backend",
        "Verify persistence",
        "HIGH",
        "PENDING",
        "2026-12-31");
    long task = tasks.list(b, null).get(0).id("id");
    fails(() -> tasks.status(c, task, "COMPLETED"));
    tasks.status(b, task, "COMPLETED");
    assertEquals(1, projects.get(project).id("completed_count"));
    CommunityDao community = new CommunityDao();
    fails(() -> community.discuss(c, project, "private"));
    community.discuss(b, project, "A persisted update <script>alert(1)</script>");
    assertEquals(1, community.discussions(a, project).size());
    fails(() -> community.discussions(c, project));
    Record notification = community.notifications(b.getId()).get(0);
    fails(() -> community.read(c.getId(), notification.id("id")));
    community.read(b.getId(), notification.id("id"));
    assertEquals(3, community.badges(b.getId()).size());
    assertTrue(
        community.leaderboard().stream()
            .anyMatch(r -> r.id("id") == b.getId() && r.id("score") == 60));
    users.profile(c.getId(), c.getName(), "Java, MySQL", "Education", "");
    assertTrue(
        projects.recommendations(c.getId()).stream()
            .anyMatch(r -> r.id("id") == project && r.id("match_score") == 8));
    fails(() -> projects.archive(c, project));
    fails(() -> new AdminDao().stats(c));
    new FileDao().add(b, project, "notes.txt", UUID.randomUUID() + ".txt", "text/plain", 12);
    long file = new FileDao().list(a, project).get(0).id("id");
    fails(() -> new FileDao().get(c, file));
  }

  @Test
  public void concurrentAcceptanceAndNotificationRollback() throws Exception {
    long request = requests.send(a, project, b.getId());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      Callable<Boolean> accept =
          () -> {
            start.await();
            try {
              requests.respond(b, request, true);
              return true;
            } catch (IllegalArgumentException expected) {
              return false;
            }
          };
      Future<Boolean> one = pool.submit(accept), two = pool.submit(accept);
      start.countDown();
      assertNotEquals(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
    assertEquals(
        1, projects.members(project).stream().filter(r -> r.id("id") == b.getId()).count());
    long rollbackRequest = requests.send(a, project, c.getId());
    // A deliberately failing notification insert verifies that acceptance and membership roll back
    // together.
    try (Connection conn = Db.open()) {
      Sql.update(
          conn,
          "CREATE TRIGGER test_notification_failure BEFORE INSERT ON notifications FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Intentional integration test failure'");
    }
    try {
      try {
        requests.respond(c, rollbackRequest, true);
        fail("Notification failure should abort acceptance");
      } catch (SQLException expected) {
      }
      assertEquals("PENDING", requests.incoming(c.getId()).get(0).text("status"));
      assertFalse(projects.members(project).stream().anyMatch(r -> r.id("id") == c.getId()));
    } finally {
      try (Connection conn = Db.open()) {
        Sql.update(conn, "DROP TRIGGER IF EXISTS test_notification_failure");
      }
    }
  }
}
