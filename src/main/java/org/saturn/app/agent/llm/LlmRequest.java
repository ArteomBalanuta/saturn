package org.saturn.app.agent.llm;

import com.google.gson.JsonObject;
import java.util.List;
import org.saturn.app.agent.routing.AgentContextProjection;

/**
 * Represents a request submitted to a language-model client.
 *
 * @param messages request-local conversation messages
 * @param tools tool definitions offered to the provider
 * @param bypassPromptCache whether provider prompt caching should be bypassed
 * @param responseFormat optional structured response format
 * @param projection context projection associated with the request
 */
public record LlmRequest(
    List<LlmMessage> messages,
    List<JsonObject> tools,
    boolean bypassPromptCache,
    JsonObject responseFormat,
    AgentContextProjection projection) {
  /**
   * Implements the {@code LlmRequest} operation for this agent component.
   *
   * @param messages input argument used by this operation
   * @param tools input argument used by this operation
   */
  public LlmRequest(List<LlmMessage> messages, List<JsonObject> tools) {
    this(messages, tools, false, null, null);
  }

  /**
   * Implements the {@code LlmRequest} operation for this agent component.
   *
   * @param messages input argument used by this operation
   * @param tools input argument used by this operation
   * @param bypassPromptCache input argument used by this operation
   */
  public LlmRequest(List<LlmMessage> messages, List<JsonObject> tools, boolean bypassPromptCache) {
    this(messages, tools, bypassPromptCache, null, null);
  }

  /**
   * Implements the {@code LlmRequest} operation for this agent component.
   *
   * @param messages input argument used by this operation
   * @param tools input argument used by this operation
   * @param bypassPromptCache input argument used by this operation
   * @param responseFormat input argument used by this operation
   */
  public LlmRequest(
      List<LlmMessage> messages,
      List<JsonObject> tools,
      boolean bypassPromptCache,
      JsonObject responseFormat) {
    this(messages, tools, bypassPromptCache, responseFormat, null);
  }

  /**
   * Implements the {@code LlmRequest} operation for this agent component.
   *
   * @param messages input argument used by this operation
   * @param tools input argument used by this operation
   * @param projection input argument used by this operation
   */
  public LlmRequest(
      List<LlmMessage> messages, List<JsonObject> tools, AgentContextProjection projection) {
    this(messages, tools, false, null, projection);
  }

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param tools the tools input; null handling follows the validation performed by this
   *     declaration
   * @param bypassPromptCache the bypassPromptCache input; null handling follows the validation
   *     performed by this declaration
   * @param responseFormat the responseFormat input; null handling follows the validation performed
   *     by this declaration
   * @param projection the projection input; null handling follows the validation performed by this
   *     declaration
   */
  public LlmRequest {
    messages = List.copyOf(messages);
    tools = List.copyOf(tools);
    if (projection != null
        && !projection
            .fingerprint()
            .equals(org.saturn.app.agent.routing.AgentMessageProjector.fingerprintOf(messages))) {
      throw new IllegalArgumentException("projection fingerprint does not match request messages");
    }
  }

  /**
   * Implements the {@code withoutPromptCache} operation for this agent component.
   *
   * @param messages input argument used by this operation
   * @param tools input argument used by this operation
   * @return the operation result
   */
  public static LlmRequest withoutPromptCache(List<LlmMessage> messages, List<JsonObject> tools) {
    return new LlmRequest(messages, tools, true);
  }
}
