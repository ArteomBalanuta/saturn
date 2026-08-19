package org.saturn.app.agent.turn;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.LlmToolCall;
import org.saturn.app.agent.routing.AgentPromptCatalog;
import org.saturn.app.agent.routing.AgentResponseCorrector;
import org.saturn.app.agent.tool.execution.AgentToolExecutor;

/** Owns mandatory fresh-data lookup and synthesis validation without owning the router loop. */
@Slf4j
public final class AgentFreshDataCoordinator {
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();
  private static final String FRESH_TOOL_CORRECTION =
      PROMPTS.text("router-fresh-tool-correction.txt");
  private static final String FRESH_SYNTHESIS_CORRECTION =
      PROMPTS.text("router-fresh-synthesis-correction.txt").strip();

  private final LlmClient client;
  private final AgentFreshDataPolicy policy;

  public AgentFreshDataCoordinator(LlmClient client, AgentFreshDataPolicy policy) {
    this.client = client;
    this.policy = policy;
  }

  public Result process(
      LlmResponse response,
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      List<LlmMessage> history,
      Optional<String> requiredFreshTool,
      Optional<String> requiredFreshNick,
      AgentContext context,
      AgentToolExecutor toolExecutor,
      AgentTurnState turnState,
      String correlationId,
      ToolResultRenderer resultRenderer,
      DefinitionProvider definitionProvider)
      throws AgentRoutingException, LlmException {
    Optional<String> missingFreshTool =
        requiredFreshTool.filter(tool -> !turnState.hasSuccessfulTool(tool));
    if (missingFreshTool.isPresent()) {
      String tool = missingFreshTool.orElseThrow();
      if (AgentFreshnessPolicy.USER_MESSAGE_HISTORY.equals(tool) && requiredFreshNick.isPresent()) {
        return loadRequiredHistory(
            response,
            messages,
            definitions,
            tool,
            requiredFreshNick.orElseThrow(),
            context,
            toolExecutor,
            turnState,
            correlationId,
            resultRenderer);
      }
      if (!policy.isExactToolCall(response, tool, requiredFreshNick)) {
        if (turnState.freshnessCorrectionUsed()) {
          throw new AgentRoutingException("Agent did not call the required fresh-data tool");
        }
        if (!turnState.toolsEnabled()) {
          throw new AgentRoutingException(
              "Required fresh-data tool is unavailable after tool-call budget exhaustion");
        }
        log.warn("Agent fresh data required, correlationId={}, tool={}", correlationId, tool);
        messages.add(LlmMessage.assistant(response.content(), List.of()));
        messages.add(LlmMessage.user(FRESH_TOOL_CORRECTION.formatted(tool).strip()));
        response =
            policy.requireExactToolCall(
                AgentResponseCorrector.requireResponse(
                    client.complete(
                        new LlmRequest(
                            messages, definitionProvider.definitionsFor(definitions, tool)))),
                tool,
                requiredFreshNick);
        turnState.markFreshnessCorrectionUsed();
      }
    } else {
      if (requiredFreshTool.filter(turnState::hasSuccessfulTool).isPresent()
          && policy.repeatsPreviousAssistant(response, history)) {
        log.warn(
            "Agent reused a pre-lookup summary; requesting fresh synthesis, correlationId={}",
            correlationId);
        messages.add(LlmMessage.assistant(response.content(), List.of()));
        messages.add(LlmMessage.user(FRESH_SYNTHESIS_CORRECTION));
        response =
            policy.requireFreshSynthesis(
                AgentResponseCorrector.requireResponse(
                    client.complete(new LlmRequest(messages, List.of()))),
                history);
      }
      if (policy.requiresSynthesisCorrection(
          requiredFreshTool, response, turnState.successfulToolResults())) {
        if (turnState.freshSynthesisCorrectionUsed()) {
          throw new AgentRoutingException(
              "Agent did not produce a complete fresh history synthesis");
        }
        log.warn(
            "Agent fresh history synthesis omitted evidence metadata, correlationId={}",
            correlationId);
        messages.add(LlmMessage.assistant(response.content(), List.of()));
        messages.add(LlmMessage.user(FRESH_SYNTHESIS_CORRECTION));
        response =
            policy.requireFreshSynthesis(
                AgentResponseCorrector.requireResponse(
                    client.complete(new LlmRequest(messages, List.of()))),
                history);
        turnState.markFreshSynthesisCorrectionUsed();
        if (!policy.satisfiesProfileContract(response, turnState.successfulToolResults())) {
          throw new AgentRoutingException(
              "Agent did not produce a complete fresh history synthesis");
        }
      }
    }
    return new Result(response, false);
  }

  private Result loadRequiredHistory(
      LlmResponse response,
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      String tool,
      String nick,
      AgentContext context,
      AgentToolExecutor toolExecutor,
      AgentTurnState turnState,
      String correlationId,
      ToolResultRenderer resultRenderer)
      throws AgentRoutingException, LlmException {
    if (!turnState.reserveToolCalls(1)) {
      throw new AgentRoutingException("Agent tool-call limit reached before loading fresh data");
    }
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", nick);
    LlmToolCall freshCall =
        new LlmToolCall("router-fresh-" + correlationId, tool, arguments.toString());
    turnState.markToolAttempted(1);
    AgentToolResult freshResult = toolExecutor.execute(context, freshCall);
    if (freshResult.isError()) {
      turnState.recordToolFailure();
      throw new AgentRoutingException("Required fresh-data tool failed: " + tool);
    }
    turnState.recordToolSuccess();
    messages.add(LlmMessage.assistant(response.content(), List.of(freshCall)));
    messages.add(
        LlmMessage.tool(freshCall.id(), resultRenderer.render(context, freshCall, freshResult)));
    turnState.recordSuccessfulTool(tool);
    turnState.recordSuccessfulToolResult(freshResult);
    log.info(
        "Agent fresh data loaded by router, correlationId={}, tool={}, nick={}",
        correlationId,
        tool,
        nick);
    return new Result(
        AgentResponseCorrector.requireResponse(
            client.complete(new LlmRequest(messages, definitions))),
        true);
  }

  /** Carries the result value used by the enclosing agent component. */
  public record Result(LlmResponse response, boolean restartLoop) {}

  @FunctionalInterface
  /** Defines the operation used to tool result renderer. */
  public interface ToolResultRenderer {
    String render(AgentContext context, LlmToolCall call, AgentToolResult result);
  }

  @FunctionalInterface
  /** Defines the operation used to definition provider. */
  public interface DefinitionProvider {
    List<JsonObject> definitionsFor(List<JsonObject> definitions, String toolName)
        throws AgentRoutingException;
  }
}
