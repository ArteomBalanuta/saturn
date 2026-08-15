package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.moandjiezana.toml.Toml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saturn.app.service.AgentService;
import org.saturn.app.service.impl.OutService;

class AgentRuntimeFactoryTest {
  @TempDir Path tempDir;

  @Test
  void disabledRuntimeDoesNotInitializeDatabaseDependencies() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                enabled = false
                """);
    Path database = tempDir.resolve("missing").resolve("agent.db");

    AgentService service =
        AgentRuntimeFactory.create(
            null, config, database.toString(), new OutService(new ArrayBlockingQueue<>(2)));

    assertFalse(Files.exists(database));
    service.close();
  }
}
