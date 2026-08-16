package org.saturn.app.agent;

/** Ordering category assigned to a tool invocation before the scheduler receives it. */
enum AgentToolExecutionMode {
  PARALLEL_READ,
  SEQUENTIAL_DEPENDENT_READ,
  SEQUENTIAL_ACTION
}
