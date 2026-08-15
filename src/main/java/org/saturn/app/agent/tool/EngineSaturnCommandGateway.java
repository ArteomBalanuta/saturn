package org.saturn.app.agent.tool;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
public final class EngineSaturnCommandGateway implements SaturnCommandGateway {
  private final EngineImpl engine;

  public EngineSaturnCommandGateway(EngineImpl engine) {
    this.engine = engine;
  }

  @Override
  public boolean execute(AgentContext context, String command, String arguments) {
    String text = engine.getPrefix() + command + (arguments.isBlank() ? "" : " " + arguments);
    ChatMessage synthetic =
        new ChatMessage(null, context.nick(), context.trip(), context.hash(), null, text);
    synthetic.setWhisper(context.whisper());
    try {
      return new UserCommandBaseImpl(synthetic, engine, List.of("x"))
          .execute()
          .filter(Status.SUCCESSFUL::equals)
          .isPresent();
    } catch (RuntimeException exception) {
      log.warn(
          "Agent-triggered Saturn command failed, command={}: {}", command, exception.getMessage());
      return false;
    }
  }
}
