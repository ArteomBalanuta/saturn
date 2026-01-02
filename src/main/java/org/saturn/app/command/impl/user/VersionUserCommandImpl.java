package org.saturn.app.command.impl.user;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"version", "v"})
public class VersionUserCommandImpl extends UserCommandBaseImpl {
  public VersionUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, List.of("x"));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    final String author = chatMessage.getNick();

    String version;
    try (InputStream is = VersionUserCommandImpl.class.getResourceAsStream("/VERSION");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      version = reader.readLine();
    } catch (Exception e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
      throw new RuntimeException(e);
    }

    engine.outService.enqueueMessageForSending(author, version, isWhisper());
    log.info("Executed [version] command by user: {}", author);
    return Optional.of(Status.SUCCESSFUL);
  }
}
