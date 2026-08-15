package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
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
          "{\"cmd\":\"chat\",\"text\":\"@alice The agent is disabled.\"}",
          engine.flushedMessages.poll(1, TimeUnit.SECONDS));
    } finally {
      service.close();
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

  private static Connection noopConnection() {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> null);
  }

  private static final class RecordingEngine extends EngineImpl {
    private final ArrayBlockingQueue<String> flushedMessages = new ArrayBlockingQueue<>(4);

    private RecordingEngine(Toml config) {
      super(noopConnection(), config, EngineType.HOST);
    }

    @Override
    public void flushMessage(String message) {
      flushedMessages.add(message);
    }
  }
}
