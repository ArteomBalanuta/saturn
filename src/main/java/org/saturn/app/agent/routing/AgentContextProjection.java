package org.saturn.app.agent.routing;

import java.util.List;
import org.saturn.app.agent.llm.LlmMessage;

/** Immutable accounting for one provider-facing context projection. */
public record AgentContextProjection(
    List<LlmMessage> messages,
    int serializedChars,
    int estimatedTokens,
    int budgetChars,
    boolean pruned,
    boolean overflow,
    int removedUnits,
    String fingerprint) {
  public AgentContextProjection {
    messages = List.copyOf(messages);
    fingerprint = fingerprint == null ? "" : fingerprint;
  }
}
