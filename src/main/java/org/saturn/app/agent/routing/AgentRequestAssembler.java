package org.saturn.app.agent.routing;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentInvocationMode;
import org.saturn.app.agent.api.ToolResultMode;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.tool.contract.AgentToolDefinitionJson;
import org.saturn.app.agent.tool.execution.AgentToolRegistry;
import org.saturn.app.agent.turn.AgentFreshnessPolicy;
import org.saturn.app.agent.turn.AgentMessageHistory;

/** Builds a bounded provider request from trusted invocation state and persisted history. */
final class AgentRequestAssembler {
  private final AgentConfig config;
  private final AgentToolRegistry registry;
  private final AgentSystemPrompt systemPrompt;
  private final AgentFreshnessPolicy freshnessPolicy = new AgentFreshnessPolicy();
  private final AgentPromptCatalog prompts = new AgentPromptCatalog();
  private final AgentMessageProjector projector = new AgentMessageProjector();

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param config the config input; null handling follows the validation performed by this
   *     declaration
   * @param registry the registry input; null handling follows the validation performed by this
   *     declaration
   * @param systemPrompt the systemPrompt input; null handling follows the validation performed by
   *     this declaration
   */
  AgentRequestAssembler(
      AgentConfig config, AgentToolRegistry registry, AgentSystemPrompt systemPrompt) {
    this.config = config;
    this.registry = registry;
    this.systemPrompt = systemPrompt;
  }

  /**
   * Builds a provider request from invocation context, memory, and recent room context.
   *
   * @param invocation the invocation input; null handling follows the validation performed by this
   *     declaration
   * @param history the history input; null handling follows the validation performed by this
   *     declaration
   * @param recentRoomContext the recentRoomContext input; null handling follows the validation
   *     performed by this declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  AgentPreparedRequest assemble(
      AgentInvocation invocation, List<LlmMessage> history, String recentRoomContext) {
    return assemble(
        invocation,
        history,
        recentRoomContext,
        new AgentRequestClassifier()
            .classifyCandidate(
                new AgentRequestInput(
                    invocation.currentMessageText() == null
                        ? invocation.prompt()
                        : invocation.currentMessageText(),
                    invocation.mode(),
                    invocation.commandOriginated())));
  }

  /**
   * Builds a provider request from invocation context, memory, and recent room context.
   *
   * @param invocation the invocation input; null handling follows the validation performed by this
   *     declaration
   * @param history the history input; null handling follows the validation performed by this
   *     declaration
   * @param recentRoomContext the recentRoomContext input; null handling follows the validation
   *     performed by this declaration
   * @param requestKind the requestKind input; null handling follows the validation performed by
   *     this declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  AgentPreparedRequest assemble(
      AgentInvocation invocation,
      List<LlmMessage> history,
      String recentRoomContext,
      AgentRequestKind requestKind) {
    AgentContext context = invocation.context();
    boolean moderation = invocation.mode() == AgentInvocationMode.MODERATION;
    Optional<String> requiredFreshTool =
        moderation
            ? Optional.empty()
            : freshnessPolicy.requiredTool(invocation.prompt(), history, context.roomUsers());
    Optional<String> requiredFreshNick =
        moderation
            ? Optional.empty()
            : freshnessPolicy.requiredNick(invocation.prompt(), history, context.roomUsers());
    String contextualizedPrompt = contextualize(context, invocation.prompt());
    List<LlmMessage> messages = new ArrayList<>();
    messages.add(
        LlmMessage.system(
            systemPrompt.render(
                invocation,
                invocation.requestId(),
                AgentTextBounds.truncate(recentRoomContext, contextBudget()),
                requestKind,
                org.saturn.app.agent.turn.AgentToolEvidence.none(),
                "CANDIDATE")));
    messages.addAll(history.stream().filter(message -> retainHistory(context, message)).toList());
    messages.add(LlmMessage.user(contextualizedPrompt));
    AgentContextProjection projection = projector.project(messages, contextBudget());
    return new AgentPreparedRequest(
        projection.messages(),
        definitions(context, invocation.mode(), invocation.prompt()),
        contextualizedPrompt,
        requiredFreshTool,
        requiredFreshNick,
        requestKind,
        projection);
  }

  private List<JsonObject> definitions(
      AgentContext context, AgentInvocationMode mode, String newestPrompt) {
    List<JsonObject> definitions = new ArrayList<>();
    for (var definition : registry.definitions(context)) {
      JsonObject object = definition.getAsJsonObject();
      if (mode != AgentInvocationMode.MODERATION
          || AgentToolDefinitionJson.functionName(object)
              .filter("run_command"::equals)
              .isPresent()) {
        definitions.add(object);
      }
    }
    return AgentCommandIntentPolicy.filter(definitions, mode, newestPrompt);
  }

  private boolean retainHistory(AgentContext context, LlmMessage message) {
    Optional<String> evidenceTool = AgentMessageHistory.internalToolEvidenceName(message.content());
    if (evidenceTool.isEmpty()) {
      return !AgentMessageHistory.isInternalToolEvidence(message.content());
    }
    return registry
        .find(context, evidenceTool.orElseThrow())
        .map(tool -> tool.descriptor(context).resultMode() == ToolResultMode.MODEL_DATA)
        .orElse(false);
  }

  private String contextualize(AgentContext context, String prompt) {
    String visibility = context.whisper() ? "Private Saturn whisper" : "Public Saturn message";
    return prompts.formatted(
        "input/router-contextualized-prompt.txt",
        visibility,
        context.nick(),
        context.room(),
        prompt);
  }

  /**
   * Computes the remaining character budget for contextual request material.
   *
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  int contextBudget() {
    long scaledBudget = (long) config.maxPromptChars() * 8L;
    return (int) Math.min(Integer.MAX_VALUE, Math.max(32_000L, scaledBudget));
  }
}
