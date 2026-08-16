package org.saturn.app.agent;

import org.saturn.app.agent.llm.LlmException;

/** Applies one ordered response policy without owning tool execution or persistence. */
interface AgentTurnPolicy {
  AgentTurnPolicyResult apply(AgentTurnPolicyInput input)
      throws LlmException, AgentRoutingException;
}
