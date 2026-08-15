package org.saturn.app.agent;

import com.moandjiezana.toml.Toml;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AgentConfig(
    boolean enabled,
    URI endpoint,
    Optional<String> model,
    String apiKey,
    Duration timeout,
    int maxConcurrentRequests,
    int maxToolCalls,
    int maxCallsPerTool,
    int maxToolFailures,
    int maxPromptChars,
    int maxOutputChars,
    int memoryTurns,
    Duration memoryTtl,
    int maxRetries,
    Duration retryBackoff,
    int maxCompletionTokens,
    boolean thinkingEnabled) {
  private static final String DEFAULT_ENDPOINT = "http://83.218.196.156:16261";
  private static final int DEFAULT_MAX_COMPLETION_TOKENS = 768;

  public AgentConfig {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(apiKey, "apiKey");
    Objects.requireNonNull(timeout, "timeout");
    Objects.requireNonNull(memoryTtl, "memoryTtl");
    Objects.requireNonNull(retryBackoff, "retryBackoff");
    validateEndpoint(endpoint);
    requirePositive(timeout, "timeout");
    requirePositive(maxConcurrentRequests, "maxConcurrentRequests");
    requirePositive(maxToolCalls, "maxToolCalls");
    requirePositive(maxCallsPerTool, "maxCallsPerTool");
    requirePositive(maxToolFailures, "maxToolFailures");
    requirePositive(maxPromptChars, "maxPromptChars");
    requirePositive(maxOutputChars, "maxOutputChars");
    requirePositive(memoryTurns, "memoryTurns");
    requirePositive(memoryTtl, "memoryTtl");
    requirePositive(maxCompletionTokens, "maxCompletionTokens");
    if (maxRetries < 0) {
      throw new IllegalArgumentException("agent.maxRetries must not be negative");
    }
    if (retryBackoff.isNegative()) {
      throw new IllegalArgumentException("agent.retryBackoff must not be negative");
    }
  }

  public AgentConfig(
      boolean enabled,
      URI endpoint,
      Optional<String> model,
      String apiKey,
      Duration timeout,
      int maxConcurrentRequests,
      int maxToolCalls,
      int maxCallsPerTool,
      int maxToolFailures,
      int maxPromptChars,
      int maxOutputChars,
      int memoryTurns,
      Duration memoryTtl,
      int maxRetries,
      Duration retryBackoff) {
    this(
        enabled,
        endpoint,
        model,
        apiKey,
        timeout,
        maxConcurrentRequests,
        maxToolCalls,
        maxCallsPerTool,
        maxToolFailures,
        maxPromptChars,
        maxOutputChars,
        memoryTurns,
        memoryTtl,
        maxRetries,
        retryBackoff,
        DEFAULT_MAX_COMPLETION_TOKENS,
        false);
  }

  public static AgentConfig from(Toml root, Map<String, String> environment) {
    Toml table = root == null ? null : root.getTable("agent");
    boolean enabled = readBoolean(table, "enabled", true);
    URI endpoint = normalizeEndpoint(URI.create(readString(table, "endpoint", DEFAULT_ENDPOINT)));
    String model = readString(table, "model", "").trim();
    String apiKeyEnv = readString(table, "apiKeyEnv", "SATURN_AGENT_API_KEY").trim();
    String apiKey = apiKeyEnv.isEmpty() ? "" : environment.getOrDefault(apiKeyEnv, "");

    return new AgentConfig(
        enabled,
        endpoint,
        model.isEmpty() ? Optional.empty() : Optional.of(model),
        apiKey,
        Duration.ofSeconds(readLong(table, "timeoutSeconds", 30)),
        toInt(readLong(table, "maxConcurrentRequests", 2), "maxConcurrentRequests"),
        toInt(readLong(table, "maxToolCalls", 4), "maxToolCalls"),
        toInt(readLong(table, "maxCallsPerTool", 2), "maxCallsPerTool"),
        toInt(readLong(table, "maxToolFailures", 2), "maxToolFailures"),
        toInt(readLong(table, "maxPromptChars", 8_000), "maxPromptChars"),
        toInt(readLong(table, "maxOutputChars", 8_000), "maxOutputChars"),
        toInt(readLong(table, "memoryTurns", 30), "memoryTurns"),
        Duration.ofHours(readLong(table, "memoryTtlHours", 168)),
        toInt(readLong(table, "maxRetries", 2), "maxRetries"),
        Duration.ofMillis(readLong(table, "retryBackoffMillis", 250)),
        toInt(
            readLong(table, "maxCompletionTokens", DEFAULT_MAX_COMPLETION_TOKENS),
            "maxCompletionTokens"),
        readBoolean(table, "thinkingEnabled", false));
  }

  private static void validateEndpoint(URI endpoint) {
    String scheme = endpoint.getScheme();
    boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    if (!supportedScheme || endpoint.isOpaque() || endpoint.getHost() == null) {
      throw new IllegalArgumentException("agent.endpoint must be an absolute HTTP(S) URL");
    }
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

  private static long readLong(Toml table, String key, long fallback) {
    Long value = table == null ? null : table.getLong(key);
    return value == null ? fallback : value;
  }

  private static boolean readBoolean(Toml table, String key, boolean fallback) {
    Boolean value = table == null ? null : table.getBoolean(key);
    return value == null ? fallback : value;
  }

  private static int toInt(long value, String name) {
    try {
      return Math.toIntExact(value);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("agent." + name + " is outside integer range", exception);
    }
  }

  private static void requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException("agent." + name + " must be positive");
    }
  }

  private static void requirePositive(Duration value, String name) {
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("agent." + name + " must be positive");
    }
  }
}
