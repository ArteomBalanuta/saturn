package org.saturn.app.agent.api;

/** Routes one agent invocation through the provider, tool loop, and Saturn response policy. */
public interface AgentRouter {
  /**
   * Produces the agent result for one request.
   *
   * @throws AgentRoutingException when the provider or bounded execution loop cannot produce a
   *     valid result
   */
  AgentResult route(AgentInvocation invocation) throws AgentRoutingException;
}
