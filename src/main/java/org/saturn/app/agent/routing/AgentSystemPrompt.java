package org.saturn.app.agent.routing;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Objects;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.turn.AgentToolEvidence;

/** Represents the system prompt assembled for an agent runtime. */
public final class AgentSystemPrompt {
  private final AgentParticipationConfig config;
  private final AgentPromptCatalog prompts;
  private final Gson gson = new Gson();

  public AgentSystemPrompt(AgentParticipationConfig config) {
    this.config = Objects.requireNonNull(config, "config");
    this.prompts = new AgentPromptCatalog();
  }

  public String render(AgentInvocation invocation, String correlationId, String recentRoomContext) {
    return render(
        invocation,
        correlationId,
        recentRoomContext,
        new AgentRequestClassifier()
            .classifyCandidate(
                new AgentRequestInput(
                    invocation.currentMessageText() == null
                        ? invocation.prompt()
                        : invocation.currentMessageText(),
                    invocation.mode(),
                    invocation.commandOriginated())),
        AgentToolEvidence.none(),
        "CANDIDATE");
  }

  public String render(
      AgentInvocation invocation,
      String correlationId,
      String recentRoomContext,
      AgentRequestKind requestKind,
      AgentToolEvidence toolEvidence,
      String requestKindPhase) {
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
    runtime.addProperty("requestKind", requestKind.name());
    runtime.addProperty("requestKindPhase", requestKindPhase);
    JsonObject toolEvidenceJson = new JsonObject();
    toolEvidenceJson.addProperty("attempted", toolEvidence.attempted());
    toolEvidenceJson.addProperty("attemptedCount", toolEvidence.attemptedCount());
    toolEvidenceJson.addProperty("successfulCount", toolEvidence.successfulCount());
    toolEvidenceJson.addProperty("failedCount", toolEvidence.failedCount());
    runtime.add("toolEvidence", toolEvidenceJson);
    runtime.addProperty("room", context.room());
    runtime.addProperty("whisper", context.whisper());
    runtime.add("caller", caller);
    runtime.add("roomUsersSnapshot", gson.toJsonTree(context.roomUsers()));

    String databasePolicy =
        context.hasCapability(AgentCapability.DYNAMIC_SQL)
            ? prompts.text("system/database-policy-enabled.txt").strip()
            : prompts.text("system/database-policy-disabled.txt").strip();
    String participationPolicy =
        switch (invocation.mode()) {
          case DIRECT -> prompts.text("system/participation-direct.txt").strip();
          case MENTION -> prompts.text("system/participation-mention.txt").strip();
          case AMBIENT ->
              prompts.formatted(
                  "system/participation-ambient.txt",
                  config.noReplyMarker(),
                  config.noReplyMarker());
          case MODERATION ->
              prompts.formatted(
                  "system/participation-moderation.txt",
                  config.noReplyMarker(),
                  config.noReplyMarker());
        };
    String roomHistory =
        recentRoomContext == null || recentRoomContext.isBlank()
            ? "{\"rows\":[]}"
            : recentRoomContext;

    return prompts
        .formatted(
            "system/system-policy.txt",
            config.creatorTrip(),
            context.whisper() ? "private whisper" : "shared room",
            databasePolicy,
            participationPolicy,
            prompts.text("persona/vaelen-system-prompt.txt").strip(),
            gson.toJson(runtime),
            roomHistory)
        .strip();
  }
}
