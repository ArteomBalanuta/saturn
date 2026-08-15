package org.saturn.app.agent.llm;

public interface LlmClient {
  LlmResponse complete(LlmRequest request) throws LlmException;
}
