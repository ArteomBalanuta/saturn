package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;
import static org.saturn.app.util.Util.sleep;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.listener.impl.MsgChannelCommandListenerImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(
    aliases = {
      "nuke",
    })
public class NukeCommandImpl extends UserCommandBaseImpl {
  public NukeCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    final List<String> arguments =
        getArguments().stream().map(arg -> arg.replace("@", "")).toList();

    final String author = chatMessage.getNick();
    if (arguments.isEmpty()) {
      log.info("Executed [nuke] command by user: {}", author);
      engine.outService.enqueueMessageForSending(
          author, "\\n Example: " + engine.prefix + "nuke hotlinks", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String room = arguments.getFirst();

    /* JoinChannelListener will make sure to close the connection */
    EngineImpl slaveEngine =
        new EngineImpl(
            null,
            super.engine.getConfig(),
            EngineType.REPLICA); // no db connection, nor config for this one is required
    setupListBot(room, slaveEngine);

    JoinChannelListener joinChannelListener =
        new MsgChannelCommandListenerImpl(
            new JoinChannelListenerDto(this.engine, slaveEngine, author, room));

    joinChannelListener.setAction(
        () -> {
          List<String> users = slaveEngine.currentChannelUsers.stream().map(User::getNick).toList();
          engine.outService.enqueueMessageForSending(
              author, "\\n Kicking and locking room ?" + room + ", users: " + users, isWhisper());

          slaveEngine.currentChannelUsers.forEach(
              u -> {
                slaveEngine.modService.ban(u.getNick());
                sleep(200, TimeUnit.MILLISECONDS);
                slaveEngine.shareMessages();
              });

          slaveEngine.modService.lock();
          slaveEngine.shareMessages();

          log.info("Executed [nuke] command by user: {}, room: {}", author, room);
        });

    slaveEngine.setOnlineSetListener(joinChannelListener);
    slaveEngine.start();

    log.info("Executed [nuke] command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }

  private void setupListBot(String channel, EngineImpl listBot) {
    listBot.setChannel(channel);
    int length = 8;
    boolean useLetters = true;
    boolean useNumbers = true;
    String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
    listBot.setNick(generatedNick);
    listBot.setPassword(engine.password);
  }
}
