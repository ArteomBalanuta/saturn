package org.saturn.app.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mutable state owned by exactly one router turn.
 *
 * <p>This keeps execution budgets, correction attempts, and observed tool outcomes out of the
 * router's local control-flow variables. It is intentionally not thread-safe: one router turn
 * advances it on its owning session lock.
 */
final class AgentTurnState {
  private final AgentExecutionState executionState;
  private final Set<String> successfulCommands = new HashSet<>();
  private final Set<String> failedCommands = new HashSet<>();
  private final Set<String> successfulTools = new HashSet<>();
  private final List<AgentToolResult> successfulToolResults = new ArrayList<>();
  private boolean commandCorrectionUsed;
  private boolean freshnessCorrectionUsed;
  private boolean freshSynthesisCorrectionUsed;
  private boolean unverifiedActionChecked;
  private boolean toolsEnabled = true;

  AgentTurnState(AgentExecutionLimits limits) {
    this.executionState = new AgentExecutionState(limits);
  }

  boolean advanceStep() {
    return executionState.advanceStep();
  }

  boolean reserveToolCalls(int requestedCalls) {
    return executionState.reserveToolCalls(requestedCalls);
  }

  boolean commandCorrectionUsed() {
    return commandCorrectionUsed;
  }

  void markCommandCorrectionUsed() {
    commandCorrectionUsed = true;
  }

  void clearCommandCorrection() {
    commandCorrectionUsed = false;
  }

  boolean freshnessCorrectionUsed() {
    return freshnessCorrectionUsed;
  }

  void markFreshnessCorrectionUsed() {
    freshnessCorrectionUsed = true;
  }

  boolean freshSynthesisCorrectionUsed() {
    return freshSynthesisCorrectionUsed;
  }

  void markFreshSynthesisCorrectionUsed() {
    freshSynthesisCorrectionUsed = true;
  }

  boolean unverifiedActionChecked() {
    return unverifiedActionChecked;
  }

  void markUnverifiedActionChecked() {
    unverifiedActionChecked = true;
  }

  void resetUnverifiedActionCheck() {
    unverifiedActionChecked = false;
  }

  boolean toolsEnabled() {
    return toolsEnabled;
  }

  void disableTools() {
    toolsEnabled = false;
  }

  boolean recordSuccessfulCommand(String command) {
    return successfulCommands.add(command);
  }

  void recordFailedCommand(String command) {
    failedCommands.add(command);
  }

  boolean recordSuccessfulTool(String tool) {
    return successfulTools.add(tool);
  }

  void recordSuccessfulToolResult(AgentToolResult result) {
    successfulToolResults.add(result);
  }

  Set<String> successfulCommands() {
    return Set.copyOf(successfulCommands);
  }

  Set<String> failedCommands() {
    return Set.copyOf(failedCommands);
  }

  Set<String> successfulTools() {
    return Set.copyOf(successfulTools);
  }

  List<AgentToolResult> successfulToolResults() {
    return List.copyOf(successfulToolResults);
  }
}
