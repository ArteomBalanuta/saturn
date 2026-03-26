package org.saturn.app.support;

import com.moandjiezana.toml.Toml;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

public final class TestSupport {
  private TestSupport() {}

  public static EngineImpl engine() {
    return new EngineImpl(noopConnection(), config(), EngineType.HOST);
  }

  public static ChatMessage chatMessage(String text, String nick, String trip) {
    return new ChatMessage(null, nick, trip, null, null, text);
  }

  public static User user(String nick, String trip, String hash) {
    return new User("programming", false, nick, trip, "", hash, 0, 0L, false);
  }

  public static void setField(Object target, Class<?> owner, String fieldName, Object value) {
    try {
      Field field = owner.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Connection noopConnection() {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              Class<?> returnType = method.getReturnType();
              if (returnType.equals(boolean.class)) {
                return false;
              }
              if (returnType.equals(int.class)) {
                return 0;
              }
              if (returnType.equals(long.class)) {
                return 0L;
              }
              if (returnType.equals(float.class)) {
                return 0f;
              }
              if (returnType.equals(double.class)) {
                return 0d;
              }
              return null;
            });
  }

  private static Toml config() {
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
}
