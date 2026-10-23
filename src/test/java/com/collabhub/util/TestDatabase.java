package com.collabhub.util;

/** Restricts integration tests to the explicitly named test schema or disposable review schemas. */
public final class TestDatabase {
  private TestDatabase() {}

  public static void requireIsolated() {
    String name = DatabaseInitializer.target(System.getenv("DB_URL"))[1];
    if (!name.equals("student_hub_test") && !name.matches("collabhub_review_[a-f0-9]{32}")) {
      throw new IllegalStateException(
          "Integration tests require an isolated test/review database.");
    }
  }
}
