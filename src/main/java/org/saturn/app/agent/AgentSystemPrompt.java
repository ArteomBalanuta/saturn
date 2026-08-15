package org.saturn.app.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Objects;

public final class AgentSystemPrompt {
  private final AgentParticipationConfig config;
  private final AgentPromptCatalog prompts;
  private final Gson gson = new Gson();

  public AgentSystemPrompt(AgentParticipationConfig config) {
    this.config = Objects.requireNonNull(config, "config");
    this.prompts = new AgentPromptCatalog();
  }

  public String render(AgentInvocation invocation, String correlationId, String recentRoomContext) {
    Objects.requireNonNull(invocation, "invocation");
    Objects.requireNonNull(correlationId, "correlationId");
    AgentContext context = invocation.context();
    JsonObject caller = new JsonObject();
    caller.addProperty("nick", context.nick());
    caller.addProperty("trip", context.trip());
    caller.addProperty("hash", context.hash());
    caller.addProperty("creator", config.creatorTrip().equals(context.trip()));

    JsonObject runtime = new JsonObject();
    runtime.addProperty("correlationId", correlationId);
    runtime.addProperty("invocationMode", invocation.mode().name());
    runtime.addProperty("room", context.room());
    runtime.addProperty("whisper", context.whisper());
    runtime.add("caller", caller);
    runtime.add("roomUsersSnapshot", gson.toJsonTree(context.roomUsers()));

    String databasePolicy =
        context.hasCapability(AgentCapability.DYNAMIC_SQL)
            ? prompts.text("database-policy-enabled.txt").strip()
            : prompts.text("database-policy-disabled.txt").strip();
    String participationPolicy =
        switch (invocation.mode()) {
          case DIRECT -> prompts.text("participation-direct.txt").strip();
          case MENTION -> prompts.text("participation-mention.txt").strip();
          case AMBIENT ->
              prompts.formatted(
                  "participation-ambient.txt", config.noReplyMarker(), config.noReplyMarker());
          case MODERATION ->
              prompts.formatted(
                  "participation-moderation.txt", config.noReplyMarker(), config.noReplyMarker());
        };
    String roomHistory =
        recentRoomContext == null || recentRoomContext.isBlank()
            ? "{\"rows\":[]}"
            : recentRoomContext;

    return prompts
        .formatted(
            "system-policy.txt",
            config.creatorTrip(),
            context.whisper() ? "private whisper" : "shared room",
            databasePolicy,
            participationPolicy,
            prompts.text("vaelen-system-prompt.txt").strip(),
            gson.toJson(runtime),
            roomHistory)
        .strip();
  }
}
