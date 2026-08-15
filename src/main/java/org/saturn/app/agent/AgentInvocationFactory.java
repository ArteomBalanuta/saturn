package org.saturn.app.agent;

import java.util.EnumSet;
import java.util.Objects;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

public final class AgentInvocationFactory {
  private final AgentParticipationConfig config;

  public AgentInvocationFactory(AgentParticipationConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  public AgentInvocation create(
      EngineImpl engine, ChatMessage message, String prompt, AgentInvocationMode mode) {
    Objects.requireNonNull(engine, "engine");
    Objects.requireNonNull(message, "message");
    EnumSet<AgentCapability> capabilities = EnumSet.noneOf(AgentCapability.class);
    String trip = message.getTrip();
    boolean creator = config.creatorTrip().equals(trip);
    if (creator || (mode != AgentInvocationMode.AMBIENT && isDynamicSqlAdmin(engine, trip))) {
      capabilities.add(AgentCapability.DYNAMIC_SQL);
    }
    if (creator) {
      capabilities.add(AgentCapability.MODERATION_COMMANDS);
      if (mode == AgentInvocationMode.DIRECT) {
        capabilities.add(AgentCapability.PERMANENT_BAN);
      }
    }

    AgentContext context =
        new AgentContext(
            engine.channel,
            message.getNick(),
            trip,
            message.getHash(),
            message.isWhisper(),
            engine.currentChannelUsers.stream().map(user -> user.getNick()).toList(),
            capabilities);
    return new AgentInvocation(context, prompt, mode);
  }

  private static boolean isDynamicSqlAdmin(EngineImpl engine, String trip) {
    if (trip == null || trip.isBlank()) {
      return false;
    }
    return Util.getAdminTrips(engine).contains(trip)
        || engine.authorizationService.resolveRole(trip) == Role.ADMIN;
  }
}
