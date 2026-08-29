package org.saturn.app.agent.config;

import com.moandjiezana.toml.Toml;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Loads validated agent settings from TOML with explicit environment precedence. */
public final class AgentConfigLoader {
  private static final String DEFAULT_ENDPOINT = "http://localhost:16261";
  private static final int DEFAULT_MAX_COMPLETION_TOKENS = 1024;
  private static final int DEFAULT_MAX_STEPS = 5;
  private static final Duration DEFAULT_TOOL_TIMEOUT = Duration.ofSeconds(10);

  private AgentConfigLoader() {}

  public static AgentConfig load(Toml root, Map<String, String> environment) {
    Toml table = root == null ? null : root.getTable("agent");
    boolean enabled =
        AgentConfigValueReader.readBoolean(
            table, environment, "enabled", "SATURN_AGENT_ENABLED", false);
    URI endpoint =
        normalizeEndpoint(
            URI.create(
                AgentConfigValueReader.readString(
                    table, environment, "endpoint", "SATURN_AGENT_ENDPOINT", DEFAULT_ENDPOINT)));
    String model =
        AgentConfigValueReader.readString(table, environment, "model", "SATURN_AGENT_MODEL", "")
            .trim();
    String apiKeyEnv =
        AgentConfigValueReader.readString(
                table, environment, "apiKeyEnv", "SATURN_AGENT_API_KEY_ENV", "SATURN_AGENT_API_KEY")
            .trim();
    String apiKey = apiKeyEnv.isEmpty() ? "" : environment.getOrDefault(apiKeyEnv, "");
    long maxToolCalls =
        AgentConfigValueReader.readLong(
            table, environment, "maxToolCalls", "SATURN_AGENT_MAX_TOOL_CALLS", 4);

    return new AgentConfig(
        enabled,
        endpoint,
        model.isEmpty() ? Optional.empty() : Optional.of(model),
        apiKey,
        Duration.ofSeconds(
            AgentConfigValueReader.readLong(
                table, environment, "timeoutSeconds", "SATURN_AGENT_TIMEOUT_SECONDS", 30)),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "maxConcurrentRequests",
                "SATURN_AGENT_MAX_CONCURRENT_REQUESTS",
                2),
            "maxConcurrentRequests"),
        AgentConfigValueReader.toInt(maxToolCalls, "maxToolCalls"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table, environment, "maxCallsPerTool", "SATURN_AGENT_MAX_CALLS_PER_TOOL", 2),
            "maxCallsPerTool"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table, environment, "maxToolFailures", "SATURN_AGENT_MAX_TOOL_FAILURES", 2),
            "maxToolFailures"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table, environment, "maxPromptChars", "SATURN_AGENT_MAX_PROMPT_CHARS", 8_000),
            "maxPromptChars"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table, environment, "maxOutputChars", "SATURN_AGENT_MAX_OUTPUT_CHARS", 8_000),
            "maxOutputChars"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table, environment, "memoryTurns", "SATURN_AGENT_MEMORY_TURNS", 30),
            "memoryTurns"),
        Duration.ofHours(
            AgentConfigValueReader.readLong(
                table, environment, "memoryTtlHours", "SATURN_AGENT_MEMORY_TTL_HOURS", 168)),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table, environment, "maxRetries", "SATURN_AGENT_MAX_RETRIES", 2),
            "maxRetries"),
        Duration.ofMillis(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "retryBackoffMillis",
                "SATURN_AGENT_RETRY_BACKOFF_MILLIS",
                250)),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "maxCompletionTokens",
                "SATURN_AGENT_MAX_COMPLETION_TOKENS",
                DEFAULT_MAX_COMPLETION_TOKENS),
            "maxCompletionTokens"),
        AgentConfigValueReader.readBoolean(
            table, environment, "thinkingEnabled", "SATURN_AGENT_THINKING_ENABLED", false),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table, environment, "maxSteps", "SATURN_AGENT_MAX_STEPS", DEFAULT_MAX_STEPS),
            "maxSteps"),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "maxToolCallsPerTurn",
                "SATURN_AGENT_MAX_TOOL_CALLS_PER_TURN",
                maxToolCalls),
            "maxToolCallsPerTurn"),
        Duration.ofMillis(
            AgentConfigValueReader.readLong(
                table,
                environment,
                "toolTimeoutMillis",
                "SATURN_AGENT_TOOL_TIMEOUT_MILLIS",
                DEFAULT_TOOL_TIMEOUT.toMillis())));
  }

  private static URI normalizeEndpoint(URI endpoint) {
    String value = endpoint.toString();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return URI.create(value);
  }
}
