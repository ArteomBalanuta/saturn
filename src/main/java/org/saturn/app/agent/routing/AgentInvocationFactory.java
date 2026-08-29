package org.saturn.app.agent.routing;

import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentInvocationMode;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

/** Creates agent invocations from routed room requests. */
public final class AgentInvocationFactory {
  private final AgentParticipationConfig config;

  /**
   * Implements the {@code AgentInvocationFactory} operation for this agent component.
   *
   * @param config input argument used by this operation
   */
  public AgentInvocationFactory(AgentParticipationConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  /**
   * Implements the {@code create} operation for this agent component.
   *
   * @param engine input argument used by this operation
   * @param message input argument used by this operation
   * @param prompt input argument used by this operation
   * @param mode input argument used by this operation
   * @return the operation result
   */
  public AgentInvocation create(
      EngineImpl engine, ChatMessage message, String prompt, AgentInvocationMode mode) {
    return create(engine, message, prompt, mode, false);
  }

  /**
   * Implements the {@code create} operation for this agent component.
   *
   * @param engine input argument used by this operation
   * @param message input argument used by this operation
   * @param prompt input argument used by this operation
   * @param mode input argument used by this operation
   * @param commandOriginated input argument used by this operation
   * @return the operation result
   */
  public AgentInvocation create(
      EngineImpl engine,
      ChatMessage message,
      String prompt,
      AgentInvocationMode mode,
      boolean commandOriginated) {
    Objects.requireNonNull(engine, "engine");
    Objects.requireNonNull(message, "message");
    EnumSet<AgentCapability> capabilities = EnumSet.noneOf(AgentCapability.class);
    String trip = message.getTrip();
    boolean creator = config.creatorTrip().equals(trip);
    boolean dynamicSqlAdmin =
        !creator
            && mode != AgentInvocationMode.AMBIENT
            && mode != AgentInvocationMode.MODERATION
            && isDynamicSqlAdmin(engine, trip);
    if (creator || dynamicSqlAdmin) {
      capabilities.add(AgentCapability.DYNAMIC_SQL);
    }
    boolean moderator =
        !creator
            && mode != AgentInvocationMode.AMBIENT
            && mode != AgentInvocationMode.MODERATION
            && (dynamicSqlAdmin || engine.authorizationService.resolveRole(trip) == Role.MODERATOR);
    if (creator || moderator) {
      capabilities.add(AgentCapability.MODERATION_COMMANDS);
    }
    if (creator && mode == AgentInvocationMode.DIRECT) {
      capabilities.add(AgentCapability.PERMANENT_BAN);
      capabilities.add(AgentCapability.ADMIN_COMMANDS);
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
    return new AgentInvocation(
        UUID.randomUUID().toString(), context, prompt, mode, message.getText(), commandOriginated);
  }

  /**
   * Implements the {@code isDynamicSqlAdmin} operation for this agent component.
   *
   * @param engine input argument used by this operation
   * @param trip input argument used by this operation
   * @return the operation result
   */
  private static boolean isDynamicSqlAdmin(EngineImpl engine, String trip) {
    if (trip == null || trip.isBlank()) {
      return false;
    }
    return Util.getAdminTrips(engine).contains(trip)
        || engine.authorizationService.resolveRole(trip) == Role.ADMIN;
  }
}
