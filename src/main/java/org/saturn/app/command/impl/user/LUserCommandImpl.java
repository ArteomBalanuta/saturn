package org.saturn.app.command.impl.user;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.AgentCapability;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentInvocation;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

@Slf4j
@CommandAliases(aliases = {"l"})
public class LUserCommandImpl extends UserCommandBaseImpl {
  public LUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, List.of("x"));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    if (!hasArguments()) {
      return failWithUsage("l <prompt/question>");
    }
    Set<AgentCapability> capabilities =
        isDynamicSqlAdmin() ? Set.of(AgentCapability.DYNAMIC_SQL) : Set.of();
    AgentContext context =
        new AgentContext(
            engine.channel,
            author(),
            chatMessage.getTrip(),
            chatMessage.getHash(),
            isWhisper(),
            engine.currentChannelUsers.stream().map(user -> user.getNick()).toList(),
            capabilities);
    if (engine.getAgentService() == null) {
      return fail("The agent is unavailable.");
    }
    AgentInvocation invocation = new AgentInvocation(context, renderArguments(true).trim());
    engine.getAgentService().submit(invocation);
    log.info("Queued [l] command by user: {}, requestId={}", author(), invocation.requestId());
    return successful();
  }

  private boolean isDynamicSqlAdmin() {
    String trip = chatMessage.getTrip();
    return trip != null
        && (Util.getAdminTrips(engine).contains(trip)
            || engine.authorizationService.resolveRole(trip) == Role.ADMIN);
  }
}
