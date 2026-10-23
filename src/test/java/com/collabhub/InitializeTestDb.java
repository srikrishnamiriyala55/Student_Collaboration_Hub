package com.collabhub;

import com.collabhub.util.DatabaseInitializer;
import com.collabhub.util.TestDatabase;

public final class InitializeTestDb {
  public static void main(String[] args) throws Exception {
    TestDatabase.requireIsolated();
    DatabaseInitializer.initialize(System.getenv("DB_URL"));
    System.out.println("Initialized the isolated test schema without dropping existing data.");
  }
}
