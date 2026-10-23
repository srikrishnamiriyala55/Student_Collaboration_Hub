package com.collabhub.model;

import java.util.LinkedHashMap;

/** Typed convenience accessors over an immutable-per-request database projection. */
public class Record extends LinkedHashMap<String, Object> {
  private static final long serialVersionUID = 1L;

  public long id(String key) {
    Object v = get(key);
    return v == null ? 0 : ((Number) v).longValue();
  }

  public String text(String key) {
    Object v = get(key);
    return v == null ? "" : v.toString();
  }

  public boolean flag(String key) {
    Object v = get(key);
    return Boolean.TRUE.equals(v) || (v instanceof Number && ((Number) v).intValue() != 0);
  }
}
