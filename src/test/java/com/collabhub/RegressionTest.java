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

/** Regressions discovered during the complete source review. */
public class RegressionTest {
  private final UserDao users = new UserDao();
  private User owner, first, second;
  private long project;

  @Before
  public void setup() throws Exception {
    Assume.assumeTrue("true".equals(System.getenv("COLLABHUB_INTEGRATION")));
    com.collabhub.util.TestDatabase.requireIsolated();
    owner = student("Owner");
    first = student("First");
    second = student("Second");
    project =
        new ProjectDao()
            .save(
                owner,
                0,
                "Regression project",
                "Review verification",
                "Java students",
                "Education",
                "Java",
                "OPEN",
                2);
  }

  private User student(String name) throws Exception {
    long id =
        users.register(
            name, UUID.randomUUID() + "@example.test", "ReviewPassword123!", "ReviewPassword123!");
    return new User(id, name, "STUDENT");
  }

  @Test
  public void longAnnouncementKeepsFullTextAndUsesBoundedNotification() throws Exception {
    try (Connection c = Db.open()) {
      Sql.update(c, "UPDATE students SET role='ADMIN' WHERE id=?", owner.getId());
    }
    User admin = new User(owner.getId(), owner.getName(), "ADMIN");
    String message = String.join("", Collections.nCopies(1000, "a"));
    new AdminDao().announce(admin, message);
    assertEquals(message, new CommunityDao().announcements().get(0).text("message"));
    String notification = new CommunityDao().notifications(first.getId()).get(0).text("message");
    assertEquals(600, notification.length());
    assertTrue(notification.endsWith("…"));
  }

  @Test
  public void overlongPasswordCannotMatchItsBcryptPrefix() throws Exception {
    String password = String.join("", Collections.nCopies(72, "p"));
    String email = UUID.randomUUID() + "@example.test";
    users.register("Boundary password", email, password, password);
    assertNotNull(users.login(email, password));
    try {
      users.login(email, password + "wrong suffix");
      fail("Overlong password accepted");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void unchangedTaskStatusDoesNotCreateDuplicateNotifications() throws Exception {
    TaskDao tasks = new TaskDao();
    tasks.save(owner, 0, project, owner.getId(), "Review task", "", "MEDIUM", "PENDING", "");
    long task = tasks.list(owner, project).get(0).id("id");
    int before = new CommunityDao().notifications(owner.getId()).size();
    long notification = new CommunityDao().notifications(owner.getId()).get(0).id("id");
    new CommunityDao().read(owner.getId(), notification);
    new CommunityDao().read(owner.getId(), notification);
    tasks.status(owner, task, "PENDING");
    assertEquals(before, new CommunityDao().notifications(owner.getId()).size());
    new ProjectDao().archive(owner, project);
    assertEquals("ARCHIVED", tasks.list(owner, null).get(0).text("project_status"));
    try {
      tasks.status(owner, task, "COMPLETED");
      fail("Archived task changed");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void competingInvitationsCannotOverfillTeamAfterWaitingForProjectLock() throws Exception {
    TeamRequestDao invitations = new TeamRequestDao();
    long a = invitations.send(owner, project, first.getId());
    long b = invitations.send(owner, project, second.getId());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try (Connection blocker = Db.open()) {
      blocker.setAutoCommit(false);
      ProjectDao.lock(blocker, project);
      Future<Boolean> one = pool.submit(() -> accept(first, a));
      Future<Boolean> two = pool.submit(() -> accept(second, b));
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean waiting = false;
        while (System.nanoTime() < deadline) {
          Record count =
              Sql.one(
                  blocker,
                  "SELECT COUNT(*) n FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND l.ENGINE=w.ENGINE WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='projects'");
          if (count.id("n") >= 2) {
            waiting = true;
            break;
          }
          Thread.sleep(25);
        }
        assertTrue("Both acceptance calls must wait for the project lock", waiting);
      } finally {
        blocker.commit();
      }
      assertNotEquals(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
      assertEquals(2, new ProjectDao().members(project).size());
    } finally {
      pool.shutdownNow();
    }
  }

  private boolean accept(User student, long request) throws Exception {
    try {
      new TeamRequestDao().respond(student, request, true);
      return true;
    } catch (IllegalArgumentException expected) {
      return false;
    }
  }
}
