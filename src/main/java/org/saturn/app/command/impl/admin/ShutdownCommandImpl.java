package org.saturn.app.command.impl.admin;

import lombok.extern.slf4j.Slf4j;
import org.saturn.ApplicationRunner;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

@Slf4j
@CommandAliases(aliases = {"exit", "quit", "shutdown"})
public class ShutdownCommandImpl extends UserCommandBaseImpl {
  public ShutdownCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Optional<Status> execute() {
    String author = chatMessage.getNick();

    try {
      ApplicationRunner.applicationRunner.stopApplication();
      log.info("Executed [shutdown] command by user: {}", author);
    } catch (Exception e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);

      log.error("Failed executing [shutdown] command by user: {}", author);
    }

    return Optional.of(Status.SUCCESSFUL);
  }
}
