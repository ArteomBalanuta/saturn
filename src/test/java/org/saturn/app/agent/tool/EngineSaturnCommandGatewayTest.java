package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.moandjiezana.toml.Toml;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;

class EngineSaturnCommandGatewayTest {
  @Test
  void returnsFalseWhenTheCommandReportsFailedStatus() throws Exception {
    try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE executed_commands (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            trip TEXT,
            command_name TEXT,
            arguments TEXT,
            status TEXT,
            created_on INTEGER NOT NULL,
            channel TEXT)
          """);
      EngineImpl engine = new EngineImpl(connection, config(), EngineType.HOST);
      EngineSaturnCommandGateway gateway = new EngineSaturnCommandGateway(engine);

      boolean executed = gateway.execute(context(), "time", "");

      assertFalse(executed);
    }
  }

  private AgentContext context() {
    return new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of("alice"));
  }

  private Toml config() {
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
