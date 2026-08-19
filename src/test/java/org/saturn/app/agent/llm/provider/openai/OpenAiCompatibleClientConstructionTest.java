package org.saturn.app.agent.llm.provider.openai;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleClientConstructionTest {
  @Test
  void rejectsNullDependenciesAtConstruction() {
    AgentTestConfigs config = new AgentTestConfigs();
    HttpClient httpClient = HttpClient.newHttpClient();

    assertThrows(
        NullPointerException.class, () -> new OpenAiCompatibleClient(null, new Gson(), httpClient));
    assertThrows(
        NullPointerException.class,
        () -> new OpenAiCompatibleClient(config.value(), null, httpClient));
    assertThrows(
        NullPointerException.class,
        () -> new OpenAiCompatibleClient(config.value(), new Gson(), null));
  }

  private static final class AgentTestConfigs {
    private final org.saturn.app.agent.config.AgentConfig value =
        new org.saturn.app.agent.config.AgentConfig(
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

    private org.saturn.app.agent.config.AgentConfig value() {
      return value;
    }
  }
}
