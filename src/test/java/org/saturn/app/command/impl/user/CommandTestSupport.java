package org.saturn.app.command.impl.user;

import com.moandjiezana.toml.Toml;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

final class CommandTestSupport {
  private CommandTestSupport() {}

  static EngineImpl engine() {
    return new EngineImpl(noopConnection(), config(), EngineType.HOST);
  }

  static ChatMessage chatMessage(String text, String nick, String trip) {
    return new ChatMessage(null, nick, trip, null, null, text);
  }

  private static Connection noopConnection() {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> null);
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
