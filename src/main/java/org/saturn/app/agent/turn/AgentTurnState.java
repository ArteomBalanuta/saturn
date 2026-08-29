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
  private int attemptedToolCount;
  private int successfulToolCount;
  private int failedToolCount;

  /**
   * Implements the {@code AgentTurnState} operation for this agent component.
   *
   * @param limits input argument used by this operation
   */
  public AgentTurnState(AgentExecutionLimits limits) {
    this.executionState = new AgentExecutionState(limits);
  }

  /**
   * Implements the {@code advanceStep} operation for this agent component.
   *
   * @return the operation result
   */
  public boolean advanceStep() {
    return executionState.advanceStep();
  }

  /**
   * Implements the {@code reserveToolCalls} operation for this agent component.
   *
   * @param requestedCalls input argument used by this operation
   * @return the operation result
   */
  public boolean reserveToolCalls(int requestedCalls) {
    return executionState.reserveToolCalls(requestedCalls);
  }

  /**
   * Implements the {@code commandCorrectionUsed} operation for this agent component.
   *
   * @return the operation result
   */
  public boolean commandCorrectionUsed() {
    return commandCorrectionUsed;
  }

  /** Implements the {@code markCommandCorrectionUsed} operation for this agent component. */
  public void markCommandCorrectionUsed() {
    commandCorrectionUsed = true;
  }

  /** Implements the {@code clearCommandCorrection} operation for this agent component. */
  public void clearCommandCorrection() {
    commandCorrectionUsed = false;
  }

  boolean freshnessCorrectionUsed() {
    return freshnessCorrectionUsed;
  }

  /** Marks that a freshness correction has been consumed for this turn. */
  void markFreshnessCorrectionUsed() {
    /** Reports whether fresh-synthesis correction has already been consumed. */
    freshnessCorrectionUsed = true;
    /** Marks that a fresh-synthesis correction has been consumed for this turn. */
  }

  /** Reports whether the unverified-action check has already been performed. */
  boolean freshSynthesisCorrectionUsed() {
    return freshSynthesisCorrectionUsed;
  }

  /** Marks fresh-synthesis correction as used for this turn. */
  void markFreshSynthesisCorrectionUsed() {
    freshSynthesisCorrectionUsed = true;
  }

  /**
   * Reports whether the unverified-action check has been performed.
   *
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  boolean unverifiedActionChecked() {
    return unverifiedActionChecked;
  }

  void markUnverifiedActionChecked() {
    unverifiedActionChecked = true;
  }

  /** Implements the {@code resetUnverifiedActionCheck} operation for this agent component. */
  public void resetUnverifiedActionCheck() {
    unverifiedActionChecked = false;
  }

  /**
   * Implements the {@code toolsEnabled} operation for this agent component.
   *
   * @return the operation result
   */
  public boolean toolsEnabled() {
    return toolsEnabled;
  }

  /** Implements the {@code disableTools} operation for this agent component. */
  public void disableTools() {
    toolsEnabled = false;
  }

  /**
   * Implements the {@code markToolAttempted} operation for this agent component.
   *
   * @param count input argument used by this operation
   */
  public void markToolAttempted(int count) {
    if (count < 0) {
      throw new IllegalArgumentException("tool attempt count must not be negative");
    }
    attemptedToolCount += count;
  }

  /** Implements the {@code recordToolSuccess} operation for this agent component. */
  public void recordToolSuccess() {
    if (successfulToolCount + failedToolCount >= attemptedToolCount) {
      throw new IllegalStateException("tool result exceeds attempted tool count");
    }
    successfulToolCount++;
  }

  /** Implements the {@code recordToolFailure} operation for this agent component. */
  public void recordToolFailure() {
    if (successfulToolCount + failedToolCount >= attemptedToolCount) {
      throw new IllegalStateException("tool result exceeds attempted tool count");
    }
    failedToolCount++;
  }

  /**
   * Implements the {@code attemptedToolCount} operation for this agent component.
   *
   * @return the operation result
   */
  public int attemptedToolCount() {
    return attemptedToolCount;
  }

  /**
   * Implements the {@code toolEvidence} operation for this agent component.
   *
   * @return the operation result
   */
  public AgentToolEvidence toolEvidence() {
    return new AgentToolEvidence(
        attemptedToolCount > 0, attemptedToolCount, successfulToolCount, failedToolCount);
  }

  /**
   * Implements the {@code recordSuccessfulCommand} operation for this agent component.
   *
   * @param command input argument used by this operation
   * @return the operation result
   */
  public boolean recordSuccessfulCommand(String command) {
    return successfulCommands.add(command);
  }

  /**
   * Implements the {@code recordFailedCommand} operation for this agent component.
   *
   * @param command input argument used by this operation
   */
  public void recordFailedCommand(String command) {
    failedCommands.add(command);
  }

  /**
   * Implements the {@code recordSuccessfulTool} operation for this agent component.
   *
   * @param tool input argument used by this operation
   * @return the operation result
   */
  public boolean recordSuccessfulTool(String tool) {
    return successfulTools.add(tool);
  }

  /**
   * Implements the {@code recordSuccessfulToolResult} operation for this agent component.
   *
   * @param result input argument used by this operation
   */
  public void recordSuccessfulToolResult(AgentToolResult result) {
    successfulToolResults.add(result);
  }

  /**
   * Implements the {@code hasSuccessfulCommand} operation for this agent component.
   *
   * @param command input argument used by this operation
   * @return the operation result
   */
  public boolean hasSuccessfulCommand(String command) {
    return successfulCommands.contains(command);
  }

  /**
   * Implements the {@code hasSuccessfulCommands} operation for this agent component.
   *
   * @return the operation result
   */
  public boolean hasSuccessfulCommands() {
    return !successfulCommands.isEmpty();
  }

  /**
   * Implements the {@code hasSuccessfulTool} operation for this agent component.
   *
   * @param tool input argument used by this operation
   * @return the operation result
   */
  public boolean hasSuccessfulTool(String tool) {
    return successfulTools.contains(tool);
  }

  /**
   * Returns an immutable snapshot of successfully completed commands.
   *
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  Set<String> successfulCommands() {
    return Set.copyOf(successfulCommands);
  }

  /**
   * Implements the {@code failedCommands} operation for this agent component.
   *
   * @return the operation result
   */
  public Set<String> failedCommands() {
    return Set.copyOf(failedCommands);
  }

  /**
   * Returns an immutable snapshot of successfully completed tools.
   *
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  Set<String> successfulTools() {
    return Set.copyOf(successfulTools);
  }

  /**
   * Implements the {@code successfulToolResults} operation for this agent component.
   *
   * @return the operation result
   */
  public List<AgentToolResult> successfulToolResults() {
    return List.copyOf(successfulToolResults);
  }
}
