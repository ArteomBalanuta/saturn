package org.saturn.app.agent.routing;

/** Semantic meaning of the newest request, independent of invocation participation mode. */
public enum AgentRequestKind {
  TALK,
  UNCLASSIFIED,
  TOOL_CALL
}
