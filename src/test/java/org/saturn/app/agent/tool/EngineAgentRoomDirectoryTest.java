package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;

class EngineAgentRoomDirectoryTest {
  private EngineImpl host;

  @AfterEach
  void clearHostReference() {
    if (host != null) {
      host.setHostRef(null);
    }
  }

  @Test
  void resolvesLiveUsersFromHostAndReplicasCaseInsensitively() {
    host = engine(EngineType.HOST);
    host.setHostRef(host);
    host.setActiveUsers(List.of(user("programming", "host-user")));
    EngineImpl lounge = engine(EngineType.REPLICA);
    lounge.setChannel("lounge");
    lounge.setActiveUsers(List.of(user("lounge", "lounge-user"), user("lounge", "guest")));
    host.addReplica(lounge);

    EngineAgentRoomDirectory directory = new EngineAgentRoomDirectory(host);

    AgentRoomDirectory.RoomSnapshot snapshot = directory.find("LOUNGE").orElseThrow();
    assertEquals("lounge", snapshot.room());
    assertEquals(List.of("lounge-user", "guest"), snapshot.users());
    assertEquals(
        List.of("host-user"),
        new EngineAgentRoomDirectory(lounge).find("programming").orElseThrow().users());
    assertTrue(directory.find("missing").isEmpty());
  }

  private static EngineImpl engine(EngineType type) {
    return new EngineImpl(noopConnection(), config(), type);
  }

  private static User user(String room, String nick) {
    return new User(room, false, nick, null, "", "hash-" + nick, 0, 0, false);
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
            wsUrl = "wss://hack.chat/chat-ws"
            proxies = ""
            autorunCommands = ""
            """);
  }
}
