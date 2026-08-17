package org.saturn.app.agent;

import java.util.Objects;
import org.saturn.app.agent.llm.LlmToolCall;

/** Renders tool results into the envelope visible to the model after execution. */
final class AgentModelVisibleToolResultRenderer
    implements AgentFreshDataCoordinator.ToolResultRenderer {
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();
  private static final String ROOM_DELIVERY_RESPONSE =
      PROMPTS.text("router-room-delivery.txt").strip();

  private final AgentToolRegistry registry;

  AgentModelVisibleToolResultRenderer(AgentToolRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  @Override
  public String render(AgentContext context, LlmToolCall call, AgentToolResult result) {
    if (result.isError()) {
      return result.envelopeJson();
    }
    return registry
        .find(context, call.name())
        .map(tool -> tool.descriptor(context).resultMode())
        .filter(mode -> mode == ToolResultMode.ROOM_DELIVERY)
        .map(mode -> ToolResponseEnvelope.success(ROOM_DELIVERY_RESPONSE).toJson())
        .orElse(result.envelopeJson());
  }
}
