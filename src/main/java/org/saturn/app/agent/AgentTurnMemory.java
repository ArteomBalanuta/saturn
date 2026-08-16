package org.saturn.app.agent;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.llm.LlmMessage;

/** Owns agent-memory I/O, legacy-history filtering, and safe routing error translation. */
@Slf4j
final class AgentTurnMemory {
  private final AgentMemoryStore store;
  private final AgentConfig config;
  private final AgentResponseSanitizer responseSanitizer = new AgentResponseSanitizer();

  AgentTurnMemory(AgentMemoryStore store, AgentConfig config) {
    this.store = store;
    this.config = config;
  }

  List<LlmMessage> load(AgentContext context, String correlationId) throws AgentRoutingException {
    List<LlmMessage> loaded;
    try {
      loaded = store.load(context, config);
    } catch (RuntimeException exception) {
      throw memoryLoadFailure(correlationId, exception);
    }
    List<LlmMessage> history = responseSanitizer.excludeLegacyPersonaTurns(loaded);
    log.info(
        "Agent memory loaded, correlationId={}, messages={}, legacyMessagesExcluded={}",
        correlationId,
        history.size(),
        loaded.size() - history.size());
    return history;
  }

  void append(AgentContext context, String user, String assistant, String correlationId)
      throws AgentRoutingException {
    try {
      store.append(context, user, assistant, config);
    } catch (RuntimeException exception) {
      throw memoryPersistenceFailure(correlationId, exception);
    }
    log.info("Agent memory persisted, correlationId={}", correlationId);
  }

  void appendToolEvidence(AgentContext context, List<AgentToolResult> results, String correlationId)
      throws AgentRoutingException {
    try {
      for (AgentToolResult result : results) {
        store.appendToolEvidence(context, result.toolName(), result.content(), config);
      }
    } catch (RuntimeException exception) {
      throw memoryPersistenceFailure(correlationId, exception);
    }
  }

  private static AgentRoutingException memoryPersistenceFailure(
      String correlationId, RuntimeException exception) {
    log.warn(
        "Agent memory append failed, correlationId={}: {}", correlationId, exception.getMessage());
    log.debug("Agent memory append failure, correlationId={}", correlationId, exception);
    return new AgentRoutingException("Agent memory persistence failed", exception);
  }

  private static AgentRoutingException memoryLoadFailure(
      String correlationId, RuntimeException exception) {
    log.warn(
        "Agent memory load failed, correlationId={}: {}", correlationId, exception.getMessage());
    log.debug("Agent memory load failure, correlationId={}", correlationId, exception);
    return new AgentRoutingException("Agent memory load failed", exception);
  }
}
