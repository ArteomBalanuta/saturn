package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmMessage;

/** Builds a bounded provider request from trusted invocation state and persisted history. */
final class AgentRequestAssembler {
  private final AgentConfig config;
  private final AgentToolRegistry registry;
  private final AgentSystemPrompt systemPrompt;
  private final AgentFreshnessPolicy freshnessPolicy = new AgentFreshnessPolicy();
  private final AgentPromptCatalog prompts = new AgentPromptCatalog();

  AgentRequestAssembler(AgentConfig config, AgentToolRegistry registry, AgentSystemPrompt systemPrompt) {
    this.config = config;
    this.registry = registry;
    this.systemPrompt = systemPrompt;
  }

  AgentPreparedRequest assemble(
      AgentInvocation invocation, List<LlmMessage> history, String recentRoomContext) {
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
                invocation, invocation.requestId(), truncate(recentRoomContext, contextBudget()))));
    messages.addAll(history);
    messages.add(LlmMessage.user(contextualizedPrompt));
    trimToBudget(messages);
    return new AgentPreparedRequest(
        messages,
        definitions(context, invocation.mode()),
        contextualizedPrompt,
        requiredFreshTool,
        requiredFreshNick);
  }

  private List<JsonObject> definitions(AgentContext context, AgentInvocationMode mode) {
    List<JsonObject> definitions = new ArrayList<>();
    for (JsonElement definition : registry.definitions(context)) {
      JsonObject object = definition.getAsJsonObject();
      if (mode != AgentInvocationMode.MODERATION || isNamedDefinition(object, "run_command")) {
        definitions.add(object);
      }
    }
    return List.copyOf(definitions);
  }

  private String contextualize(AgentContext context, String prompt) {
    String visibility = context.whisper() ? "Private Saturn whisper" : "Public Saturn message";
    return prompts.formatted(
        "router-contextualized-prompt.txt", visibility, context.nick(), context.room(), prompt);
  }

  private int contextBudget() {
    return Math.max(32_000, config.maxPromptChars() * 8);
  }

  private void trimToBudget(List<LlmMessage> messages) {
    while (serializedLength(messages) > contextBudget() && messages.size() > 2) {
      messages.remove(1);
    }
  }

  private static int serializedLength(List<LlmMessage> messages) {
    return messages.stream()
        .mapToInt(message -> message.content() == null ? 0 : message.content().length())
        .sum();
  }

  private static boolean isNamedDefinition(JsonObject definition, String toolName) {
    JsonObject function = definition.getAsJsonObject("function");
    return function != null
        && function.has("name")
        && toolName.equals(function.get("name").getAsString());
  }

  private static String truncate(String content, int maxChars) {
    if (content == null || codePointCount(content) <= maxChars) {
      return content == null ? "" : content;
    }
    return content.substring(0, content.offsetByCodePoints(0, maxChars));
  }

  private static int codePointCount(String content) {
    return content.codePointCount(0, content.length());
  }
}
