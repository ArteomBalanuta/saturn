package org.saturn.app.agent.tool;

import org.saturn.app.agent.AgentContext;

@FunctionalInterface
public interface SaturnCommandGateway {
  boolean execute(AgentContext context, String command, String arguments);
}
