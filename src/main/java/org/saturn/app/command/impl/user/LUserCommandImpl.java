package org.saturn.app.command.impl.user;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentInvocationMode;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.routing.AgentInvocationFactory;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

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
    if (engine.getAgentService() == null) {
      return fail("The agent is unavailable.");
    }
    AgentInvocation invocation =
        new AgentInvocationFactory(AgentParticipationConfig.from(engine.getConfig()))
            .create(engine, chatMessage, renderArguments(true).trim(), AgentInvocationMode.DIRECT);
    engine.getAgentService().submit(invocation);
    log.info("Queued [l] command by user: {}, requestId={}", author(), invocation.requestId());
    return successful();
  }
}
