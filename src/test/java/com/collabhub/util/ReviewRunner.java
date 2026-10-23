package com.collabhub.util;

import java.sql.Connection;
import java.sql.Statement;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

/** Runs database/HTTP regressions only in a disposable, UUID-named review schema. */
public final class ReviewRunner {
  public static void main(String[] args) throws Exception {
    String url = System.getenv("DB_URL");
    String[] target = DatabaseInitializer.target(url);
    Validation.require(
        target[1].matches("collabhub_review_[a-f0-9]{32}"),
        "ReviewRunner requires a disposable review schema.");
    Result result;
    try {
      DatabaseInitializer.initialize(url);
      result =
          JUnitCore.runClasses(
              com.collabhub.WorkflowTest.class,
              com.collabhub.WebTest.class,
              com.collabhub.RegressionTest.class);
      result.getFailures().forEach(System.err::println);
      System.out.println(
          "Review checks: "
              + result.getRunCount()
              + "; failures: "
              + result.getFailureCount()
              + "; skipped: "
              + result.getIgnoreCount());
    } finally {
      try (Connection c = Db.connect(target[0]);
          Statement sql = c.createStatement()) {
        sql.execute("DROP DATABASE IF EXISTS `" + target[1] + "`");
        System.out.println("Removed temporary review database.");
      }
    }
    if (!result.wasSuccessful()) throw new IllegalStateException("Review regressions failed.");
  }
}
