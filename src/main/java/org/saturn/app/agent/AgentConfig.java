package org.saturn.app.agent;

import com.moandjiezana.toml.Toml;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, validated configuration for provider access and bounded agent execution.
 *
 * <p>{@link #from(Toml, Map)} reads TOML defaults and gives non-blank {@code SATURN_AGENT_*}
 * environment values precedence. Credentials are read only from the environment variable named by
 * {@code apiKeyEnv}.
 */
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
    boolean thinkingEnabled,
    int maxSteps,
    int maxToolCallsPerTurn,
    Duration toolTimeout) {
  private static final String DEFAULT_ENDPOINT = "http://localhost:16261";
  private static final int DEFAULT_MAX_COMPLETION_TOKENS = 768;
  private static final int DEFAULT_MAX_STEPS = 5;
  private static final Duration DEFAULT_TOOL_TIMEOUT = Duration.ofSeconds(10);

  public AgentConfig {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(apiKey, "apiKey");
    Objects.requireNonNull(timeout, "timeout");
    Objects.requireNonNull(memoryTtl, "memoryTtl");
    Objects.requireNonNull(retryBackoff, "retryBackoff");
    Objects.requireNonNull(toolTimeout, "toolTimeout");
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
    requirePositive(maxSteps, "maxSteps");
    requirePositive(maxToolCallsPerTurn, "maxToolCallsPerTurn");
    requirePositive(toolTimeout, "toolTimeout");
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
      Duration retryBackoff,
      int maxCompletionTokens,
      boolean thinkingEnabled) {
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
        maxCompletionTokens,
        thinkingEnabled,
        DEFAULT_MAX_STEPS,
        maxToolCalls,
        DEFAULT_TOOL_TIMEOUT);
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
        false,
        DEFAULT_MAX_STEPS,
        maxToolCalls,
        DEFAULT_TOOL_TIMEOUT);
  }

  public static AgentConfig from(Toml root, Map<String, String> environment) {
    return AgentConfigLoader.load(root, environment);
  }

  private static void validateEndpoint(URI endpoint) {
    String scheme = endpoint.getScheme();
    boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    if (!supportedScheme || endpoint.isOpaque() || endpoint.getHost() == null) {
      throw new IllegalArgumentException("agent.endpoint must be an absolute HTTP(S) URL");
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
