package org.saturn.app.agent.tool.execution;

import java.util.Objects;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.api.ToolResponseEnvelope;
import org.saturn.app.agent.api.ToolResultMode;
import org.saturn.app.agent.llm.LlmToolCall;
import org.saturn.app.agent.routing.AgentPromptCatalog;
import org.saturn.app.agent.turn.AgentFreshDataCoordinator;

/** Renders tool results into the envelope visible to the model after execution. */
public final class AgentModelVisibleToolResultRenderer
    implements AgentFreshDataCoordinator.ToolResultRenderer {
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();
  private static final String ROOM_DELIVERY_RESPONSE =
      PROMPTS.text("router-room-delivery.txt").strip();

  private final AgentToolRegistry registry;

  public AgentModelVisibleToolResultRenderer(AgentToolRegistry registry) {
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
