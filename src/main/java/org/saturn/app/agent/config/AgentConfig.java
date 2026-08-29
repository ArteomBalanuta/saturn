package org.saturn.app.agent.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Immutable, validated configuration for provider access and bounded agent execution. */
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
  private static final int DEFAULT_MAX_COMPLETION_TOKENS = 1024;
  private static final int DEFAULT_MAX_STEPS = 5;
  private static final Duration DEFAULT_TOOL_TIMEOUT = Duration.ofSeconds(10);

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param enabled the enabled input; null handling follows the validation performed by this
   *     declaration
   * @param endpoint the endpoint input; null handling follows the validation performed by this
   *     declaration
   * @param model the model input; null handling follows the validation performed by this
   *     declaration
   * @param apiKey the apiKey input; null handling follows the validation performed by this
   *     declaration
   * @param timeout the timeout input; null handling follows the validation performed by this
   *     declaration
   * @param maxConcurrentRequests the maxConcurrentRequests input; null handling follows the
   *     validation performed by this declaration
   * @param maxToolCalls the maxToolCalls input; null handling follows the validation performed by
   *     this declaration
   * @param maxCallsPerTool the maxCallsPerTool input; null handling follows the validation
   *     performed by this declaration
   * @param maxToolFailures the maxToolFailures input; null handling follows the validation
   *     performed by this declaration
   * @param maxPromptChars the maxPromptChars input; null handling follows the validation performed
   *     by this declaration
   * @param maxOutputChars the maxOutputChars input; null handling follows the validation performed
   *     by this declaration
   * @param memoryTurns the memoryTurns input; null handling follows the validation performed by
   *     this declaration
   * @param memoryTtl the memoryTtl input; null handling follows the validation performed by this
   *     declaration
   * @param maxRetries the maxRetries input; null handling follows the validation performed by this
   *     declaration
   * @param retryBackoff the retryBackoff input; null handling follows the validation performed by
   *     this declaration
   * @param maxCompletionTokens the maxCompletionTokens input; null handling follows the validation
   *     performed by this declaration
   * @param thinkingEnabled the thinkingEnabled input; null handling follows the validation
   *     performed by this declaration
   * @param maxSteps the maxSteps input; null handling follows the validation performed by this
   *     declaration
   * @param maxToolCallsPerTurn the maxToolCallsPerTurn input; null handling follows the validation
   *     performed by this declaration
   * @param toolTimeout the toolTimeout input; null handling follows the validation performed by
   *     this declaration
   */
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

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param enabled the enabled input; null handling follows the validation performed by this
   *     declaration
   * @param endpoint the endpoint input; null handling follows the validation performed by this
   *     declaration
   * @param model the model input; null handling follows the validation performed by this
   *     declaration
   * @param apiKey the apiKey input; null handling follows the validation performed by this
   *     declaration
   * @param timeout the timeout input; null handling follows the validation performed by this
   *     declaration
   * @param maxConcurrentRequests the maxConcurrentRequests input; null handling follows the
   *     validation performed by this declaration
   * @param maxToolCalls the maxToolCalls input; null handling follows the validation performed by
   *     this declaration
   * @param maxCallsPerTool the maxCallsPerTool input; null handling follows the validation
   *     performed by this declaration
   * @param maxToolFailures the maxToolFailures input; null handling follows the validation
   *     performed by this declaration
   * @param maxPromptChars the maxPromptChars input; null handling follows the validation performed
   *     by this declaration
   * @param maxOutputChars the maxOutputChars input; null handling follows the validation performed
   *     by this declaration
   * @param memoryTurns the memoryTurns input; null handling follows the validation performed by
   *     this declaration
   * @param memoryTtl the memoryTtl input; null handling follows the validation performed by this
   *     declaration
   * @param maxRetries the maxRetries input; null handling follows the validation performed by this
   *     declaration
   * @param retryBackoff the retryBackoff input; null handling follows the validation performed by
   *     this declaration
   * @param maxCompletionTokens the maxCompletionTokens input; null handling follows the validation
   *     performed by this declaration
   * @param thinkingEnabled the thinkingEnabled input; null handling follows the validation
   *     performed by this declaration
   */
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

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param enabled the enabled input; null handling follows the validation performed by this
   *     declaration
   * @param endpoint the endpoint input; null handling follows the validation performed by this
   *     declaration
   * @param model the model input; null handling follows the validation performed by this
   *     declaration
   * @param apiKey the apiKey input; null handling follows the validation performed by this
   *     declaration
   * @param timeout the timeout input; null handling follows the validation performed by this
   *     declaration
   * @param maxConcurrentRequests the maxConcurrentRequests input; null handling follows the
   *     validation performed by this declaration
   * @param maxToolCalls the maxToolCalls input; null handling follows the validation performed by
   *     this declaration
   * @param maxCallsPerTool the maxCallsPerTool input; null handling follows the validation
   *     performed by this declaration
   * @param maxToolFailures the maxToolFailures input; null handling follows the validation
   *     performed by this declaration
   * @param maxPromptChars the maxPromptChars input; null handling follows the validation performed
   *     by this declaration
   * @param maxOutputChars the maxOutputChars input; null handling follows the validation performed
   *     by this declaration
   * @param memoryTurns the memoryTurns input; null handling follows the validation performed by
   *     this declaration
   * @param memoryTtl the memoryTtl input; null handling follows the validation performed by this
   *     declaration
   * @param maxRetries the maxRetries input; null handling follows the validation performed by this
   *     declaration
   * @param retryBackoff the retryBackoff input; null handling follows the validation performed by
   *     this declaration
   */
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
