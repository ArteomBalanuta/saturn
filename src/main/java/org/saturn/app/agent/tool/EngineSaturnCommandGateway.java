package org.saturn.app.agent.tool;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.CommandOutputCapture;

@Slf4j
/** Adapts agent command requests to Saturn's engine command-dispatch boundary. */
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

  @Override
  public CommandExecution executeWithResult(
      AgentContext context, String command, String arguments) {
    CommandOutputCapture.Captured<Boolean> captured =
        CommandOutputCapture.capture(() -> execute(context, command, arguments));
    if (!captured.value()) {
      return new CommandExecution(false, "");
    }
    String modelData = String.join("\n", captured.chatMessages());
    if (modelData.isBlank()) {
      modelData =
          "Saturn command '%s' executed; its output was sent to the room. No other Saturn command was executed."
              .formatted(command);
    }
    return new CommandExecution(true, modelData);
  }
}
