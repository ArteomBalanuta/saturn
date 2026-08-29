package org.saturn.app.agent.llm;

/** Provides the language-model completion contract used by agent routing. */
public interface LlmClient {
  LlmResponse complete(LlmRequest request) throws LlmException;
}
