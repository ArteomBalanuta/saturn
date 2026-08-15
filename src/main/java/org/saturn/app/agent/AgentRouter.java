package org.saturn.app.agent;

public interface AgentRouter {
  AgentResult route(AgentInvocation invocation) throws AgentRoutingException;
}
