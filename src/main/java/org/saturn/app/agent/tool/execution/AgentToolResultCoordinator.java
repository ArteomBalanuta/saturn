package org.saturn.app.agent.tool.execution;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmToolCall;
import org.saturn.app.agent.routing.AgentCommandProseGuard;
import org.saturn.app.agent.turn.AgentFreshDataPolicy;
import org.saturn.app.agent.turn.AgentTurnState;

/** Owns per-tool-result state transitions and model-visible tool-message assembly. */
@Slf4j
public final class AgentToolResultCoordinator {
  private final AgentFreshDataPolicy freshDataPolicy;
  private final AgentCommandProseGuard commandProseGuard;

  public AgentToolResultCoordinator(
      AgentFreshDataPolicy freshDataPolicy, AgentCommandProseGuard commandProseGuard) {
    this.freshDataPolicy = freshDataPolicy;
    this.commandProseGuard = commandProseGuard;
  }

  public void record(
      AgentContext context,
      List<LlmToolCall> calls,
      List<AgentToolResult> results,
      Optional<String> requiredFreshTool,
      Optional<String> requiredFreshNick,
      AgentTurnState turnState,
      List<LlmMessage> messages,
      ToolResultRenderer resultRenderer,
      String correlationId)
      throws AgentRoutingException {
    int alreadyAttempted = turnState.attemptedToolCount();
    if (alreadyAttempted < calls.size()) {
      turnState.markToolAttempted(calls.size() - alreadyAttempted);
    }
    if (calls.size() != results.size()) {
      throw new AgentRoutingException("Agent tool result count did not match tool call count");
    }
    for (AgentToolResult result : results) {
      if (result == null) {
        throw new AgentRoutingException("Agent tool result was null");
      }
    }
    for (int index = 0; index < calls.size(); index++) {
      LlmToolCall call = calls.get(index);
      AgentToolResult result = results.get(index);
      if (result.isError()) {
        turnState.recordToolFailure();
      } else {
        turnState.recordToolSuccess();
      }
      log.info(
          "Agent tool completed, correlationId={}, tool={}, outcome={}",
          correlationId,
          call.name(),
          result.isError() ? "error" : "success");
      if (result.isError()
          && requiredFreshTool.filter(call.name()::equals).isPresent()
          && !turnState.hasSuccessfulTool(call.name())) {
        throw new AgentRoutingException("Required fresh-data tool failed: " + call.name());
      }
      if (!result.isError()
          && freshDataPolicy.matchesTarget(call, requiredFreshNick)
          && turnState.recordSuccessfulTool(call.name())) {
        log.info(
            "Agent fresh data satisfied, correlationId={}, tool={}", correlationId, call.name());
      }
      if (!result.isError()) {
        turnState.recordSuccessfulToolResult(result);
      }
      if (!result.isError() && "run_command".equals(call.name())) {
        commandProseGuard.executedCommand(call).ifPresent(turnState::recordSuccessfulCommand);
        turnState.clearCommandCorrection();
      } else if (result.isError() && "run_command".equals(call.name())) {
        commandProseGuard.executedCommand(call).ifPresent(turnState::recordFailedCommand);
      }
      messages.add(LlmMessage.tool(call.id(), resultRenderer.render(context, call, result)));
    }
  }

  @FunctionalInterface
  /** Defines the operation used to tool result renderer. */
  public interface ToolResultRenderer {
    String render(AgentContext context, LlmToolCall call, AgentToolResult result);
  }
}
