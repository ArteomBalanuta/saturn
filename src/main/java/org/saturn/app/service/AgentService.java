package org.saturn.app.service;

import org.saturn.app.agent.api.AgentInvocation;

public interface AgentService extends AutoCloseable {
  boolean submit(AgentInvocation invocation);

  @Override
  void close();
}
