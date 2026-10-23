package com.collabhub.util;

public final class Validation {
  private Validation() {}

  public static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }

  public static String text(String value, String label, int max) {
    String s = value == null ? "" : value.trim();
    require(
        !s.isEmpty() && s.length() <= max,
        label + " is required (maximum " + max + " characters).");
    return s;
  }

  public static String optional(String value, int max) {
    String s = value == null ? "" : value.trim();
    require(s.length() <= max, "Text is too long.");
    return s;
  }

  public static String choice(String value, String... allowed) {
    for (String s : allowed) if (s.equals(value)) return s;
    throw new IllegalArgumentException("Invalid selection.");
  }

  public static String preview(String value, int max) {
    require(value != null && max > 0, "Invalid notification text.");
    if (value.codePointCount(0, value.length()) <= max) return value;
    return value.substring(0, value.offsetByCodePoints(0, max - 1)) + "…";
  }

  public static boolean validPasswordLength(String password) {
    return password != null
        && password.length() >= 8
        && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
  }

  public static long id(String value) {
    try {
      long id = Long.parseLong(value);
      require(id > 0, "Invalid identifier.");
      return id;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid identifier.");
    }
  }

  public static int capacity(String value) {
    try {
      int result = Integer.parseInt(value);
      require(result >= 1 && result <= 100, "Team capacity must be between 1 and 100.");
      return result;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Enter a valid team capacity between 1 and 100.");
    }
  }

  public static java.sql.Date date(String value) {
    if (value == null || value.trim().isEmpty()) return null;
    try {
      java.time.LocalDate parsed = java.time.LocalDate.parse(value);
      require(parsed.getYear() >= 1000 && parsed.getYear() <= 9999, "Enter a valid deadline.");
      return java.sql.Date.valueOf(parsed);
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Enter a valid deadline in YYYY-MM-DD format.");
    }
  }
}
