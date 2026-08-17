package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentConfigTest {
  @Test
  void appliesSafeDefaultsAndReadsApiKeyFromConfiguredEnvironmentVariable() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                endpoint = "http://localhost:16261/"
                apiKeyEnv = "SATURN_TEST_AGENT_KEY"
                """);

    AgentConfig actual = AgentConfig.from(config, Map.of("SATURN_TEST_AGENT_KEY", "secret"));

    assertEquals("http://localhost:16261", actual.endpoint().toString());
    assertEquals("secret", actual.apiKey());
    assertEquals(Duration.ofSeconds(30), actual.timeout());
    assertEquals(4, actual.maxToolCalls());
    assertEquals(5, actual.maxSteps());
    assertEquals(4, actual.maxToolCallsPerTurn());
    assertEquals(Duration.ofSeconds(10), actual.toolTimeout());
    assertEquals(2, actual.maxConcurrentRequests());
    assertEquals(768, actual.maxCompletionTokens());
    assertFalse(actual.thinkingEnabled());
    assertFalse(actual.model().isPresent());
  }

  @Test
  void readsBoundedCompletionSettings() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                maxCompletionTokens = 321
                thinkingEnabled = true
                """);

    AgentConfig actual = AgentConfig.from(config, Map.of());

    assertEquals(321, actual.maxCompletionTokens());
    assertTrue(actual.thinkingEnabled());
  }

  @Test
  void readsIndependentSdkExecutionLimits() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                maxSteps = 6
                maxToolCallsPerTurn = 3
                toolTimeoutMillis = 1200
                """);

    AgentConfig actual = AgentConfig.from(config, Map.of());

    assertEquals(6, actual.maxSteps());
    assertEquals(3, actual.maxToolCallsPerTurn());
    assertEquals(Duration.ofMillis(1200), actual.toolTimeout());
  }

  @Test
  void environmentOverridesTomlForProviderAndExecutionSettings() {
    Toml config =
        new Toml()
            .read(
                """
                [agent]
                enabled = false
                endpoint = "http://toml.example"
                model = "toml-model"
                timeoutSeconds = 30
                maxSteps = 8
                maxToolCallsPerTurn = 4
                toolTimeoutMillis = 15000
                """);

    AgentConfig actual =
        AgentConfig.from(
            config,
            Map.of(
                "SATURN_AGENT_ENABLED", "true",
                "SATURN_AGENT_ENDPOINT", "http://environment.example/",
                "SATURN_AGENT_MODEL", "environment-model",
                "SATURN_AGENT_TIMEOUT_SECONDS", "12",
                "SATURN_AGENT_MAX_STEPS", "5",
                "SATURN_AGENT_MAX_TOOL_CALLS_PER_TURN", "3",
                "SATURN_AGENT_TOOL_TIMEOUT_MILLIS", "10000"));

    assertTrue(actual.enabled());
    assertEquals("http://environment.example", actual.endpoint().toString());
    assertEquals("environment-model", actual.model().orElseThrow());
    assertEquals(Duration.ofSeconds(12), actual.timeout());
    assertEquals(5, actual.maxSteps());
    assertEquals(3, actual.maxToolCallsPerTurn());
    assertEquals(Duration.ofSeconds(10), actual.toolTimeout());
  }

  @Test
  void usesLocalSafeDefaultsWhenAgentSettingsAreAbsent() {
    AgentConfig actual = AgentConfig.from(new Toml(), Map.of());

    assertFalse(actual.enabled());
    assertEquals("http://localhost:16261", actual.endpoint().toString());
    assertEquals(5, actual.maxSteps());
    assertEquals(Duration.ofSeconds(10), actual.toolTimeout());
  }

  @Test
  void rejectsNonHttpEndpointAndInvalidLimits() {
    Toml badEndpoint =
        new Toml()
            .read(
                """
                [agent]
                endpoint = "file:///tmp/router"
                """);
    Toml badLimit =
        new Toml()
            .read(
                """
                [agent]
                endpoint = "http://localhost:16261"
                maxToolCalls = 0
                """);
    Toml missingHost =
        new Toml()
            .read(
                """
                [agent]
                endpoint = "http:router"
                """);

    assertThrows(IllegalArgumentException.class, () -> AgentConfig.from(badEndpoint, Map.of()));
    assertThrows(IllegalArgumentException.class, () -> AgentConfig.from(badLimit, Map.of()));
    assertThrows(IllegalArgumentException.class, () -> AgentConfig.from(missingHost, Map.of()));
  }

  @Test
  void rejectsNegativeRetrySettings() {
    Toml negativeRetries =
        new Toml()
            .read(
                """
                [agent]
                maxRetries = -1
                """);
    Toml negativeBackoff =
        new Toml()
            .read(
                """
                [agent]
                retryBackoffMillis = -1
                """);

    assertThrows(IllegalArgumentException.class, () -> AgentConfig.from(negativeRetries, Map.of()));
    assertThrows(IllegalArgumentException.class, () -> AgentConfig.from(negativeBackoff, Map.of()));
  }

  @Test
  void supportsLegacyConstructorDefaults() {
    AgentConfig config =
        new AgentConfig(
            true,
            URI.create("http://localhost:16261"),
            Optional.empty(),
            "",
            Duration.ofSeconds(1),
            2,
            4,
            2,
            2,
            100,
            100,
            2,
            Duration.ofMinutes(1),
            0,
            Duration.ZERO,
            128,
            true);

    assertEquals(5, config.maxSteps());
    assertEquals(4, config.maxToolCallsPerTurn());
    assertEquals(Duration.ofSeconds(10), config.toolTimeout());
  }
}
