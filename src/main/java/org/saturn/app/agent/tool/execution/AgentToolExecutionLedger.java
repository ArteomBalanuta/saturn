package org.saturn.app.agent.tool.execution;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.saturn.app.agent.api.AgentToolDescriptor;

/** Thread-safe request-local accounting for tool limits, prerequisites, and duplicate calls. */
final class AgentToolExecutionLedger {
  private final Set<String> invocationKeys = new HashSet<>();
  private final Set<String> inFlightInvocationKeys = new HashSet<>();
  private final Set<String> successfulTools = new HashSet<>();
  private final Set<String> disabledTools = new HashSet<>();
  private final Map<String, Integer> callsByTool = new HashMap<>();
  private final Map<String, Integer> failuresByTool = new HashMap<>();

  synchronized boolean isDisabled(String toolName) {
    return disabledTools.contains(toolName);
  }

  synchronized Set<String> missingPrerequisites(AgentToolDescriptor descriptor) {
    Set<String> missing = new HashSet<>(descriptor.requiredSuccessfulTools());
    missing.removeAll(successfulTools);
    return Set.copyOf(missing);
  }

  synchronized Reservation reserve(String invocationKey, String toolName, int maxCallsPerTool) {
    if (invocationKeys.contains(invocationKey) || inFlightInvocationKeys.contains(invocationKey)) {
      return Reservation.DUPLICATE;
    }
    int calls = callsByTool.getOrDefault(toolName, 0);
    if (calls >= maxCallsPerTool) {
      return Reservation.LIMIT_REACHED;
    }
    callsByTool.put(toolName, calls + 1);
    inFlightInvocationKeys.add(invocationKey);
    return Reservation.ACCEPTED;
  }

  synchronized void recordSuccess(String invocationKey, String toolName) {
    inFlightInvocationKeys.remove(invocationKey);
    invocationKeys.add(invocationKey);
    successfulTools.add(toolName);
  }

  synchronized void recordFailure(String invocationKey, String toolName, int maxFailures) {
    inFlightInvocationKeys.remove(invocationKey);
    int failures = failuresByTool.merge(toolName, 1, Integer::sum);
    if (failures >= maxFailures) {
      disabledTools.add(toolName);
    }
  }

  synchronized void recordValidationFailure(String toolName, int maxFailures) {
    int failures = failuresByTool.merge(toolName, 1, Integer::sum);
    if (failures >= maxFailures) {
      disabledTools.add(toolName);
    }
  }

  /** Enumerates the possible reservation states used by the enclosing agent component. */
  enum Reservation {
    ACCEPTED,
    DUPLICATE,
    LIMIT_REACHED
  }
}
