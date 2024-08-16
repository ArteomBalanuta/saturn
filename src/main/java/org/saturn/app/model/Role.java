package org.saturn.app.model;

public enum Role {
  ADMIN(5),
  MODERATOR(4),
  TRUSTED(3),
  USER(2),
  REGULAR(1),
  PEST(0);

  private final int value;

  Role(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
