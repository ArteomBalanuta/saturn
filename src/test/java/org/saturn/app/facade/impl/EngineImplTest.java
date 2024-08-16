package org.saturn.app.facade.impl;

import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.EngineType;
import org.saturn.app.model.dto.User;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EngineImplTest {
  private final EngineImpl engine =
      new EngineImpl(mock(Connection.class), mock(Toml.class), EngineType.HOST);

  @Test
  public void isUserMentioned() {
    User user = new User("lab", false, "merc", "8Wotmg", "", "", 9999, 1, false);
    assertAll(
        "User is mentioned!",
        () -> assertTrue(engine.isUserMentioned("@merc", user), "1"),
        () -> assertTrue(engine.isUserMentioned(" @merc", user), "2"),
        () -> assertTrue(engine.isUserMentioned("@merc ", user), "3"),
        () -> assertTrue(engine.isUserMentioned(" @merc ", user), "4"),
        () -> assertTrue(engine.isUserMentioned("merc", user), "5"),
        () -> assertTrue(engine.isUserMentioned(" merc", user), "6"),
        () -> assertTrue(engine.isUserMentioned("merc ", user), "7"),
        () -> assertTrue(engine.isUserMentioned(" merc ", user), "8"),
        () -> assertTrue(engine.isUserMentioned("asd merc asd", user), "9"),
        () -> assertTrue(engine.isUserMentioned("merc asds", user), "10"),
        () -> assertTrue(engine.isUserMentioned("asad merc", user), "11"));
  }

  @Test
  public void userNotMentioned() {
    User user = new User("lab", false, "merc", "8Wotmg", "", "", 9999, 1, false);
    assertAll(
        "User should NOT be mentioned!",
        () -> assertFalse(engine.isUserMentioned("+@merc+", user), "1"),
        () -> assertFalse(engine.isUserMentioned("-@merc", user), "2"),
        () -> assertFalse(engine.isUserMentioned("@merc-", user), "3"),
        () -> assertFalse(engine.isUserMentioned("", user), "4"),
        () -> assertFalse(engine.isUserMentioned("-merc-", user), "5"),
        () -> assertFalse(engine.isUserMentioned(" mercury", user), "6"),
        () -> assertFalse(engine.isUserMentioned("merca ", user), "7"),
        () -> assertFalse(engine.isUserMentioned(" merc2 ", user), "8"),
        () -> assertFalse(engine.isUserMentioned(" merc1 ", user), "9"),
        () -> assertFalse(engine.isUserMentioned("a asdmerc", user), "10"));
  }
}
