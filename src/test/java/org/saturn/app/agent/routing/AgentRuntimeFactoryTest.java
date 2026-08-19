package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.moandjiezana.toml.Toml;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentRoomAutomation;
import org.saturn.app.agent.room.DefaultAgentRoomAutomation;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AgentService;
import org.saturn.app.service.impl.OutService;

class AgentRuntimeFactoryTest {
  @TempDir Path tempDir;

  @Test
  void disabledRuntimeDoesNotInitializeDatabaseDependencies() {
    Toml config = disabledConfig();
    Path database = tempDir.resolve("missing").resolve("agent.db");

    AgentService service =
        AgentRuntimeFactory.create(
            null, config, database.toString(), new OutService(new ArrayBlockingQueue<>(2)));

    assertFalse(Files.exists(database));
    service.close();
  }

  @Test
  void flushesAgentReplyWithoutWaitingForAnotherIncomingMessage() throws Exception {
    Toml config = disabledConfig();
    RecordingEngine engine = new RecordingEngine(config);
    engine.outgoingMessageQueue.add("already queued");
    AgentService service =
        AgentRuntimeFactory.create(
            engine, config, tempDir.resolve("agent.db").toString(), engine.outService);

    try {
      assertFalse(
          service.submit(
              new AgentInvocation(
                  new AgentContext(
                      "programming", "alice", "trip-a", "hash-a", false, List.of("alice")),
                  "question")));

      assertEquals(
          "{\"cmd\":\"chat\",\"text\":\"already queued\"}",
          engine.flushedMessages.poll(1, TimeUnit.SECONDS));
      assertEquals(
          "{\"cmd\":\"chat\",\"text\":\"@alice \\nThe agent is disabled.\"}",
          engine.flushedMessages.poll(1, TimeUnit.SECONDS));
    } finally {
      service.close();
    }
  }

  @Test
  void installsRoomAutomationForEnabledHostAndReplicaRuntimes() {
    Toml config = enabledConfig();
    RecordingEngine host = new RecordingEngine(config, EngineType.HOST);
    RecordingEngine replica = new RecordingEngine(config, EngineType.REPLICA);
    AgentService hostService =
        AgentRuntimeFactory.create(
            host, config, tempDir.resolve("host.db").toString(), host.outService);
    AgentService replicaService =
        AgentRuntimeFactory.create(
            replica, config, tempDir.resolve("replica.db").toString(), replica.outService);

    try {
      assertInstanceOf(DefaultAgentRoomAutomation.class, host.getAgentRoomAutomation());
      assertInstanceOf(DefaultAgentRoomAutomation.class, replica.getAgentRoomAutomation());
    } finally {
      hostService.close();
      replicaService.close();
      host.stop();
      replica.stop();
    }
  }

  @Test
  void processesAnonymousMessagesAndJoinsWithoutFailingProtectedIdentityChecks() {
    Toml config = enabledConfig();
    RecordingEngine engine = new RecordingEngine(config);
    AgentService service =
        AgentRuntimeFactory.create(
            engine, config, tempDir.resolve("anonymous.db").toString(), engine.outService);
    ChatMessage message =
        new ChatMessage(null, "anonymous", null, "anonymous-hash", null, "hello room");
    User user =
        new User("programming", false, "anonymous", null, "user", "anonymous-hash", 0, 1L, false);

    try {
      assertEquals(
          AgentRoomAutomation.Outcome.PASS, engine.getAgentRoomAutomation().onMessage(message));
      assertDoesNotThrow(() -> engine.getAgentRoomAutomation().onJoin(user));
    } finally {
      service.close();
      engine.stop();
    }
  }

  private static Toml disabledConfig() {
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

            [agent]
            enabled = false
            """);
  }

  private static Toml enabledConfig() {
    return new Toml()
        .read(
            """
            cmdPrefix = "*"
            channel = "programming"
            nick = "saturn"
            trip = "secret13"
            userTrips = ""
            adminTrips = "595754"
            wsUrl = "wss://hack.chat/chat-ws"
            proxies = ""
            autorunCommands = ""

            [agent]
            enabled = true
            endpoint = "http://localhost:16261"
            """);
  }

  private static Connection noopConnection() {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if (method.getReturnType().equals(boolean.class)) {
                return false;
              }
              if (method.getReturnType().equals(int.class)) {
                return 0;
              }
              if (method.getReturnType().equals(long.class)) {
                return 0L;
              }
              return null;
            });
  }

  private static final class RecordingEngine extends EngineImpl {
    private final ArrayBlockingQueue<String> flushedMessages = new ArrayBlockingQueue<>(4);

    private RecordingEngine(Toml config) {
      this(config, EngineType.HOST);
    }

    private RecordingEngine(Toml config, EngineType type) {
      super(noopConnection(), config, type);
    }

    @Override
    public void flushMessage(String message) {
      flushedMessages.add(message);
    }
  }
}
