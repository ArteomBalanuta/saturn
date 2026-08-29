package org.saturn.app.agent.turn;

import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.llm.LlmException;

/** Applies one ordered response policy without owning tool execution or persistence. */
public interface AgentTurnPolicy {
  AgentTurnPolicyResult apply(AgentTurnPolicyInput input)
      throws LlmException, AgentRoutingException;
}
