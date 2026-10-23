package com.collabhub.model;

import java.io.Serializable;

public final class User implements Serializable {
  private static final long serialVersionUID = 1L;
  private final long id;
  private final String name, role;

  public User(long id, String name, String role) {
    this.id = id;
    this.name = name;
    this.role = role;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getRole() {
    return role;
  }

  public boolean isAdmin() {
    return "ADMIN".equals(role);
  }
}
