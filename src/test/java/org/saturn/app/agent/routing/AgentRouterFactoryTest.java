package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient;
import org.saturn.app.agent.persistence.H2AgentMemoryStore;
import org.saturn.app.agent.persistence.H2AgentQueryRepository;
import org.saturn.app.agent.persistence.H2AgentSchemaRepository;
import org.saturn.app.agent.persistence.H2AgentSqlRepository;
import org.saturn.app.agent.persistence.H2ReadOnlyConnectionFactory;
import org.saturn.app.agent.tool.execution.AgentToolRegistry;

class AgentRouterFactoryTest {
  @Test
  void composesCanonicalOpenAiProviderImplementation() throws Exception {
    var databasePath = Files.createTempFile("agent-router-factory", ".mv.db").toString();
    var readOnly = new H2ReadOnlyConnectionFactory(databasePath);
    var infrastructure =
        new AgentInfrastructure(
            new H2AgentQueryRepository(readOnly),
            new H2AgentMemoryStore(databasePath),
            new H2AgentSchemaRepository(readOnly),
            new H2AgentSqlRepository(readOnly));

    var router =
        new AgentRouterFactory()
            .create(
                testConfig(),
                new AgentToolRegistry(),
                infrastructure,
                AgentParticipationConfig.from(null));
    Field client = DefaultAgentRouter.class.getDeclaredField("client");
    client.setAccessible(true);

    assertEquals(OpenAiCompatibleClient.class, client.get(router).getClass());
  }

  private static AgentConfig testConfig() {
    return new AgentConfig(
        true,
        java.net.URI.create("http://localhost"),
        java.util.Optional.empty(),
        "",
        java.time.Duration.ofSeconds(1),
        1,
        1,
        1,
        1,
        100,
        100,
        1,
        java.time.Duration.ofSeconds(1),
        0,
        java.time.Duration.ofMillis(1));
  }
}
