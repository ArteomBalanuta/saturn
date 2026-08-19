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

  AgentRequestAssembler(
      AgentConfig config, AgentToolRegistry registry, AgentSystemPrompt systemPrompt) {
    this.config = config;
    this.registry = registry;
    this.systemPrompt = systemPrompt;
  }

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
    trimToBudget(messages);
    return new AgentPreparedRequest(
        messages,
        definitions(context, invocation.mode(), invocation.prompt()),
        contextualizedPrompt,
        requiredFreshTool,
        requiredFreshNick,
        requestKind);
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

  int contextBudget() {
    long scaledBudget = (long) config.maxPromptChars() * 8L;
    return (int) Math.min(Integer.MAX_VALUE, Math.max(32_000L, scaledBudget));
  }

  private void trimToBudget(List<LlmMessage> messages) {
    int length = serializedLength(messages);
    while (length > contextBudget() && messages.size() > 2) {
      LlmMessage removed = messages.remove(1);
      length -= removed.content() == null ? 0 : removed.content().length();
    }
  }

  private static int serializedLength(List<LlmMessage> messages) {
    return messages.stream()
        .mapToInt(message -> message.content() == null ? 0 : message.content().length())
        .sum();
  }
}
