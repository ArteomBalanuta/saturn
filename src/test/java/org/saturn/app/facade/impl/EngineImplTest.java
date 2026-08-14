package org.saturn.app.facade.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.EngineType;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;

class EngineImplTest {
  private final EngineImpl engine = new EngineImpl(noopConnection(), buildConfig(), EngineType.HOST);

  private static Toml buildConfig() {
    return new Toml()
        .read(
            """
            cmdPrefix = "*"
            channel = "programming"
            nick = "saturn"
            trip = "secret13"
            userTrips = ""
            adminTrips = ""
            dbPath = "database/database.db"
            wsUrl = "wss://hack.chat/chat-ws"
            proxies = ""
            autorunCommands = ""
            """);
  }

  private static Connection noopConnection() {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> null);
  }

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

  @Test
  void dispatchMessageUsesRegisteredPayloadListener() {
    CountingListener listener = new CountingListener();
    engine.registerPayloadListener("testCmd", listener);

    engine.dispatchMessage("{\"cmd\":\"testCmd\",\"text\":\"hello\"}");

    assertEquals(1, listener.notifications());
    assertEquals("{\"cmd\":\"testCmd\",\"text\":\"hello\"}", listener.lastMessage());
  }

  @Test
  void setOnlineSetListenerUpdatesPayloadRegistry() {
    CountingListener listener = new CountingListener();
    engine.setOnlineSetListener(listener);

    engine.dispatchMessage("{\"cmd\":\"onlineSet\",\"users\":[]}");

    assertEquals(1, listener.notifications());
    assertEquals("{\"cmd\":\"onlineSet\",\"users\":[]}", listener.lastMessage());
  }

  @Test
  void buildChatPayloadEscapesTrailingBackslash() {
    String payload = EngineImpl.buildChatPayload("@ab $\\");

    assertEquals("{\"cmd\":\"chat\",\"text\":\"@ab $\\\\\"}", payload);
  }

  @Test
  void buildChatPayloadEscapesRealNewlinesAsJson() {
    String payload = EngineImpl.buildChatPayload("@ab line1\nline2");

    assertEquals("{\"cmd\":\"chat\",\"text\":\"@ab line1\\nline2\"}", payload);
  }

  private static final class CountingListener implements Listener {
    private int notifications;
    private String lastMessage;

    @Override
    public String getListenerName() {
      return "test";
    }

    @Override
    public void notify(String message) {
      notifications++;
      lastMessage = message;
    }

    int notifications() {
      return notifications;
    }

    String lastMessage() {
      return lastMessage;
    }
  }
}
