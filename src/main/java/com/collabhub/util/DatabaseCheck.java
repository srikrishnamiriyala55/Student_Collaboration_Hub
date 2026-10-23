package com.collabhub.util;

import java.sql.*;

public final class DatabaseCheck {
  public static void main(String[] args) throws Exception {
    try (Connection c = Db.open();
        PreparedStatement p =
            c.prepareStatement("SELECT DATABASE(),VERSION(),(SELECT COUNT(*) FROM students)");
        ResultSet r = p.executeQuery()) {
      r.next();
      System.out.println(
          "Connected to "
              + r.getString(1)
              + " on MySQL "
              + r.getString(2)
              + "; student count: "
              + r.getLong(3));
    }
  }
}
