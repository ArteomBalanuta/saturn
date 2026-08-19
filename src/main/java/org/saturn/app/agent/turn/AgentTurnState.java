package org.saturn.app.agent.turn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.saturn.app.agent.api.AgentExecutionLimits;
import org.saturn.app.agent.api.AgentToolResult;

/**
 * Mutable state owned by exactly one router turn.
 *
 * <p>This keeps execution budgets, correction attempts, and observed tool outcomes out of the
 * router's local control-flow variables. It is intentionally not thread-safe: one router turn
 * advances it on its owning session lock.
 */
public final class AgentTurnState {
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

  public AgentTurnState(AgentExecutionLimits limits) {
    this.executionState = new AgentExecutionState(limits);
  }

  public boolean advanceStep() {
    return executionState.advanceStep();
  }

  public boolean reserveToolCalls(int requestedCalls) {
    return executionState.reserveToolCalls(requestedCalls);
  }

  public boolean commandCorrectionUsed() {
    return commandCorrectionUsed;
  }

  public void markCommandCorrectionUsed() {
    commandCorrectionUsed = true;
  }

  public void clearCommandCorrection() {
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

  public void resetUnverifiedActionCheck() {
    unverifiedActionChecked = false;
  }

  public boolean toolsEnabled() {
    return toolsEnabled;
  }

  public void disableTools() {
    toolsEnabled = false;
  }

  public boolean recordSuccessfulCommand(String command) {
    return successfulCommands.add(command);
  }

  public void recordFailedCommand(String command) {
    failedCommands.add(command);
  }

  public boolean recordSuccessfulTool(String tool) {
    return successfulTools.add(tool);
  }

  public void recordSuccessfulToolResult(AgentToolResult result) {
    successfulToolResults.add(result);
  }

  public boolean hasSuccessfulCommand(String command) {
    return successfulCommands.contains(command);
  }

  public boolean hasSuccessfulCommands() {
    return !successfulCommands.isEmpty();
  }

  public boolean hasSuccessfulTool(String tool) {
    return successfulTools.contains(tool);
  }

  Set<String> successfulCommands() {
    return Set.copyOf(successfulCommands);
  }

  public Set<String> failedCommands() {
    return Set.copyOf(failedCommands);
  }

  Set<String> successfulTools() {
    return Set.copyOf(successfulTools);
  }

  public List<AgentToolResult> successfulToolResults() {
    return List.copyOf(successfulToolResults);
  }
}
