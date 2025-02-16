package org.saturn.app.command.impl.user;

import java.util.ArrayList;
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
@CommandAliases(aliases = {"crashcourse", "howto", "moderationcrashcourse", "hcguide"})
public class HowToUserCommandImpl extends UserCommandBaseImpl {
  private final List<String> aliases = new ArrayList<>();

  public HowToUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, List.of("x"));
    super.setAliases(this.getAliases());
    this.aliases.addAll(aliases);
  }

  @Override
  public List<String> getAliases() {
    return this.aliases;
  }

  @Override
  public List<String> getArguments() {
    return super.getArguments();
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    String author = chatMessage.getNick();
    engine.outService.enqueueMessageForSending(
        author,
        "hack.chat moderation guide \\n In case spammer or a ~~valid~~ nasty user joined: \\n https://youtu.be/E_Yl9ul3Ulw",
        isWhisper());

    log.info("Executed [howto] command by user: {}, value", author);
    return Optional.of(Status.SUCCESSFUL);
  }
}
