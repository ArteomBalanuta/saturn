package org.saturn.app.agent;

import com.moandjiezana.toml.Toml;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Loads validated agent settings from TOML with explicit environment precedence. */
final class AgentConfigLoader {
  private static final String DEFAULT_ENDPOINT = "http://localhost:16261";
  private static final int DEFAULT_MAX_COMPLETION_TOKENS = 768;
  private static final int DEFAULT_MAX_STEPS = 5;
  private static final Duration DEFAULT_TOOL_TIMEOUT = Duration.ofSeconds(10);

  private AgentConfigLoader() {}

  static AgentConfig load(Toml root, Map<String, String> environment) {
    Toml table = root == null ? null : root.getTable("agent");
    boolean enabled = readBoolean(table, environment, "enabled", "SATURN_AGENT_ENABLED", false);
    URI endpoint =
        normalizeEndpoint(
            URI.create(
                readString(
                    table, environment, "endpoint", "SATURN_AGENT_ENDPOINT", DEFAULT_ENDPOINT)));
    String model = readString(table, environment, "model", "SATURN_AGENT_MODEL", "").trim();
    String apiKeyEnv =
        readString(
                table, environment, "apiKeyEnv", "SATURN_AGENT_API_KEY_ENV", "SATURN_AGENT_API_KEY")
            .trim();
    String apiKey = apiKeyEnv.isEmpty() ? "" : environment.getOrDefault(apiKeyEnv, "");
    long maxToolCalls =
        readLong(table, environment, "maxToolCalls", "SATURN_AGENT_MAX_TOOL_CALLS", 4);

    return new AgentConfig(
        enabled,
        endpoint,
        model.isEmpty() ? Optional.empty() : Optional.of(model),
        apiKey,
        Duration.ofSeconds(
            readLong(table, environment, "timeoutSeconds", "SATURN_AGENT_TIMEOUT_SECONDS", 30)),
        toInt(
            readLong(
                table,
                environment,
                "maxConcurrentRequests",
                "SATURN_AGENT_MAX_CONCURRENT_REQUESTS",
                2),
            "maxConcurrentRequests"),
        toInt(maxToolCalls, "maxToolCalls"),
        toInt(
            readLong(table, environment, "maxCallsPerTool", "SATURN_AGENT_MAX_CALLS_PER_TOOL", 2),
            "maxCallsPerTool"),
        toInt(
            readLong(table, environment, "maxToolFailures", "SATURN_AGENT_MAX_TOOL_FAILURES", 2),
            "maxToolFailures"),
        toInt(
            readLong(table, environment, "maxPromptChars", "SATURN_AGENT_MAX_PROMPT_CHARS", 8_000),
            "maxPromptChars"),
        toInt(
            readLong(table, environment, "maxOutputChars", "SATURN_AGENT_MAX_OUTPUT_CHARS", 8_000),
            "maxOutputChars"),
        toInt(
            readLong(table, environment, "memoryTurns", "SATURN_AGENT_MEMORY_TURNS", 30),
            "memoryTurns"),
        Duration.ofHours(
            readLong(table, environment, "memoryTtlHours", "SATURN_AGENT_MEMORY_TTL_HOURS", 168)),
        toInt(
            readLong(table, environment, "maxRetries", "SATURN_AGENT_MAX_RETRIES", 2),
            "maxRetries"),
        Duration.ofMillis(
            readLong(
                table,
                environment,
                "retryBackoffMillis",
                "SATURN_AGENT_RETRY_BACKOFF_MILLIS",
                250)),
        toInt(
            readLong(
                table,
                environment,
                "maxCompletionTokens",
                "SATURN_AGENT_MAX_COMPLETION_TOKENS",
                DEFAULT_MAX_COMPLETION_TOKENS),
            "maxCompletionTokens"),
        readBoolean(table, environment, "thinkingEnabled", "SATURN_AGENT_THINKING_ENABLED", false),
        toInt(
            readLong(table, environment, "maxSteps", "SATURN_AGENT_MAX_STEPS", DEFAULT_MAX_STEPS),
            "maxSteps"),
        toInt(
            readLong(
                table,
                environment,
                "maxToolCallsPerTurn",
                "SATURN_AGENT_MAX_TOOL_CALLS_PER_TURN",
                maxToolCalls),
            "maxToolCallsPerTurn"),
        Duration.ofMillis(
            readLong(
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

  private static String readString(Toml table, String key, String fallback) {
    String value = table == null ? null : table.getString(key);
    return value == null ? fallback : value;
  }

  private static String readString(
      Toml table,
      Map<String, String> environment,
      String key,
      String environmentKey,
      String fallback) {
    String environmentValue = environment.get(environmentKey);
    return environmentValue == null || environmentValue.isBlank()
        ? readString(table, key, fallback)
        : environmentValue;
  }

  private static long readLong(Toml table, String key, long fallback) {
    Long value = table == null ? null : table.getLong(key);
    return value == null ? fallback : value;
  }

  private static long readLong(
      Toml table,
      Map<String, String> environment,
      String key,
      String environmentKey,
      long fallback) {
    String environmentValue = environment.get(environmentKey);
    if (environmentValue == null || environmentValue.isBlank()) {
      return readLong(table, key, fallback);
    }
    try {
      return Long.parseLong(environmentValue.strip());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(environmentKey + " must be an integer", exception);
    }
  }

  private static boolean readBoolean(
      Toml table,
      Map<String, String> environment,
      String key,
      String environmentKey,
      boolean fallback) {
    String environmentValue = environment.get(environmentKey);
    if (environmentValue == null || environmentValue.isBlank()) {
      Boolean value = table == null ? null : table.getBoolean(key);
      return value == null ? fallback : value;
    }
    if ("true".equalsIgnoreCase(environmentValue.strip())) {
      return true;
    }
    if ("false".equalsIgnoreCase(environmentValue.strip())) {
      return false;
    }
    throw new IllegalArgumentException(environmentKey + " must be true or false");
  }

  private static int toInt(long value, String name) {
    try {
      return Math.toIntExact(value);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("agent." + name + " is outside integer range", exception);
    }
  }
}
